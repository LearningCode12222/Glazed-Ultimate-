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
    private boolean working = false;
    private int totemSlot = -1;

    public AutoCursorTotem() {
        super(GlazedAddon.pvp, "auto-cursor-totem", "Automatically moves a totem to your offhand using the cursor.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.interactionManager == null) return;

        // Step 0: Already has totem in offhand
        if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
            if (working && mc.currentScreen instanceof InventoryScreen) {
                mc.player.closeHandledScreen();
            }
            working = false;
            return;
        }

        // Tick delay
        if (delayCounter > 0) {
            delayCounter--;
            return;
        }

        // Step 1: Ensure inventory open
        if (!(mc.currentScreen instanceof InventoryScreen)) {
            mc.setScreen(new InventoryScreen(mc.player));
            working = true;
            delayCounter = clickDelay.get().intValue();
            return;
        }

        // Step 2: Find a random totem in handler slots
        if (totemSlot == -1) {
            totemSlot = findRandomTotemSlot();
            if (totemSlot == -1) {
                // no totems -> close inventory and stop
                mc.player.closeHandledScreen();
                working = false;
                return;
            }
        }

        // Step 3: Move totem to offhand (slot 45 in handler)
        int offhandSlot = 45;
        clickSlot(totemSlot);
        clickSlot(offhandSlot);

        // Step 4: Put leftovers back
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            clickSlot(totemSlot);
        }

        // Step 5: Close inventory
        mc.player.closeHandledScreen();
        working = false;
        totemSlot = -1;
        delayCounter = clickDelay.get().intValue();
    }

    private int findRandomTotemSlot() {
        List<Integer> slots = new ArrayList<>();
        // Handler slots 9–44 = inventory, 0–8 hotbar
        for (int i = 0; i < 45; i++) {
            ItemStack stack = mc.player.currentScreenHandler.getSlot(i).getStack();
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                slots.add(i);
            }
        }
        if (slots.isEmpty()) return -1;
        return slots.get(new Random().nextInt(slots.size()));
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
