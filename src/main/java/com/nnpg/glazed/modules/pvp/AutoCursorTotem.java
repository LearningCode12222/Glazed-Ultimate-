package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AutoCursorTotem extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> autoOpenInventory = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-open-inventory")
        .description("Automatically opens inventory if not already open.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoCloseInventory = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-close-inventory")
        .description("Closes inventory once swap is complete.")
        .defaultValue(true)
        .build()
    );

    private boolean swapping = false;

    public AutoCursorTotem() {
        super(GlazedAddon.pvp, "auto-cursor-totem", "Moves a totem into your offhand using the cursor, like pressing F.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.interactionManager == null) return;

        // Already holding a totem in offhand, do nothing
        if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
            if (swapping && autoCloseInventory.get() && mc.currentScreen instanceof InventoryScreen) {
                mc.player.closeHandledScreen();
            }
            swapping = false;
            return;
        }

        // If no totem in offhand, begin process
        if (autoOpenInventory.get() && !(mc.currentScreen instanceof InventoryScreen)) {
            mc.setScreen(new InventoryScreen(mc.player));
            return;
        }

        if (!(mc.currentScreen instanceof InventoryScreen)) return;

        // Collect all totem slots
        List<Integer> totemSlots = new ArrayList<>();
        for (int i = 9; i < 45; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i - 9);
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                totemSlots.add(i);
            }
        }

        if (totemSlots.isEmpty()) return;

        // Pick a random totem slot
        int totemSlot = totemSlots.get(new Random().nextInt(totemSlots.size()));

        // Offhand slot index in screen handler
        int offhandSlot = 45;

        // Pick up totem with cursor
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, totemSlot, 0, SlotActionType.PICKUP, mc.player);

        // Place into offhand instantly
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, offhandSlot, 0, SlotActionType.PICKUP, mc.player);

        swapping = true;
    }
}
