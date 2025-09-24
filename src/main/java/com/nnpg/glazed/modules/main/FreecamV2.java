package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

public class FreecamV2 extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Your speed while in freecam.")
        .defaultValue(1.0)
        .min(0.1)
        .build()
    );

    private OtherClientPlayerEntity dummy;
    private Perspective oldPerspective;
    private PlayerEntity oldCamera;

    public FreecamV2() {
        super(Categories.Render, "freecam-v2", "Allows the camera to move away from the player.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) return;

        // save old perspective + camera
        oldPerspective = mc.options.getPerspective();
        oldCamera = mc.cameraEntity instanceof PlayerEntity ? (PlayerEntity) mc.cameraEntity : null;

        // create dummy clone of player (so body stays visible)
        dummy = new OtherClientPlayerEntity(mc.world, mc.getSession().getProfile());
        dummy.copyPositionAndRotation(mc.player);
        dummy.setHeadYaw(mc.player.getHeadYaw());
        mc.world.addEntity(dummy.getId(), dummy);

        // switch camera to dummy
        mc.cameraEntity = dummy;
    }

    @Override
    public void onDeactivate() {
        if (mc.player == null || mc.world == null) return;

        // restore camera
        if (oldCamera != null) mc.cameraEntity = oldCamera;
        mc.options.setPerspective(oldPerspective);

        // remove dummy
        if (dummy != null) {
            mc.world.removeEntity(dummy.getId(), net.minecraft.entity.Entity.RemovalReason.DISCARDED);
            dummy = null;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (dummy == null) return;

        double s = speed.get();
        Vec3d forward = Vec3d.fromPolar(0, mc.player.getYaw());
        Vec3d right = Vec3d.fromPolar(0, mc.player.getYaw() + 90);

        double velX = 0, velY = 0, velZ = 0;

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

        dummy.updatePosition(dummy.getX() + velX, dummy.getY() + velY, dummy.getZ() + velZ);
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        // block movement packets so server doesn’t rubberband
        if (event.packet instanceof PlayerMoveC2SPacket) {
            event.cancel();
        }
    }
}
