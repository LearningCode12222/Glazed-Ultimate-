package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.sound.SoundEvents;

public class TotemPopAlert extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Boolean> playSound = sg.add(new BoolSetting.Builder()
        .name("play-sound")
        .description("Play a sound when someone pops a totem.")
        .defaultValue(true).build()
    );

    private final Setting<Boolean> chatNotify = sg.add(new BoolSetting.Builder()
        .name("chat-notify")
        .description("Send an in-game message when someone pops.")
        .defaultValue(true).build()
    );

    public TotemPopAlert() {
        super(GlazedAddon.pvp, "totem-pop-alert", "Plays a sound or sends a short message when a totem pops.");
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof EntityStatusS2CPacket packet)) return;
        if (packet.getStatus() != 35) return;

        Entity e = packet.getEntity(mc.world);
        if (e instanceof PlayerEntity p) {
            if (playSound.get() && mc.player != null) mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
            if (chatNotify.get()) info("Totem popped: %s", p.getName().getString());
        }
    }
}
