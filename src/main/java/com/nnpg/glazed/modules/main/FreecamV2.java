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
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.DeathMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
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
 * - Camera moves freely and looks around.
 * - Player stays frozen in original position & rotation.
 * - Player can still mine/interact if enabled, but always from their frozen spot.
 */
public class FreecamV2 extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Camera flight speed.")
        .onChanged(aDouble -> speedValue = aDouble)
        .defaultValue(1.0)
        .min(0.0)
        .build()
    );

    private final Setting<Double> speedScrollSensitivity = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed-scroll-sensitivity")
        .description("Change speed with scroll wheel. 0 = off.")
        .defaultValue(0.0)
        .min(0.0)
        .sliderMax(2.0)
        .build()
    );

    private final Setting<Boolean> playerMine = sgGeneral.add(new BoolSetting.Builder()
        .name("player-mine")
        .description("Let the real player mine/interact while in freecam.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> toggleOnDamage = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-on-damage")
        .description("Disable freecam when you take damage.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> toggleOnDeath = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-on-death")
        .description("Disable freecam when you die.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> toggleOnLog = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-on-log")
        .description("Disable freecam when you disconnect.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> reloadChunks = sgGeneral.add(new BoolSetting.Builder()
        .name("reload-chunks")
        .description("Reload chunks to prevent occlusion bugs.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> renderHands = sgGeneral.add(new BoolSetting.Builder()
        .name("show-hands")
        .description("Render hands in freecam.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> staticView = sgGeneral.add(new BoolSetting.Builder()
        .name("static-view")
        .description("Disable FOV/bob effects.")
        .defaultValue(true)
        .build()
    );

    // Camera pos & rotation
    public final Vector3d pos = new Vector3d();
    public final Vector3d prevPos = new Vector3d();
    public float yaw, pitch;
    public float lastYaw, lastPitch;

    // Stored values
    private Perspective perspective;
    private double speedValue;
    private double fovScale;
    private boolean bobView;

    // Player frozen rotation
    private float frozenPlayerYaw, frozenPlayerPitch;

    // Freecam movement state
    private boolean forward, backward, right, left, up, down;

    public FreecamV2() {
        super(Categories.Render, "freecam-v2", "Camera moves freely while player stays still.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        fovScale = mc.options.getFovEffectScale().getValue();
        bobView = mc.options.getBobView().getValue();
        if (staticView.get()) {
            mc.options.getFovEffectScale().setValue(0.0);
            mc.options.getBobView().setValue(false);
        }

        frozenPlayerYaw = mc.player.getYaw();
        frozenPlayerPitch = mc.player.getPitch();

        yaw = frozenPlayerYaw;
        pitch = frozenPlayerPitch;
        perspective = mc.options.getPerspective();
        speedValue = speed.get();

        Vec3d camPos = mc.gameRenderer.getCamera().getPos();
        pos.set(camPos.x, camPos.y, camPos.z);
        prevPos.set(pos);

        lastYaw = yaw;
        lastPitch = pitch;

        unpress();
        mc.options.setPerspective(Perspective.FIRST_PERSON);

        if (reloadChunks.get()) mc.worldRenderer.reload();
    }

    @Override
    public void onDeactivate() {
        if (reloadChunks.get()) mc.execute(mc.worldRenderer::reload);

        mc.options.setPerspective(perspective);

        if (staticView.get()) {
            mc.options.getFovEffectScale().setValue(fovScale);
            mc.options.getBobView().setValue(bobView);
        }

        if (mc.player != null) {
            mc.player.setYaw(frozenPlayerYaw);
            mc.player.setPitch(frozenPlayerPitch);
        }
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
        unpress();
        prevPos.set(pos);
        lastYaw = yaw;
        lastPitch = pitch;
    }

    @EventHandler
    private void onChunkOcclusion(ChunkOcclusionEvent event) {
        event.cancel();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (toggleOnLog.get()) toggle();
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (event.packet instanceof DeathMessageS2CPacket && toggleOnDeath.get()) {
            toggle();
        } else if (event.packet instanceof HealthUpdateS2CPacket hp && toggleOnDamage.get()) {
            if (hp.getHealth() < mc.player.getHealth()) toggle();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        // Freeze player rotation
        mc.player.setYaw(frozenPlayerYaw);
        mc.player.setPitch(frozenPlayerPitch);

        if (!mc.options.getPerspective().isFirstPerson()) {
            mc.options.setPerspective(Perspective.FIRST_PERSON);
        }

        // Movement
        Vec3d forwardVec = Vec3d.fromPolar(0, yaw);
        Vec3d rightVec = Vec3d.fromPolar(0, yaw + 90);
        double velX = 0, velY = 0, velZ = 0;

        if (forward) { velX += forwardVec.x * speedValue; velZ += forwardVec.z * speedValue; }
        if (backward) { velX -= forwardVec.x * speedValue; velZ -= forwardVec.z * speedValue; }
        if (right) { velX += rightVec.x * speedValue; velZ += rightVec.z * speedValue; }
        if (left) { velX -= rightVec.x * speedValue; velZ -= rightVec.z * speedValue; }
        if (up) velY += speedValue;
        if (down) velY -= speedValue;

        prevPos.set(pos);
        pos.set(pos.x + velX, pos.y + velY, pos.z + velZ);

        // Player mining
        if (playerMine.get()) {
            if (mc.options.attackKey.isPressed()) {
                var hr = mc.player.raycast(6.0, 0f, false);
                if (hr instanceof BlockHitResult bhr) {
                    BlockPos target = bhr.getBlockPos();
                    Direction side = bhr.getSide();
                    mc.player.networkHandler.sendPacket(
                        new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, target, side)
                    );
                    mc.interactionManager.updateBlockBreakingProgress(target, side);
                }
            }

            if (mc.options.useKey.isPressed()) {
                var hr = mc.player.raycast(6.0, 0f, false);
                if (hr instanceof BlockHitResult bhr) {
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onKey(KeyEvent event) {
        if (checkGuiMove()) return;

        if (mc.options.forwardKey.matchesKey(event.key, 0)) {
            forward = event.action != KeyAction.Release;
            event.cancel();
        }
        else if (mc.options.backKey.matchesKey(event.key, 0)) {
            backward = event.action != KeyAction.Release;
            event.cancel();
        }
        else if (mc.options.rightKey.matchesKey(event.key, 0)) {
            right = event.action != KeyAction.Release;
            event.cancel();
        }
        else if (mc.options.leftKey.matchesKey(event.key, 0)) {
            left = event.action != KeyAction.Release;
            event.cancel();
        }
        else if (mc.options.jumpKey.matchesKey(event.key, 0)) {
            up = event.action != KeyAction.Release;
            event.cancel();
        }
        else if (mc.options.sneakKey.matchesKey(event.key, 0)) {
            down = event.action != KeyAction.Release;
            event.cancel();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onMouseButton(MouseButtonEvent event) {
        if (checkGuiMove()) return;

        if (!playerMine.get()) {
            event.cancel();
        }
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
        return mc.currentScreen != null && !guiMove.isActive();
    }

    // Camera getters
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
