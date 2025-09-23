package com.nnpg.glazed.modules.main;

import meteordevelopment.meteorclient.events.meteor.MouseScrollEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * FreecamV2
 *
 * - camera-based movement
 * - raycast from camera to allow attack/use/break attempts where the camera is looking
 * - settings let you control reach distance and cooldown so interactions are less spammy
 *
 * NOTE: Servers validate interactions — mining/attacks far from your real player will often be rejected.
 * This module does not do position spoofing / bypass anti-cheat.
 */
public class FreecamV2 extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgInteract = settings.createGroup("Interaction");

    // Movement / camera settings
    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Camera movement speed.")
        .defaultValue(1.0)
        .min(0.01).max(10.0)
        .build()
    );

    private final Setting<Double> speedScrollSensitivity = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed-scroll-sensitivity")
        .description("Scroll changes camera speed (0 disables).")
        .defaultValue(0.0)
        .min(0.0).max(5.0)
        .build()
    );

    private final Setting<Boolean> keepHands = sgGeneral.add(new BoolSetting.Builder()
        .name("render-hands")
        .description("Render hands / items while in freecam.")
        .defaultValue(true)
        .build()
    );

    // Interaction settings
    private final Setting<Boolean> enableAttack = sgInteract.add(new BoolSetting.Builder()
        .name("attack-with-camera")
        .description("Allow attacking entities with the camera.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> enableUse = sgInteract.add(new BoolSetting.Builder()
        .name("use-with-camera")
        .description("Allow right-click (use/place) where camera is looking.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> enableBreak = sgInteract.add(new BoolSetting.Builder()
        .name("break-with-camera")
        .description("Attempt to break blocks where the camera is looking (client-side attempt).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> reach = sgInteract.add(new DoubleSetting.Builder()
        .name("reach-distance")
        .description("Max distance for the camera interactions.")
        .defaultValue(5.0)
        .min(1.0).max(50.0)
        .build()
    );

    private final Setting<Integer> actionCooldown = sgInteract.add(new IntSetting.Builder()
        .name("action-cooldown-ticks")
        .description("Minimum ticks between repeated interact/attack actions (prevents spamming).")
        .defaultValue(2)
        .min(0).max(20)
        .build()
    );

    // Internal state
    public final Vector3d pos = new Vector3d();
    public final Vector3d prevPos = new Vector3d();
    private double speedValue;
    private float yaw, pitch, lastYaw, lastPitch;
    private double prevX, prevY, prevZ;
    private int cooldownTicks = 0;

    public FreecamV2() {
        super("freecam-v2", "Move the camera independently and interact from camera.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) {
            toggle();
            return;
        }

        speedValue = speed.get();

        // Initialize camera position & rotation from current camera
        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        pos.set(cam.x, cam.y, cam.z);
        prevPos.set(cam.x, cam.y, cam.z);

        yaw = mc.player.getYaw();
        pitch = mc.player.getPitch();
        lastYaw = yaw;
        lastPitch = pitch;

        // prevent in-game keys from moving player
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);

        // ensure first-person perspective while in freecam
        if (!mc.options.getPerspective().isFirstPerson()) mc.options.setPerspective(Perspective.FIRST_PERSON);
    }

    @Override
    public void onDeactivate() {
        // restore nothing invasive — keep defaults
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        // handle speed scroll sensitivity
        speedValue = speed.get();

        // basic camera movement using keyboard state (same scheme as Meteor freecam)
        boolean forward = mc.options.forwardKey.isPressed();
        boolean back = mc.options.backKey.isPressed();
        boolean left = mc.options.leftKey.isPressed();
        boolean right = mc.options.rightKey.isPressed();
        boolean up = mc.options.jumpKey.isPressed();
        boolean down = mc.options.sneakKey.isPressed();

        Vec3d look = Vec3d.fromPolar((float) Math.toRadians(pitch), (float) Math.toRadians(yaw));
        Vec3d rightVec = Vec3d.fromPolar((float) Math.toRadians(pitch), (float) Math.toRadians(yaw + 90));

        double s = InputIsSprinting() ? 2.0 : 1.0; // small sprint multiplier if user is sprinting
        double dx = 0, dy = 0, dz = 0;
        double spd = speedValue * 0.15;

        if (forward) { dx += look.x * spd * s; dz += look.z * spd * s; dy += look.y * spd * s; }
        if (back)    { dx -= look.x * spd * s; dz -= look.z * spd * s; dy -= look.y * spd * s; }
        if (right)   { dx += rightVec.x * spd * s; dz += rightVec.z * spd * s; }
        if (left)    { dx -= rightVec.x * spd * s; dz -= rightVec.z * spd * s; }
        if (up)      dy += spd * s;
        if (down)    dy -= spd * s;

        prevPos.set(pos);
        pos.set(pos.x + dx, pos.y + dy, pos.z + dz);

        // update camera yaw/pitch from player's true rotation each tick so mouse still controls look
        // this keeps behavior simple: camera rotation follows player rotation (can be adapted)
        yaw = mc.player.getYaw();
        pitch = mc.player.getPitch();

        // decrement cooldown
        if (cooldownTicks > 0) cooldownTicks--;

        // Interactions (attack/use/break) — check input keys and perform raycast from camera
        tryPerformInteractions();
    }

    private void tryPerformInteractions() {
        if (mc.player == null || mc.world == null) return;

        // Must hold a small cooldown between actions to prevent spam
        if (cooldownTicks > 0) return;

        double reachDist = reach.get();

        // Raycast from cameraEntity (camera position / orientation)
        HitResult hit = mc.cameraEntity.raycast(reachDist, 0.0f, false);

        // Left click / attack
        if (enableAttack.get() && mc.options.attackKey.isPressed()) {
            if (hit instanceof EntityHitResult ehr) {
                Entity target = ehr.getEntity();
                // Attack via interaction manager (client-side attempt)
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(Hand.MAIN_HAND);
                cooldownTicks = actionCooldown.get();
                return;
            } else if (enableBreak.get() && hit instanceof BlockHitResult bhr) {
                // Attempt to start breaking the block at camera location
                // Client attempt — server may ignore if player is far away
                mc.interactionManager.updateBlockBreakingProgress(bhr.getBlockPos(), bhr.getSide());
                mc.player.swingHand(Hand.MAIN_HAND);
                cooldownTicks = actionCooldown.get();
                return;
            }
        }

        // Right click / use
        if (enableUse.get() && mc.options.useKey.isPressed()) {
            if (hit instanceof BlockHitResult bhr) {
                // Attempt to interact with block (place / use)
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                mc.player.swingHand(Hand.MAIN_HAND);
                cooldownTicks = actionCooldown.get();
                return;
            } else {
                // Use item in air (interact item)
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
                cooldownTicks = actionCooldown.get();
                return;
            }
        }
    }

    @EventHandler
    private void onMouseScroll(MouseScrollEvent event) {
        if (speedScrollSensitivity.get() <= 0 || mc.currentScreen != null) return;

        double sens = speedScrollSensitivity.get();
        speedValue += event.value * sens * (speedValue * 0.1 + 0.05);
        if (speedValue < 0.01) speedValue = 0.01;
        if (speedValue > 50) speedValue = 50;
        event.cancel();
    }

    /** Small helper to detect if the user is effectively sprinting. */
    private boolean InputIsSprinting() {
        // Use Minecraft sprint key pressed or double-tap mechanics; keep simple:
        return mc.options.sprintKey.isPressed();
    }

    // Exposed getters for rendering code (if you want to use them)
    public double getX(float tickDelta) { return lerp(prevPos.x, pos.x, tickDelta); }
    public double getY(float tickDelta) { return lerp(prevPos.y, pos.y, tickDelta); }
    public double getZ(float tickDelta) { return lerp(prevPos.z, pos.z, tickDelta); }
    public double getYaw(float tickDelta) { return mc.player.getYaw(); }
    public double getPitch(float tickDelta) { return mc.player.getPitch(); }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    @Override
    public boolean renderHands() {
        return !isActive() || keepHands.get();
    }
}
