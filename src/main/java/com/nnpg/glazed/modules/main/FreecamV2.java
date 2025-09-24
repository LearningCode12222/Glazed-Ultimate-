package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.meteor.MouseButtonEvent;
import meteordevelopment.meteorclient.events.meteor.MouseScrollEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.ChunkOcclusionEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.GUIMove;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.DeathMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;

/**
 * FreecamV2
 * - camera moves freely (you can look around)
 * - player stays frozen in original position & rotation (server-side)
 * - if Player Mine mode enabled: holding attack/use causes your real player (original spot) to mine/interact
 */
public class FreecamV2 extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Your speed while in freecam.")
        .onChanged(aDouble -> speedValue = aDouble)
        .defaultValue(1.0)
        .min(0.0)
        .build()
    );

    private final Setting<Double> speedScrollSensitivity = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed-scroll-sensitivity")
        .description("Allows you to change speed value using scroll wheel. 0 to disable.")
        .defaultValue(0.0)
        .min(0.0)
        .sliderMax(2.0)
        .build()
    );

    private final Setting<Boolean> playerMine = sgGeneral.add(new BoolSetting.Builder()
        .name("player-mine")
        .description("When enabled, holding attack/use will make your real player (original position) mine/interact while camera moves freely.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> staySneaking = sgGeneral.add(new BoolSetting.Builder()
        .name("stay-sneaking")
        .description("If you are sneaking when you enter freecam, whether your player should remain sneaking.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> toggleOnDamage = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-on-damage")
        .description("Disables freecam when you take damage.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> toggleOnDeath = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-on-death")
        .description("Disables freecam when you die.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> toggleOnLog = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-on-log")
        .description("Disables freecam when you disconnect from a server.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> reloadChunks = sgGeneral.add(new BoolSetting.Builder()
        .name("reload-chunks")
        .description("Reload chunks while enabling freecam to avoid occlusion artifacts.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> renderHands = sgGeneral.add(new BoolSetting.Builder()
        .name("show-hands")
        .description("Whether or not to render your hands in freecam.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates to the block or entity you are looking at.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> staticView = sgGeneral.add(new BoolSetting.Builder()
        .name("static")
        .description("Disables settings that move the view (bob/fov).")
        .defaultValue(true)
        .build()
    );

    public final Vector3d pos = new Vector3d();
    public final Vector3d prevPos = new Vector3d();

    private Perspective perspective;
    private double speedValue;

    public float yaw, pitch;
    public float lastYaw, lastPitch;

    private double fovScale;
    private boolean bobView;

    private boolean forward, backward, right, left, up, down, isSneaking;

    // Keep the player's original rotation fixed while camera moves
    private float frozenPlayerYaw, frozenPlayerPitch;

    public FreecamV2() {
        super(Categories.Render, "freecam-v2", "Move camera freely while player stays in place; optional player-mining.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        // Save some options and freeze player rotation
        fovScale = mc.options.getFovEffectScale().getValue();
        bobView = mc.options.getBobView().getValue();
        if (staticView.get()) {
            mc.options.getFovEffectScale().setValue((double) 0);
            mc.options.getBobView().setValue(false);
        }

        frozenPlayerYaw = mc.player.getYaw();
        frozenPlayerPitch = mc.player.getPitch();

        // Camera initial yaw/pitch come from player (so visual continuity)
        yaw = frozenPlayerYaw;
        pitch = frozenPlayerPitch;

        perspective = mc.options.getPerspective();
        speedValue = speed.get();

        // Camera start position: current camera position (works for first/third person)
        Vec3d camPos = mc.gameRenderer.getCamera().getPos();
        pos.set(camPos.x, camPos.y, camPos.z);
        prevPos.set(pos);

        if (mc.options.getPerspective() == Perspective.THIRD_PERSON_FRONT) {
            yaw += 180;
            pitch *= -1;
        }

        lastYaw = yaw;
        lastPitch = pitch;

        isSneaking = mc.options.sneakKey.isPressed();

        forward = Input.isPressed(mc.options.forwardKey);
        backward = Input.isPressed(mc.options.backKey);
        right = Input.isPressed(mc.options.rightKey);
        left = Input.isPressed(mc.options.leftKey);
        up = Input.isPressed(mc.options.jumpKey);
        down = Input.isPressed(mc.options.sneakKey);

        // release vanilla inputs so freecam controls will work
        unpress();

        // Force first-person camera perspective for freecam
        mc.options.setPerspective(Perspective.FIRST_PERSON);

        if (reloadChunks.get()) mc.worldRenderer.reload();
    }

    @Override
    public void onDeactivate() {
        if (reloadChunks.get()) mc.execute(mc.worldRenderer::reload);

        // restore perspective and view options
        mc.options.setPerspective(perspective);

        if (staticView.get()) {
            mc.options.getFovEffectScale().setValue(fovScale);
            mc.options.getBobView().setValue(bobView);
        }

        // ensure player rotation is restored (should already be frozen)
        if (mc.player != null) {
            mc.player.setYaw(frozenPlayerYaw);
            mc.player.setPitch(frozenPlayerPitch);
        }

        isSneaking = false;
    }

    private void unpress() {
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        // when opening an inventory/screen, reset pressed keys to avoid stuck movement
        unpress();
        prevPos.set(pos);
        lastYaw = yaw;
        lastPitch = pitch;
    }

    @EventHandler
    private void onChunkOcclusion(ChunkOcclusionEvent event) {
        // disable cave culling when freecam active to avoid chunks being culled incorrectly
        event.cancel();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (!toggleOnLog.get()) return;
        toggle();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof DeathMessageS2CPacket packet) {
            Entity dead = mc.world.getEntityById(packet.playerId());
            if (dead == mc.player && toggleOnDeath.get()) {
                toggle();
                info("Toggled off because you died.");
            }
        } else if (event.packet instanceof HealthUpdateS2CPacket packet) {
            if (mc.player.getHealth() - packet.getHealth() > 0 && toggleOnDamage.get()) {
                toggle();
                info("Toggled off because you took damage.");
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        // Keep player rotation frozen server-side / client-side
        mc.player.setYaw(frozenPlayerYaw);
        mc.player.setPitch(frozenPlayerPitch);

        // Ensure first-person for camera (so camera uses our pos)
        if (!mc.options.getPerspective().isFirstPerson()) mc.options.setPerspective(Perspective.FIRST_PERSON);

        // Camera movement calculations
        Vec3d forwardVec = Vec3d.fromPolar(0, yaw);
        Vec3d rightVec = Vec3d.fromPolar(0, yaw + 90);
        double velX = 0, velY = 0, velZ = 0;
        double sprintMul = Input.isPressed(mc.options.sprintKey) ? 1.0 : 0.5;

        if (this.forward) { velX += forwardVec.x * sprintMul * speedValue; velZ += forwardVec.z * sprintMul * speedValue; }
        if (this.backward) { velX -= forwardVec.x * sprintMul * speedValue; velZ -= forwardVec.z * sprintMul * speedValue; }
        if (this.right) { velX += rightVec.x * sprintMul * speedValue; velZ += rightVec.z * sprintMul * speedValue; }
        if (this.left) { velX -= rightVec.x * sprintMul * speedValue; velZ -= rightVec.z * sprintMul * speedValue; }
        if (this.up) velY += sprintMul * speedValue;
        if (this.down) velY -= sprintMul * speedValue;

        prevPos.set(pos);
        pos.set(pos.x + velX, pos.y + velY, pos.z + velZ);

        // Rotation-to-target option (optional)
        if (rotate.get()) {
            try {
                if (mc.crosshairTarget instanceof EntityHitResult ehr) {
                    BlockPos p = ehr.getEntity().getBlockPos();
                    Rotations.rotate(Rotations.getYaw(p), Rotations.getPitch(p), 0, null);
                } else if (mc.crosshairTarget instanceof BlockHitResult bhr) {
                    Vec3d targetPos = mc.crosshairTarget.getPos();
                    BlockPos p = bhr.getBlockPos();
                    if (!mc.world.getBlockState(p).isAir()) {
                        Rotations.rotate(Rotations.getYaw(targetPos), Rotations.getPitch(targetPos), 0, null);
                    }
                }
            } catch (Exception ignored) {}
        }

        // Player-mine mode: use player's raycast & let player perform the action
        if (playerMine.get()) {
            // Attack (hold left click)
            if (mc.options.attackKey.isPressed()) {
                // Raycast from PLAYER (not camera). Use small range (6) and tickDelta 0f
                var hr = mc.player.raycast(6.0, 0f, false);
                if (hr instanceof BlockHitResult bhr) {
                    BlockPos target = bhr.getBlockPos();
                    Direction side = bhr.getSide();
                    // Tell server we start destroying the block and update breaking progress locally
                    mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, target, side));
                    // Update local breaking progress (works client-side)
                    try {
                        mc.interactionManager.updateBlockBreakingProgress(target, side);
                    } catch (Exception ignored) {}
                }
            }

            // Use (right click)
            if (mc.options.useKey.isPressed()) {
                var hr = mc.player.raycast(6.0, 0f, false);
                if (hr instanceof BlockHitResult bhr) {
                    try {
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    // Input handling: convert WASD/jump/sneak keys to freecam booleans, cancel other interactions from camera
    @EventHandler(priority = EventPriority.HIGH)
    public void onKey(KeyEvent event) {
        if (Input.isKeyPressed(GLFW.GLFW_KEY_F3)) return;
        if (checkGuiMove()) return;

        boolean cancel = true;

        if (mc.options.forwardKey.matchesKey(event.key, 0)) {
            forward = event.action != KeyAction.Release;
            mc.options.forwardKey.setPressed(false);
        }
        else if (mc.options.backKey.matchesKey(event.key, 0)) {
            backward = event.action != KeyAction.Release;
            mc.options.backKey.setPressed(false);
        }
        else if (mc.options.rightKey.matchesKey(event.key, 0)) {
            right = event.action != KeyAction.Release;
            mc.options.rightKey.setPressed(false);
        }
        else if (mc.options.leftKey.matchesKey(event.key, 0)) {
            left = event.action != KeyAction.Release;
            mc.options.leftKey.setPressed(false);
        }
        else if (mc.options.jumpKey.matchesKey(event.key, 0)) {
            up = event.action != KeyAction.Release;
            mc.options.jumpKey.setPressed(false);
        }
        else if (mc.options.sneakKey.matchesKey(event.key, 0)) {
            down = event.action != KeyAction.Release;
            mc.options.sneakKey.setPressed(false);
        }
        else {
            cancel = false;
        }

        if (cancel) event.cancel();
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onMouseButton(MouseButtonEvent event) {
        if (checkGuiMove()) return;

        // Allow left click / right click to be processed *only* for Player Mine mode (they should act as normal player actions)
        // but we must *not* let camera send unintended interactions. So:
        // - If playerMine is enabled and user is pressing attack/use we DO NOT cancel (leave event) so Minecraft's logic checks keys pressed.
        // - Otherwise we cancel the mouse input to prevent camera interacting with world.
        if (event.button == 0) { // left click
            // If press (not release) AND playerMine enabled, let it pass to cause player mining (we handle packets in onTick)
            if (playerMine.get() && event.action == KeyAction.Press) {
                // let it through
                return;
            } else {
                // cancel any camera left-click interactions
                event.cancel();
                return;
            }
        }

        if (event.button == 1) { // right click
            if (playerMine.get() && event.action == KeyAction.Press) {
                return;
            } else {
                event.cancel();
                return;
            }
        }

        // other mouse buttons: cancel to be safe
        event.cancel();
    }

    @EventHandler(priority = EventPriority.LOW)
    private void onMouseScroll(MouseScrollEvent event) {
        if (speedScrollSensitivity.get() > 0 && mc.currentScreen == null) {
            speedValue += event.value * 0.25 * (speedScrollSensitivity.get() * speedValue);
            if (speedValue < 0.1) speedValue = 0.1;
            event.cancel();
        }
    }

    private boolean checkGuiMove() {
        GUIMove guiMove = Modules.get().get(GUIMove.class);
        if (mc.currentScreen != null && !guiMove.isActive()) return true;
        return (mc.currentScreen != null && guiMove.isActive() && guiMove.skip());
    }

    // Mouse/keyboard look handling is already handled by the Meteor input handlers and your camera yaw/pitch updated elsewhere.
    // If you need to have mouse-drag camera look, hook into similar mouse move events and update yaw/pitch accordingly.
    // Utility getters used by renderers elsewhere in the client:
    public double getX(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevPos.x, pos.x);
    }
    public double getY(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevPos.y, pos.y);
    }
    public double getZ(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevPos.z, pos.z);
    }
    public double getYaw(float tickDelta) {
        return MathHelper.lerp(tickDelta, lastYaw, yaw);
    }
    public double getPitch(float tickDelta) {
        return MathHelper.lerp(tickDelta, lastPitch, pitch);
    }
}
