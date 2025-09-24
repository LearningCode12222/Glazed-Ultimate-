package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.mixins.HandledScreenAccessor;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AutoCursorTotem extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> equipDelay = sgGeneral.add(new IntSetting.Builder()
        .name("equip-delay")
        .description("Delay in ticks before moving totem.")
        .defaultValue(2)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> inventoryStay = sgGeneral.add(new IntSetting.Builder()
        .name("inventory-stay")
        .description("How long the inventory stays open after equipping (ticks).")
        .defaultValue(5)
        .min(0)
        .sliderMax(40)
        .build()
    );

    private final Setting<Boolean> autoOpen = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-open-inventory")
        .description("Automatically opens inventory when needed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoClose = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-close-inventory")
        .description("Automatically closes inventory after equipping.")
        .defaultValue(true)
        .build()
    );

    private int delayCounter = 0;
    private int stayCounter = 0;
    private final Random random = new Random();

    public AutoCursorTotem() {
        super(GlazedAddon.pvp, "auto-cursor-totem", "Moves cursor over a totem and equips it to offhand.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.interactionManager == null) return;

        // Already has a totem in offhand -> nothing to do
        if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) return;

        // Auto-open inventory if needed
        if (!(mc.currentScreen instanceof InventoryScreen)) {
            if (autoOpen.get()) {
                mc.setScreen(new InventoryScreen(mc.player));
            }
            return;
        }

        if (delayCounter > 0) {
            delayCounter--;
            return;
        }

        // Collect all totem slots
        List<Integer> totemSlots = new ArrayList<>();
        for (int i = 9; i < 45; i++) {
            if (mc.player.getInventory().getStack(i - 9).getItem() == Items.TOTEM_OF_UNDYING) {
                totemSlots.add(i);
            }
        }

        if (totemSlots.isEmpty()) {
            return; // no totems found
        }

        // Pick random slot
        int chosenSlot = totemSlots.get(random.nextInt(totemSlots.size()));

        if (mc.currentScreen instanceof InventoryScreen invScreen) {
            Slot slot = ((HandledScreenAccessor) invScreen).getScreenHandler().getSlot(chosenSlot);
            if (slot != null) {
                // Move cursor visually (just sets focusedSlot)
                ((HandledScreenAccessor) invScreen).setFocusedSlot(slot);

                // Instantly equip to offhand
                int offhandSlot = 45;
                mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    chosenSlot,
                    0,
                    SlotActionType.PICKUP,
                    mc.player
                );
                mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    offhandSlot,
                    0,
                    SlotActionType.PICKUP,
                    mc.player
                );

                delayCounter = equipDelay.get();
                stayCounter = inventoryStay.get();
            }
        }

        // Auto-close inventory after delay
        if (autoClose.get() && stayCounter > 0) {
            stayCounter--;
            if (stayCounter == 0) {
                mc.setScreen((Screen) null);
            }
        }
    }
}
