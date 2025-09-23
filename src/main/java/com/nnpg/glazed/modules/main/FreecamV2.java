package com.nnpg.glazed.modules.main;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.client.network.OtherClientPlayerEntity;

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
        // fallback to Misc category if your addon doesn't define its own
        super(Category.Misc, "freecam-v2", "Move your camera outside your body with extra options.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) return;

        // correct constructor for OtherClientPlayerEntity
        dummy = new OtherClientPlayerEntity(mc.world,
            mc.getSession().getUuidOrNull(),
            mc.getSession().getUsername()
        );
        dummy.copyPositionAndRotation(mc.player);
        dummy.setHeadYaw(mc.player.getHeadYaw());

        mc.world.addEntity(dummy);
    }

    @Override
    public void onDeactivate() {
        if (mc.world != null && dummy != null) {
            dummy.discard();
            dummy = null;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        mc.player.getAbilities().flying = true;
        mc.player.getAbilities().setFlySpeed(speed.get().floatValue() / 10.0f);

        if (mode.get() == FreecamMode.XRay) {
            mc.options.getGamma().setValue(15.0); // ✅ correct
        } else {
            mc.options.getGamma().setValue(1.0);  // ✅ correct
        }
    }
}
