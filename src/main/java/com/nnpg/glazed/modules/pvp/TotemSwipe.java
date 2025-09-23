package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;

public class TotemSwipe extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    public enum WeaponType { Sword, Axe }
    public enum Animation { Swing, Crit, None }

    private final Setting<WeaponType> weaponType = sg.add(new EnumSetting.Builder<WeaponType>()
        .name("weapon-type")
        .description("Which weapon to fake the attack with.")
        .defaultValue(WeaponType.Sword)
        .build()
    );

    private final Setting<Animation> animation = sg.add(new EnumSetting.Builder<Animation>()
        .name("animation")
        .description("What animation to play when attacking.")
        .defaultValue(Animation.Swing)
        .build()
    );

    private final Setting<Integer> switchBackTicks = sg.add(new IntSetting.Builder()
        .name("switch-back-ticks")
        .description("Ticks to wait before switching back (0 = instant).")
        .defaultValue(0)
        .min(0).max(5)
        .build()
    );

    private int originalSlot = -1;
    private int ticksRemaining = 0;
    private boolean busy = false;

    public TotemSwipe() {
        super(GlazedAddon.pvp, "totem-swipe", "Quickly swap to a weapon, attack, then swap back so it looks like the totem hit.");
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (mc.player == null || mc.interactionManager == null || busy) return;

        int weaponSlot = findWeaponSlot(weaponType.get());
        if (weaponSlot == -1) return;

        originalSlot = mc.player.getInventory().selectedSlot;

        busy = true;

        // Switch to weapon
        mc.player.getInventory().selectedSlot = weaponSlot;
        if (mc.player.networkHandler != null) {
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(weaponSlot));
        }

        // Attack
        try {
            mc.interactionManager.attackEntity(mc.player, event.entity);
            playAnimation();
        } catch (Exception e) {
            restoreImmediate();
            return;
        }

        // Restore
        ticksRemaining = Math.max(0, switchBackTicks.get());
        if (ticksRemaining == 0) restoreImmediate();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!busy) return;

        if (ticksRemaining > 0) ticksRemaining--;

        if (ticksRemaining <= 0) {
            restoreImmediate();
        }
    }

    private void restoreImmediate() {
        if (originalSlot >= 0 && mc.player != null) {
            mc.player.getInventory().selectedSlot = originalSlot;
            if (mc.player.networkHandler != null) {
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(originalSlot));
            }
        }
        originalSlot = -1;
        busy = false;
        ticksRemaining = 0;
    }

    private void playAnimation() {
        switch (animation.get()) {
            case Swing -> mc.player.swingHand(Hand.MAIN_HAND);
            case Crit -> mc.player.swingHand(Hand.MAIN_HAND, true); // force crit animation
            case None -> {} // do nothing
        }
    }

    private int findWeaponSlot(WeaponType type) {
        if (mc.player == null) return -1;
        int sel = mc.player.getInventory().selectedSlot;
        int bestSlot = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            Item item = stack.getItem();

            if (type == WeaponType.Sword && isSword(item)) {
                bestSlot = pickClosest(bestSlot, i, sel);
            } else if (type == WeaponType.Axe && isAxe(item)) {
                bestSlot = pickClosest(bestSlot, i, sel);
            }
        }

        return bestSlot;
    }

    private int pickClosest(int current, int candidate, int sel) {
        if (current == -1) return candidate;
        int curDist = Math.abs(current - sel);
        int candDist = Math.abs(candidate - sel);
        return candDist < curDist ? candidate : current;
    }

    private boolean isSword(Item item) {
        return item == Items.NETHERITE_SWORD || item == Items.DIAMOND_SWORD ||
               item == Items.IRON_SWORD || item == Items.STONE_SWORD ||
               item == Items.GOLDEN_SWORD || item == Items.WOODEN_SWORD;
    }

    private boolean isAxe(Item item) {
        return item == Items.NETHERITE_AXE || item == Items.DIAMOND_AXE ||
               item == Items.IRON_AXE || item == Items.STONE_AXE ||
               item == Items.GOLDEN_AXE || item == Items.WOODEN_AXE;
    }
}
