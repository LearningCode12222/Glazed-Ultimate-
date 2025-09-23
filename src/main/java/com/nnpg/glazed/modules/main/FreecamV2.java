package com.nnpg.glazed.modules.render;

import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import com.nnpg.glazed.GlazedAddon;

public class FreecamV2 extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("How fast you move while in freecam.")
        .defaultValue(1.0)
        .min(0.1)
        .max(5.0)
        .sliderMax(5.0)
        .build()
    );

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Type of freecam to use.")
        .defaultValue(Mode.Basic)
        .build()
    );

    private final Setting<Boolean> remoteMine = sgGeneral.add(new BoolSetting.Builder()
        .name("remote-mine")
        .description("Mines with your real body instead of freecam position.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showDummy = sgGeneral.add(new BoolSetting.Builder()
        .name("show-dummy")
        .description("Renders your real body while you are in freecam.")
        .defaultValue(true)
        .build()
    );

    private Vec3d oldPos;
    private float oldYaw, oldPitch;
    private OtherClientPlayerEntity dummy;

    public FreecamV2() {
        super(GlazedAddon.render, "freecam-v2", "Move your camera outside your body with extra options.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;

        oldPos = mc.player.getPos();
        oldYaw = mc.player.getYaw();
        oldPitch = mc.player.getPitch();

        if (showDummy.get()) {
            dummy = new OtherClientPlayerEntity(mc.world, mc.player.getGameProfile());
            dummy.copyPositionAndRotation(mc.player);
            dummy.headYaw = mc.player.headYaw;
            mc.world.addEntity(dummy.getId(), dummy);
        }
    }

    @Override
    public void onDeactivate() {
        if (mc.player == null) return;

        mc.player.updatePosition(oldPos.x, oldPos.y, oldPos.z);
        mc.player.setYaw(oldYaw);
        mc.player.setPitch(oldPitch);

        if (dummy != null) {
            mc.world.removeEntity(dummy.getId(), RemovalReason.DISCARDED);
            dummy = null;
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mode.get() == Mode.XRay) {
            mc.worldRenderer.reload(); // reload chunks so walls don't render properly (basic xray)
        }
    }

    @EventHandler
    private void onStartBreakingBlock(StartBreakingBlockEvent event) {
        if (remoteMine.get()) {
            BlockPos target = event.blockPos;
            mc.interactionManager.attackBlock(target, event.direction);
            event.cancel(); // cancel local freecam mining
        }
    }

    public enum Mode {
        Basic,
        XRay
    }
}
