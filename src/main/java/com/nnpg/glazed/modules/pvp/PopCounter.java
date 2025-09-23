package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TotemPopCounter extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Boolean> announce = sg.add(new BoolSetting.Builder()
        .name("announce")
        .description("Send chat message when someone pops a totem.")
        .defaultValue(true).build()
    );

    private final Map<UUID, Integer> pops = new HashMap<>();

    public TotemPopCounter() {
        super(GlazedAddon.pvp, "totem-pop-counter", "Counts how many totems players have popped.");
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof EntityStatusS2CPacket packet)) return;
        if (packet.getStatus() != 35) return; // 35 = totem pop

        Entity e = packet.getEntity(mc.world);
        if (e instanceof PlayerEntity p) {
            UUID id = p.getUuid();
            pops.put(id, pops.getOrDefault(id, 0) + 1);
            int count = pops.get(id);
            if (announce.get()) info("%s popped a totem! (total: %d)", p.getName().getString(), count);
        }
    }

    @Override
    public String getInfoString() {
        return String.valueOf(pops.values().stream().mapToInt(Integer::intValue).sum());
    }

    public Map<UUID, Integer> getPops() {
        return new HashMap<>(pops);
    }
}
