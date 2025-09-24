package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

import java.util.List;
import java.util.Random;

public class AutoCursorTotem extends Module {
    private final Random random = new Random();
    private long lastAction = 0;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final IntSetting delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay-ms")
        .description("Delay between actions in ms.")
        .defaultValue(150)
        .min(50)
        .sliderMax(1000)
        .build()
    );

    private final BoolSetting autoOpen = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-open-inventory")
        .description("Automatically opens the inventory.")
        .defaultValue(true)
        .build()
    );

    private final BoolSetting autoMode = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-mode")
        .description("Automatically moves totems when offhand is empty.")
        .defaultValue(true)
        .build()
    );

    public AutoCursorTotem() {
        super(Categories.Combat, GlazedAddon.NAME, "AutoCursor Totem", "Automatically equips totems into offhand.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.interactionManager == null) return;

        // Check delay
        if (System.currentTimeMillis() - lastAction < delay.get()) return;

        // Skip if already holding a totem in offhand
        if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) return;

        if (autoMode.get()) {
            int totemSlot = findTotemSlot();
            if (totemSlot != -1) {
                moveTotemToOffhand(totemSlot);
                lastAction = System.currentTimeMillis();
            }
        }
    }

    private int findTotemSlot() {
        for (int i = 9; i < 36; i++) { // main inventory only
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) return i;
        }
        return -1;
    }

    private void moveTotemToOffhand(int slot) {
        if (autoOpen.get() && !(mc.currentScreen instanceof InventoryScreen)) {
            mc.setScreen(new InventoryScreen(mc.player));
        }

        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,
            slot,
            0,
            SlotActionType.PICKUP,
            mc.player
        );

        // Offhand is slot 45
        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,
            45,
            0,
            SlotActionType.PICKUP,
            mc.player
        );
    }
}
