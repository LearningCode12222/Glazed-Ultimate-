package com.nnpg.glazed.modules.main;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;

import com.nnpg.glazed.GlazedAddon;

public class FreecamV2 extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Movement speed while in freecam.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderMax(5.0)
        .build()
    );

    public enum FreecamMode {
        Basic,
        XRay
    }

    private final Setting<FreecamMode> mode = sgGeneral.add(new EnumSetting.Builder<FreecamMode>()
        .name("mode")
        .description("Freecam mode to use.")
        .defaultValue(FreecamMode.Basic)
        .build()
    );

    private OtherClientPlayerEntity dummy;

    public FreecamV2() {
        // use a valid category from GlazedAddon (main/pvp/render/etc.)
        super(GlazedAddon.main, "freecam-v2", "Move your camera outside your body with extra options.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) return;

        // create a dummy entity to represent your body
        dummy = new OtherClientPlayerEntity(mc.world, mc.getSession().getProfile());
        dummy.copyPositionAndRotation(mc.player);
        dummy.setHeadYaw(mc.player.getHeadYaw());

        // ✅ Corrected: addEntity only takes one arg
        mc.world.addEntity(dummy);
    }

    @Override
    public void onDeactivate() {
        if (mc.world != null && dummy != null) {
            // ✅ Corrected: discard entity instead of using RemovalReason
            dummy.discard();
            dummy = null;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        // Apply speed setting to movement
        mc.player.getAbilities().flying = true;
        mc.player.getAbilities().setFlySpeed(speed.get().floatValue() / 10.0f);

        // Mode handling
        if (mode.get() == FreecamMode.XRay) {
            mc.options.gamma = 15.0; // fullbright/xray feel
        } else {
            mc.options.gamma = 1.0; // reset gamma
        }
    }
}
