package com.nnpg.glazed.modules.pvp;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import java.util.HashMap;
import java.util.Map;
import com.nnpg.glazed.GlazedAddon;

public class PopCounter extends Module {
    private final Map<Integer, Integer> pops = new HashMap<>();

    public PopCounter() {
        super(GlazedAddon.pvp, "pop-counter", "Tracks how many times players have popped totems.");
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.packet instanceof EntityStatusS2CPacket packet)) return;
        if (packet.getStatus() != 35) return; // 35 = Totem pop

        Entity entity = packet.getEntity(mc.world);
        if (entity == null || entity == mc.player) return;

        int id = entity.getId();
        pops.put(id, pops.getOrDefault(id, 0) + 1);

        info(entity.getName().getString() + " has popped " + pops.get(id) + " totem(s).");
    }

    @Override
    public void onDeactivate() {
        pops.clear();
    }
}
