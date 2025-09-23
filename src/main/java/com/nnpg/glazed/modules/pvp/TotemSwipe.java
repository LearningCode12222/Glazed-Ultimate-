package com.nnpg.glazed.modules.pvp;

import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

import com.nnpg.glazed.GlazedAddon;

/**
 * TotemSwipe Module
 *
 * This makes it look like you are attacking with a totem,
 * but actually swaps to a sword or axe for the damage
 * and then swaps back instantly.
 */
public class TotemSwipe extends Module {
    // ------------------------------
    // Settings
    // ------------------------------
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // Trigger only when holding a totem
    private final Setting<Boolean> onlyWithTotem = sgGeneral.add(new BoolSetting.Builder()
        .name("only-with-totem")
        .description("Only activates if you're holding a totem.")
        .defaultValue(true)
        .build()
    );

    // Weapon type to use (sword or axe)
    public enum WeaponMode {
        Sword,
        Axe
    }

    private final Setting<WeaponMode> weaponMode = sgGeneral.add(new EnumSetting.Builder<WeaponMode>()
        .name("weapon-mode")
        .description("Which weapon to use for the hidden attack.")
        .defaultValue(WeaponMode.Sword)
        .build()
    );

    // Whether to swing animation is forced
    private final Setting<Boolean> doSwing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing-animation")
        .description("Plays the hand swing animation when the attack is triggered.")
        .defaultValue(true)
        .build()
    );

    // ------------------------------
    // Constructor
    // ------------------------------
    public TotemSwipe() {
        super(GlazedAddon.pvp, "totem-swipe", "Attacks with a sword or axe while showing a totem.");
    }

    // ------------------------------
    // Event Listener
    // ------------------------------
    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        if (mc.player == null || mc.world == null) return;

        // Check if we are holding a totem when required
        if (onlyWithTotem.get() && mc.player.getMainHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
            return;
        }

        // Save the player’s current hotbar slot
        int originalSlot = mc.player.getInventory().selectedSlot;

        // Try to find the closest valid weapon in hotbar
        int weaponSlot = findWeaponSlot();

        if (weaponSlot == -1) {
            // No valid weapon found, cancel
            return;
        }

        // Swap to the weapon
        mc.player.getInventory().selectedSlot = weaponSlot;

        // Perform the attack
        Entity target = event.entity;
        if (target != null) {
            mc.interactionManager.attackEntity(mc.player, target);

            if (doSwing.get()) {
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }

        // Swap back instantly to the original slot (totem)
        mc.player.getInventory().selectedSlot = originalSlot;
    }

    // ------------------------------
    // Helper Methods
    // ------------------------------

    /**
     * Finds the closest hotbar slot that matches the chosen weapon mode.
     * @return the slot index, or -1 if not found
     */
    private int findWeaponSlot() {
        Item targetWeapon;

        if (weaponMode.get() == WeaponMode.Sword) {
            targetWeapon = Items.NETHERITE_SWORD;
        } else {
            targetWeapon = Items.NETHERITE_AXE;
        }

        // Look through hotbar slots 0-8
        for (int i = 0; i < 9; i++) {
            Item slotItem = mc.player.getInventory().getStack(i).getItem();
            if (slotItem == targetWeapon) {
                return i;
            }
        }

        // Nothing found
        return -1;
    }
}
