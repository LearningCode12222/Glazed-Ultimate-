package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.VersionUtil;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

public class TotemSwipe extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> onlyWithTotem = sgGeneral.add(new BoolSetting.Builder()
        .name("only-with-totem")
        .description("Only triggers when holding a totem in hand.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> swordSlot = sgGeneral.add(new IntSetting.Builder()
        .name("sword-slot")
        .description("Preferred hotbar slot (0-8) to use as sword. If that slot doesn't contain a sword, module will auto-find the first sword in hotbar.")
        .defaultValue(0)
        .min(0).max(8)
        .build()
    );

    // internal state for the staged swap/attack/swap-back
    private int stage = 0; // 0 = idle, 1 = swapped -> attack now, 2 = swap back next tick
    private int prevSlot = -1;
    private int targetSwordSlot = -1;
    private Entity targetEntity = null;

    public TotemSwipe() {
        super(GlazedAddon.pvp, "totem-swipe", "Quickly swap to a sword to make an attack look like it came from a totem.");
    }

    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        if (mc.player == null || mc.interactionManager == null) return;

        // If requested, only do this when holding a totem in either hand
        if (onlyWithTotem.get()) {
            ItemStack main = mc.player.getMainHandStack();
            ItemStack off = mc.player.getOffHandStack();
            if (main.getItem() != Items.TOTEM_OF_UNDYING && off.getItem() != Items.TOTEM_OF_UNDYING) return;
        }

        // Determine sword slot (preferred first, fallback to auto-find)
        int preferred = swordSlot.get();
        int found = isSwordAt(preferred) ? preferred : findSwordInHotbar();
        if (found == -1) return; // no sword found -> do nothing

        // Save state for tick handler
        prevSlot = VersionUtil.getSelectedSlot(mc.player);
        targetSwordSlot = found;
        targetEntity = event.entity;
        stage = 1; // start the swap/attack flow on next tick
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.interactionManager == null) return;

        switch (stage) {
            case 0 -> {
                // idle
            }
            case 1 -> {
                // switch to sword slot
                VersionUtil.setSelectedSlot(mc.player, targetSwordSlot);

                // Perform attack using the stored entity (guard null)
                if (targetEntity != null && targetEntity.isAlive()) {
                    try {
                        mc.interactionManager.attackEntity(mc.player, targetEntity);
                        mc.player.swingHand(Hand.MAIN_HAND);
                    } catch (Exception ignore) {}
                }

                // Next tick we'll swap back
                stage = 2;
            }
            case 2 -> {
                // switch back to previous slot
                VersionUtil.setSelectedSlot(mc.player, prevSlot);

                // reset state
                prevSlot = -1;
                targetSwordSlot = -1;
                targetEntity = null;
                stage = 0;
            }
        }
    }

    private int findSwordInHotbar() {
        for (int i = 0; i < 9; i++) {
            Item item = mc.player.getInventory().getStack(i).getItem();
            if (isSword(item)) return i;
        }
        return -1;
    }

    private boolean isSwordAt(int slot) {
        if (mc.player == null) return false;
        if (slot < 0 || slot > 8) return false;
        Item item = mc.player.getInventory().getStack(slot).getItem();
        return isSword(item);
    }

    private boolean isSword(Item item) {
        return item == Items.NETHERITE_SWORD || item == Items.DIAMOND_SWORD ||
               item == Items.IRON_SWORD || item == Items.GOLDEN_SWORD ||
               item == Items.STONE_SWORD || item == Items.WOODEN_SWORD;
    }
}
