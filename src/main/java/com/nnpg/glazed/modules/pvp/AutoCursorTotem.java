package com.nnpg.glazed.modules.main;

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

    private final Setting<Double> clickDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("click-delay")
        .description("Delay in ticks between cursor clicks.")
        .defaultValue(2.0)
        .min(0.0)
        .sliderMax(20.0)
        .build()
    );

    private int delayCounter = 0;
    private boolean working = false; // true while moving items
    private int totemSlot = -1;

    public AutoCursorTotem() {
        super(GlazedAddon.pvp, "auto-cursor-totem", "Automatically moves a totem to your offhand using the cursor.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.interactionManager == null) return;

        // Already has a totem in offhand
        ItemStack offhand = mc.player.getOffHandStack();
        if (offhand.getItem() == Items.TOTEM_OF_UNDYING) {
            if (mc.currentScreen instanceof InventoryScreen && working) {
                mc.player.closeHandledScreen(); // close inventory after success
            }
            working = false;
            return;
        }

        // Delay timer
        if (delayCounter > 0) {
            delayCounter--;
            return;
        }

        // Step 1: Open inventory if not already
        if (!(mc.currentScreen instanceof InventoryScreen)) {
            mc.setScreen(new InventoryScreen(mc.player));
            working = true;
            delayCounter = clickDelay.get().intValue();
            return;
        }

        // Step 2: If inventory open, find a totem
        if (totemSlot == -1) {
            totemSlot = findRandomTotem();
            if (totemSlot == -1) {
                // No totem found -> close and stop
                mc.player.closeHandledScreen();
                working = false;
                return;
            }
        }

        // Step 3: Move totem into offhand
        int offhandSlot = 45;
        clickSlot(totemSlot); // pick up totem
        clickSlot(offhandSlot); // put in offhand

        // Step 4: If still holding something, put it back
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            clickSlot(totemSlot);
        }

        // Step 5: Close inventory after operation
        mc.player.closeHandledScreen();
        working = false;
        totemSlot = -1;
        delayCounter = clickDelay.get().intValue();
    }

    private int findRandomTotem() {
        List<Integer> slots = new ArrayList<>();
        for (int i = 9; i < 45; i++) {
            if (mc.player.getInventory().getStack(i - 9).getItem() == Items.TOTEM_OF_UNDYING) {
                slots.add(i);
            }
        }
        if (slots.isEmpty()) return -1;
        return slots.get(new Random().nextInt(slots.size())); // pick random
    }

    private void clickSlot(int slot) {
        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,
            slot,
            0,
            SlotActionType.PICKUP,
            mc.player
        );
    }
}
