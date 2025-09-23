package com.wazify.pyra.modules.pvp;

import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;

import com.wazify.pyra.PyraAddon;

public class TotemSwipe extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // Only trigger when holding totem
    private final Setting<Boolean> onlyWithTotem = sgGeneral.add(new BoolSetting.Builder()
        .name("only-with-totem")
        .description("Only swipes when holding a totem in main hand.")
        .defaultValue(true)
        .build()
    );

    // Weapon mode: Sword, Axe, Auto
    private final Setting<WeaponMode> weaponMode = sgGeneral.add(new EnumSetting.Builder<WeaponMode>()
        .name("weapon-mode")
        .description("Choose which weapon to use when swiping.")
        .defaultValue(WeaponMode.Sword)
        .build()
    );

    // Hotbar slot (if not auto mode)
    private final Setting<Integer> weaponSlot = sgGeneral.add(new IntSetting.Builder()
        .name("weapon-slot")
        .description("Hotbar slot (0-8) of your weapon (ignored in Auto mode).")
        .defaultValue(0)
        .range(0, 8)
        .build()
    );

    // Attack delay before swapping back
    private final Setting<Integer> attackDelay = sgGeneral.add(new IntSetting.Builder()
        .name("attack-delay")
        .description("Delay (in ticks) before swapping back to totem.")
        .defaultValue(1)
        .range(0, 10)
        .sliderRange(0, 10)
        .build()
    );

    // Swing style
    private final Setting<SwingStyle> swingStyle = sgGeneral.add(new EnumSetting.Builder<SwingStyle>()
        .name("swing-style")
        .description("How the swing animation should be played.")
        .defaultValue(SwingStyle.MainHand)
        .build()
    );

    // Debug logs
    private final Setting<Boolean> debugLogs = sgGeneral.add(new BoolSetting.Builder()
        .name("debug-logs")
        .description("Print debug messages in chat for testing.")
        .defaultValue(false)
        .build()
    );

    public TotemSwipe() {
        super(PyraAddon.pvp, "totem-swipe", "Attack with sword/axe while holding a totem (visual trick).");
    }

    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        if (mc.player == null || mc.interactionManager == null) return;

        ItemStack mainHand = mc.player.getMainHandStack();

        // Check totem condition
        if (onlyWithTotem.get() && mainHand.getItem() != Items.TOTEM_OF_UNDYING) {
            if (debugLogs.get()) info("Cancelled: Not holding totem.");
            return;
        }

        int originalSlot = mc.player.getInventory().selectedSlot;
        int targetSlot = resolveWeaponSlot();

        // Safety check
        if (targetSlot == -1) {
            if (debugLogs.get()) info("No valid weapon found.");
            return;
        }

        // Switch to weapon
        switchToSlot(targetSlot);

        // Perform the attack
        mc.interactionManager.attackEntity(mc.player, event.entity);

        // Swing animation
        playSwing();

        // Optionally delay before switching back
        if (attackDelay.get() > 0) {
            mc.execute(() -> mc.player.getInventory().selectedSlot = originalSlot, attackDelay.get());
            mc.execute(() -> mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(originalSlot)), attackDelay.get());
        } else {
            // Instant revert
            switchToSlot(originalSlot);
        }

        if (debugLogs.get()) {
            info("Swiped entity with slot " + targetSlot + " (" + mc.player.getInventory().getStack(targetSlot).getItem().getName().getString() + ")");
        }
    }

    // Resolves which slot to use based on mode
    private int resolveWeaponSlot() {
        if (weaponMode.get() == WeaponMode.Auto) {
            for (int i = 0; i < 9; i++) {
                Item item = mc.player.getInventory().getStack(i).getItem();
                if (item == Items.NETHERITE_SWORD || item == Items.DIAMOND_SWORD || 
                    item == Items.IRON_SWORD || item == Items.NETHERITE_AXE || 
                    item == Items.DIAMOND_AXE || item == Items.IRON_AXE) {
                    return i;
                }
            }
            return -1;
        }
        return weaponSlot.get();
    }

    // Handles slot switching safely
    private void switchToSlot(int slot) {
        mc.player.getInventory().selectedSlot = slot;
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
    }

    // Handles swing animations
    private void playSwing() {
        switch (swingStyle.get()) {
            case MainHand -> mc.player.swingHand(Hand.MAIN_HAND);
            case OffHand -> mc.player.swingHand(Hand.OFF_HAND);
            case Both -> {
                mc.player.swingHand(Hand.MAIN_HAND);
                mc.player.swingHand(Hand.OFF_HAND);
            }
        }
    }

    // Weapon modes
    public enum WeaponMode {
        Sword, Axe, Auto
    }

    // Swing styles
    public enum SwingStyle {
        MainHand, OffHand, Both
    }
}
