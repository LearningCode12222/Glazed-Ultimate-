package com.nnpg.glazed.modules.pvp;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.entity.Entity;

import com.nnpg.glazed.GlazedAddon;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PopCounter - tracks totem pops per-entity and total pops this session.
 */
public class PopCounter extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Boolean> includeSelf = sg.add(new BoolSetting.Builder()
        .name("count-self")
        .description("Count your own totem pops as well.")
        .defaultValue(false)
        .build()
    );

    private final Map<UUID, Integer> pops = new HashMap<>();
    private int totalPops = 0;

    public PopCounter() {
        super(GlazedAddon.pvp, "pop-counter", "Counts totem pops per player and total pops.");
    }

    @Override
    public void onActivate() {
        pops.clear();
        totalPops = 0;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.packet instanceof EntityStatusS2CPacket packet)) return;
        // 35 is the totem pop status
        if (packet.getStatus() != 35) return;

        Entity e = packet.getEntity(mc.world);
        if (e == null) return;

        if (!includeSelf.get() && e == mc.player) return;

        UUID id = e.getUuid();
        int newCount = pops.getOrDefault(id, 0) + 1;
        pops.put(id, newCount);
        totalPops++;

        String name = e.getName().getString();
        info("%s popped a totem (%d)", name, newCount);
    }

    @Override
    public String getInfoString() {
        return String.valueOf(totalPops);
    }

    /** Returns the per-uuid counts for any external use. */
    public Map<UUID, Integer> getPops() {
        return new HashMap<>(pops);
    }
}
