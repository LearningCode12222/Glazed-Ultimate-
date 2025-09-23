package com.nnpg.glazed.modules.main;

import com.mojang.authlib.GameProfile;
import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

public class FreecamV2 extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Freecam flying speed.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderMax(5.0)
        .build()
    );

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("How Freecam renders.")
        .defaultValue(Mode.Basic)
        .build()
    );

    private OtherClientPlayerEntity dummy;
    private Vec3d oldPos;

    public FreecamV2() {
        super(GlazedAddon.CATEGORY, "freecam-v2", "Move your camera outside your body with extra options.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) return;

        // save old position
        oldPos = mc.player.getPos();

        // create dummy at player location
        GameProfile profile = new GameProfile(mc.player.getUuid(), mc.getSession().getUsername());
        dummy = new OtherClientPlayerEntity(mc.world, profile);
        dummy.copyFrom(mc.player);
        mc.world.addEntity(dummy);

        // adjust gamma if using xray mode
        if (mode.get() == Mode.XRay) {
            mc.options.getGamma().setValue(15.0); // fullbright
        }
    }

    @Override
    public void onDeactivate() {
        if (mc.player == null || mc.world == null) return;

        // restore player position
        if (oldPos != null) mc.player.updatePosition(oldPos.x, oldPos.y, oldPos.z);

        // remove dummy
        if (dummy != null) {
            dummy.discard();
            dummy = null;
        }

        // reset gamma
        if (mode.get() == Mode.XRay) {
            mc.options.getGamma().setValue(1.0);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        // freecam movement
        double spd = speed.get();
        Vec3d forward = Vec3d.fromPolar(0, mc.player.getYaw()).normalize().multiply(spd);
        if (mc.options.forwardKey.isPressed()) mc.player.setVelocity(forward);
        if (mc.options.backKey.isPressed()) mc.player.setVelocity(forward.negate());
        if (mc.options.jumpKey.isPressed()) mc.player.addVelocity(0, spd, 0);
        if (mc.options.sneakKey.isPressed()) mc.player.addVelocity(0, -spd, 0);

        // cancel collisions
        mc.player.noClip = true;
    }

    public enum Mode {
        Basic,
        XRay
    }
}
