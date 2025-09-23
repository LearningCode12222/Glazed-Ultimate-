package com.nnpg.glazed.modules.main;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

/**
 * Freecam V2 for Glazed-Ultimate.
 */
public class FreecamV2 extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("How fast you fly in freecam.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderMax(5.0)
        .build()
    );

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("How Freecam works.")
        .defaultValue(Mode.CameraOnly)
        .build()
    );

    private enum Mode {
        CameraOnly,
        CameraAndInteract
    }

    private OtherClientPlayerEntity dummy;
    private Vec3d cameraPos;
    private float cameraYaw, cameraPitch;

    public FreecamV2() {
        super(Categories.Render, "freecam-v2", "Move the camera independently and interact from camera.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        // Dummy player to hold your spot
        dummy = new OtherClientPlayerEntity(mc.world, mc.player.getGameProfile());
        dummy.copyFrom(mc.player);
        dummy.headYaw = mc.player.headYaw;
        mc.world.addEntity(dummy); // ✅ fixed: only pass the entity

        // Save camera state
        cameraPos = mc.player.getPos();
        cameraYaw = mc.player.getYaw();
        cameraPitch = mc.player.getPitch();
    }

    @Override
    public void onDeactivate() {
        if (mc.world != null && dummy != null) {
            mc.world.removeEntity(dummy.getId(), Entity.RemovalReason.DISCARDED); // ✅ proper removal
        }
        dummy = null;

        // Reset camera back
        if (mc.player != null) {
            mc.player.setYaw(cameraYaw);
            mc.player.setPitch(cameraPitch);
            mc.player.updatePosition(cameraPos.x, cameraPos.y, cameraPos.z);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        // Movement
        Vec3d forward = Vec3d.fromPolar(0, cameraYaw);
        Vec3d right = Vec3d.fromPolar(0, cameraYaw + 90);

        double velX = 0, velY = 0, velZ = 0;
        double s = speed.get();

        if (mc.options.forwardKey.isPressed()) {
            velX += forward.x * s;
            velZ += forward.z * s;
        }
        if (mc.options.backKey.isPressed()) {
            velX -= forward.x * s;
            velZ -= forward.z * s;
        }
        if (mc.options.rightKey.isPressed()) {
            velX += right.x * s;
            velZ += right.z * s;
        }
        if (mc.options.leftKey.isPressed()) {
            velX -= right.x * s;
            velZ -= right.z * s;
        }
        if (mc.options.jumpKey.isPressed()) velY += s;
        if (mc.options.sneakKey.isPressed()) velY -= s;

        cameraPos = cameraPos.add(velX, velY, velZ);

        // Update view
        mc.getCameraEntity().setYaw(cameraYaw);
        mc.getCameraEntity().setPitch(cameraPitch);
        mc.getCameraEntity().setPos(cameraPos.x, cameraPos.y, cameraPos.z);

        // Sync movement to server if interact mode
        if (mode.get() == Mode.CameraAndInteract) {
            mc.player.networkHandler.sendPacket(
                new PlayerMoveC2SPacket.Full(
                    cameraPos.x, cameraPos.y, cameraPos.z,
                    cameraYaw, cameraPitch,
                    mc.player.isOnGround(), true
                )
            );
        }
    }
}
