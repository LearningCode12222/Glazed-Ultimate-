package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

public class FreecamV2 extends Module {
    private OtherClientPlayerEntity clone;
    private Vec3d camPos;
    private float camYaw, camPitch;

    private final DoubleSetting speed = settings.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Freecam fly speed.")
        .defaultValue(0.7)
        .min(0.1)
        .sliderMax(5)
        .build()
    );

    public FreecamV2() {
        super(Categories.Render, GlazedAddon.NAME, "FreecamV2", "Detach the camera from your player.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) return;

        // Clone player as stand-in
        clone = new OtherClientPlayerEntity(mc.world, mc.player.getGameProfile());
        clone.copyPositionAndRotation(mc.player);
        clone.headYaw = mc.player.headYaw;
        clone.bodyYaw = mc.player.bodyYaw;
        mc.world.addEntity(clone.getId(), clone);

        // Save camera state
        camPos = mc.player.getPos();
        camYaw = mc.player.getYaw();
        camPitch = mc.player.getPitch();
    }

    @Override
    public void onDeactivate() {
        if (mc.world != null && clone != null) {
            mc.world.removeEntity(clone.getId(), net.minecraft.entity.Entity.RemovalReason.DISCARDED);
        }
        clone = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        // Handle freecam movement
        double forward = 0, strafe = 0;
        if (mc.options.forwardKey.isPressed()) forward++;
        if (mc.options.backKey.isPressed()) forward--;
        if (mc.options.leftKey.isPressed()) strafe++;
        if (mc.options.rightKey.isPressed()) strafe--;

        Vec3d forwardVec = Vec3d.fromPolar(0, camYaw).normalize();
        Vec3d strafeVec = Vec3d.fromPolar(0, camYaw + 90).normalize();
        Vec3d move = forwardVec.multiply(forward).add(strafeVec.multiply(strafe));

        if (move.lengthSquared() > 0) move = move.normalize().multiply(speed.get());
        camPos = camPos.add(move);

        if (mc.options.jumpKey.isPressed()) camPos = camPos.add(0, speed.get(), 0);
        if (mc.options.sneakKey.isPressed()) camPos = camPos.add(0, -speed.get(), 0);

        // Lock yaw/pitch to your current mouse movement
        camYaw = mc.player.getYaw();
        camPitch = mc.player.getPitch();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.gameRenderer.getCamera() != null) {
            Camera camera = mc.gameRenderer.getCamera();
            camera.setPos(camPos.x, camPos.y, camPos.z);
            camera.setRotation(camYaw, camPitch);
        }
    }
}
