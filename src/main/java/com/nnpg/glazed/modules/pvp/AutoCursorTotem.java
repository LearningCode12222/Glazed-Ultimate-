package com.nnpg.glazed.modules.main;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

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

    private final Setting<Boolean> autoOpenInventory = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-open-inventory")
        .description("Automatically opens inventory if not already open.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> manualMode = sgGeneral.add(new BoolSetting.Builder()
        .name("manual-mode")
        .description("Only works if you manually open inventory.")
        .defaultValue(false)
        .build()
    );

    private int delayCounter = 0;

    public AutoCursorTotem() {
        super(GlazedAddon.pvp, "auto-cursor-totem", "Automatically moves a totem to your offhand using the cursor.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.interactionManager == null) return;

        // Auto open inventory if enabled
        if (autoOpenInventory.get() && !(mc.currentScreen instanceof InventoryScreen)) {
            mc.setScreen(new InventoryScreen(mc.player));
            return;
        }

        if (manualMode.get() && !(mc.currentScreen instanceof InventoryScreen)) return;
        if (!(mc.currentScreen instanceof InventoryScreen)) return;

        if (delayCounter > 0) {
            delayCounter--;
            return;
        }

        int totemSlot = -1;

        // Find a totem in inventory (slots 9–44 are inventory)
        for (int i = 9; i < 45; i++) {
            if (mc.player.getInventory().getStack(i - 9).getItem() == Items.TOTEM_OF_UNDYING) {
                totemSlot = i;
                break;
            }
        }

        if (totemSlot != -1) {
            int offhandSlot = 45; // offhand slot index
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, totemSlot, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, offhandSlot, 0, SlotActionType.PICKUP, mc.player);
            delayCounter = clickDelay.get().intValue();
        }
    }
}
