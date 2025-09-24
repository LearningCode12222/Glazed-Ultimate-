package com.nnpg.glazed.modules.main;

import com.mojang.authlib.GameProfile;
import meteordevelopment.meteorclient.events.meteor.MouseButtonEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import meteordevelopment.meteorclient.utils.misc.input.KeyAction;

public class FreecamV2 extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Freecam flying speed.")
        .defaultValue(1.0)
        .min(0.0)
        .build()
    );

    private double speedValue;
    private Perspective oldPerspective;
    private PlayerEntity oldCamera;
    private OtherClientPlayerEntity dummy;

    // Save player rotation
    private float frozenYaw, frozenPitch;

    public FreecamV2() {
        super(Categories.Render, "freecam-v2", "Camera only freecam. Player stays frozen but can still mine.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) return;

        oldPerspective = mc.options.getPerspective();
        oldCamera = mc.cameraEntity instanceof PlayerEntity ? (PlayerEntity) mc.cameraEntity : null;

        frozenYaw = mc.player.getYaw();
        frozenPitch = mc.player.getPitch();

        GameProfile profile = new GameProfile(mc.getSession().getUuidOrNull(), mc.getSession().getUsername());
        dummy = new OtherClientPlayerEntity(mc.world, profile);
        dummy.copyPositionAndRotation(mc.player);
        dummy.setHeadYaw(mc.player.getHeadYaw());

        mc.world.addEntity(dummy);
        mc.cameraEntity = dummy;

        speedValue = speed.get();
    }

    @Override
    public void onDeactivate() {
        if (mc.player == null || mc.world == null) return;

        if (dummy != null) {
            dummy.discard();
            dummy = null;
        }

        if (oldCamera != null) {
            mc.cameraEntity = oldCamera;
        }

        mc.options.setPerspective(oldPerspective);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (dummy == null) return;

        Vec3d move = Vec3d.ZERO;
        if (mc.options.forwardKey.isPressed()) move = move.add(0, 0, 1);
        if (mc.options.backKey.isPressed()) move = move.add(0, 0, -1);
        if (mc.options.leftKey.isPressed()) move = move.add(1, 0, 0);
        if (mc.options.rightKey.isPressed()) move = move.add(-1, 0, 0);
        if (mc.options.jumpKey.isPressed()) move = move.add(0, 1, 0);
        if (mc.options.sneakKey.isPressed()) move = move.add(0, -1, 0);

        if (move.lengthSquared() > 0) {
            move = move.normalize().multiply(speedValue);
            dummy.setPos(dummy.getX() + move.x, dummy.getY() + move.y, dummy.getZ() + move.z);
        }

        // Lock player rotation
        mc.player.setYaw(frozenYaw);
        mc.player.setPitch(frozenPitch);
    }

    // Cancel all interactions except mining
    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (dummy == null) return;

        if (!(event.packet instanceof PlayerActionC2SPacket action
            && action.getAction() == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK)) {
            event.cancel();
        }
    }

    // Handle mining manually from frozen player position
    @EventHandler
    private void onMouse(MouseButtonEvent event) {
        if (dummy == null) return;

        if (event.button == 0 && event.action == KeyAction.Press) {
            // Raycast from player with frozen yaw/pitch
            BlockHitResult bhr = (BlockHitResult) mc.player.raycast(
                5.0,
                mc.getRenderTickCounter().getTickDelta(true), // smooth delta
                false
            );

            if (bhr != null) {
                BlockPos target = bhr.getBlockPos();
                mc.interactionManager.attackBlock(target, bhr.getSide());
                mc.player.swingHand(mc.player.getActiveHand());
            }
        }
    }
}
