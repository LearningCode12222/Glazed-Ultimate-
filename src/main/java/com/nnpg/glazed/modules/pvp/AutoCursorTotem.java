package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;
import net.minecraft.text.LiteralText;

/**
 * AutoCursorTotem (SAFE helper)
 *
 * - Client-side helper that locates a Totem of Undying in your inventory.
 * - Optionally opens the inventory (client-side), displays which slot index has a totem,
 *   and closes the inventory after a configurable delay.
 *
 * IMPORTANT: This module does NOT move the cursor or click. It only helps you locate the totem so you can manually move it.
 */
public class AutoCursorTotem extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> openDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("open-delay-ticks")
        .description("How many ticks the inventory stays open after a totem is found (if auto-open enabled).")
        .defaultValue(20.0)
        .min(0.0)
        .sliderMax(200.0)
        .build()
    );

    private final Setting<Boolean> autoOpenInventory = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-open-inventory")
        .description("Automatically open the inventory UI when a totem is missing from offhand.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> manualMode = sgGeneral.add(new BoolSetting.Builder()
        .name("manual-mode")
        .description("If true, only runs if you manually opened the inventory (prevents auto open).")
        .defaultValue(false)
        .build()
    );

    private int closeCounter = 0;

    public AutoCursorTotem() {
        super(Categories.Defense /* or PVP depending on your category enum */, "auto-cursor-totem-safe", "Helper: locate totems and open inventory for manual equipping (no auto-click).");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        // If offhand already has a totem, we do nothing; close inventory if we opened it
        if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
            if (mc.currentScreen instanceof InventoryScreen && closeCounter > 0) {
                closeCounter = 0;
                mc.setScreen(null); // close
            }
            return;
        }

        // Not in inventory and auto-open disabled or manual mode requires manual open: do nothing
        if (manualMode.get() && !(mc.currentScreen instanceof InventoryScreen)) return;

        // If auto-open enabled and inventory not open, open it now
        if (autoOpenInventory.get() && !(mc.currentScreen instanceof InventoryScreen)) {
            mc.setScreen(new InventoryScreen(mc.player));
            // we will allow the screen to remain for openDelay ticks before closing
            closeCounter = (int) Math.max(0, openDelay.get());
            // continue to locate totem below
        }

        // If inventory is open (by user or auto-open), attempt to find a totem
        if (mc.currentScreen instanceof InventoryScreen) {
            int foundSlot = -1;

            // Search main inventory and hotbar (slot indices 0..35 are inventory in player inventory stacks)
            for (int invIndex = 0; invIndex < mc.player.getInventory().size(); invIndex++) {
                if (mc.player.getInventory().getStack(invIndex).getItem() == Items.TOTEM_OF_UNDYING) {
                    foundSlot = invIndex;
                    break;
                }
            }

            if (foundSlot != -1) {
                // Inform player which slot index contains a totem.
                // Note: client inventory slot indexing differs between UI and player container,
                // so we just present the player-inventory index to be clear.
                mc.inGameHud.getChatHud().addMessage(new LiteralText("[AutoCursorTotem] Totem found in inventory slot: " + foundSlot + " (player-inv index)."));
                mc.player.sendMessage(new LiteralText("Totem located at inventory index " + foundSlot + ". Manually move it to offhand (shift-click or drag)."), true);

                // start or reset close counter
                if (autoOpenInventory.get()) closeCounter = (int) Math.max(0, openDelay.get());
            } else {
                // no totem found
                mc.inGameHud.getChatHud().addMessage(new LiteralText("[AutoCursorTotem] No totem found in inventory."));
                // if we opened inventory automatically, keep it open for a short while so user can obtain a totem
                if (autoOpenInventory.get() && closeCounter <= 0) closeCounter = (int) Math.max(0, openDelay.get());
            }
        }

        // handle closing countdown (if we opened the inventory)
        if (closeCounter > 0) {
            closeCounter--;
            if (closeCounter <= 0 && autoOpenInventory.get() && mc.currentScreen instanceof InventoryScreen) {
                mc.setScreen(null); // close inventory
            }
        }
    }
}
