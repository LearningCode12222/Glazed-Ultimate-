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
    private boolean openedInventoryThisCycle = false;

    public AutoCursorTotem() {
        super(GlazedAddon.pvp, "auto-cursor-totem", "Automatically moves a totem to your offhand using the cursor.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.interactionManager == null) return;

        // Already has a totem in offhand -> do nothing
        ItemStack offhand = mc.player.getOffHandStack();
        if (offhand.getItem() == Items.TOTEM_OF_UNDYING) {
            openedInventoryThisCycle = false;
            return;
        }

        // Handle auto/manual inventory opening
        if (!(mc.currentScreen instanceof InventoryScreen)) {
            if (manualMode.get()) return;
            if (autoOpenInventory.get() && !openedInventoryThisCycle) {
                mc.setScreen(new InventoryScreen(mc.player));
                openedInventoryThisCycle = true;
            }
            return;
        }

        // Delay between clicks
        if (delayCounter > 0) {
            delayCounter--;
            return;
        }

        int totemSlot = findTotem();
        if (totemSlot == -1) return;

        int offhandSlot = 45; // offhand index

        // Pick up totem
        clickSlot(totemSlot);

        // Place into offhand
        clickSlot(offhandSlot);

        // Put back leftovers if still holding something
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            clickSlot(totemSlot);
        }

        delayCounter = clickDelay.get().intValue();
    }

    private int findTotem() {
        // Slots 9–44 map to inventory (0–35)
        for (int i = 9; i < 45; i++) {
            if (mc.player.getInventory().getStack(i - 9).getItem() == Items.TOTEM_OF_UNDYING) {
                return i;
            }
        }
        return -1;
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
