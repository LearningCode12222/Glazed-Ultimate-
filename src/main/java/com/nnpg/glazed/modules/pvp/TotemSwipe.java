package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.VersionUtil;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;

/**
 * TotemSwipe - packet-only hotbar swap to nearest sword, attack, then restore.
 * Server sees the sword attack, client never visually switches.
 */
public class TotemSwipe extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> onlyWithTotem = sgGeneral.add(new BoolSetting.Builder()
        .name("only-with-totem")
        .description("Only triggers when holding a totem in either hand.")
        .defaultValue(true)
        .build()
    );

    public TotemSwipe() {
        super(GlazedAddon.pvp, "totem-swipe", "Packet-swap to nearest sword, attack, then swap back so it looks like the totem did the damage.");
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (mc.player == null || mc.interactionManager == null || mc.getNetworkHandler() == null) return;

        // Require totem in either hand if configured
        if (onlyWithTotem.get()) {
            ItemStack main = mc.player.getMainHandStack();
            ItemStack off = mc.player.getOffHandStack();
            if (main.getItem() != Items.TOTEM_OF_UNDYING && off.getItem() != Items.TOTEM_OF_UNDYING) return;
        }

        Entity target = event.entity;
        if (target == null) return;

        int clientSlot = VersionUtil.getSelectedSlot(mc.player);
        int swordSlot = findClosestSwordSlot(clientSlot);
        if (swordSlot == -1) return; // no sword found

        // Send packet to server telling it we selected the sword slot (client UI not changed)
        try {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(swordSlot));
        } catch (Exception ignored) {}

        // Attack (interaction manager will send AttackEntity packet using the server's selected slot)
        try {
            mc.interactionManager.attackEntity(mc.player, target);
            // local swing (also not visually changing selected slot)
            mc.player.swingHand(Hand.MAIN_HAND);
        } catch (Exception ignored) {}

        // Restore server-side selected slot to original
        try {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(clientSlot));
        } catch (Exception ignored) {}
    }

    private int findClosestSwordSlot(int currentSlot) {
        if (mc.player == null) return -1;

        int bestSlot = -1;
        int bestDist = Integer.MAX_VALUE;

        for (int i = 0; i < 9; i++) {
            Item it = mc.player.getInventory().getStack(i).getItem();
            if (isSword(it)) {
                int dist = Math.abs(currentSlot - i);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestSlot = i;
                }
            }
        }

        return bestSlot;
    }

    private boolean isSword(Item item) {
        return item == Items.NETHERITE_SWORD || item == Items.DIAMOND_SWORD ||
               item == Items.IRON_SWORD || item == Items.GOLDEN_SWORD ||
               item == Items.STONE_SWORD || item == Items.WOODEN_SWORD;
    }
}
