package com.nnpg.glazed.modules.pvp;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.sound.SoundEvents;
import com.nnpg.glazed.GlazedAddon;

public class TotemPopAlert extends Module {
    public TotemPopAlert() {
        super(GlazedAddon.pvp, "totem-pop-alert", "Plays a sound when someone pops a totem.");
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.packet instanceof EntityStatusS2CPacket packet)) return;
        if (packet.getStatus() != 35) return; // Totem pop packet

        Entity entity = packet.getEntity(mc.world);
        if (entity == null || entity == mc.player) return;

        mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        info(entity.getName().getString() + " popped a totem!");
    }
}
