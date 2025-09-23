package com.nnpg.glazed.modules.pvp;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.entity.Entity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.Hand;

import com.nnpg.glazed.GlazedAddon;

public class TotemSwipe extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> onlyOnSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-self")
        .description("Only swings if your own totem pops.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> offhandOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("offhand-only")
        .description("Only swings offhand if the popped totem was in offhand.")
        .defaultValue(true)
        .build()
    );

    public TotemSwipe() {
        super(GlazedAddon.pvp, "totem-swipe", "Swings your hand when a totem pops.");
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.packet instanceof EntityStatusS2CPacket packet)) return;

        // 35 = totem pop animation
        if (packet.getStatus() == 35) {
            Entity entity = packet.getEntity(mc.world);
            if (entity == null) return;

            if (onlyOnSelf.get() && entity != mc.player) return;

            if (offhandOnly.get()) {
                if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
                    mc.player.swingHand(Hand.OFF_HAND);
                } else {
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
            } else {
                mc.player.swingHand(mc.player.getActiveHand());
            }
        }
    }
}
