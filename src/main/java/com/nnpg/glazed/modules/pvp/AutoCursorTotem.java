package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
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

    private final Setting<Integer> hoverTime = sgGeneral.add(new IntSetting.Builder()
        .name("hover-time")
        .description("Ticks to hover over a totem before swapping it to offhand.")
        .defaultValue(3)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> closeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("close-delay")
        .description("Ticks to wait before closing inventory after swap.")
        .defaultValue(5)
        .min(0)
        .sliderMax(40)
        .build()
    );

    private int hoverTimer = 0;
    private int closeTimer = 0;
    private Slot targetSlot = null;
    private boolean swapping = false;

    private final Random random = new Random();

    public AutoCursorTotem() {
        super(GlazedAddon.pvp, "auto-cursor-totem", "Hovers cursor over a random totem then puts it into offhand instantly.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.interactionManager == null) return;

        // Already has totem in offhand -> reset state
        if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
            if (closeTimer > 0) {
                closeTimer--;
                if (closeTimer == 0 && mc.currentScreen instanceof InventoryScreen && autoOpenInventory.get()) {
                    mc.player.closeHandledScreen();
                }
            }
            swapping = false;
            targetSlot = null;
            hoverTimer = 0;
            return;
        }

        // Open inventory if needed
        if (autoOpenInventory.get() && !(mc.currentScreen instanceof InventoryScreen)) {
            mc.setScreen(new InventoryScreen(mc.player));
            return;
        }
        if (!(mc.currentScreen instanceof InventoryScreen)) return;

        if (!swapping) {
            // Collect totem slots
            List<Slot> totemSlots = new ArrayList<>();
            for (Slot slot : mc.player.currentScreenHandler.slots) {
                if (slot.getStack().getItem() == Items.TOTEM_OF_UNDYING && slot.id >= 9 && slot.id < 45) {
                    totemSlots.add(slot);
                }
            }
            if (totemSlots.isEmpty()) return;

            // Pick random slot and start hover
            targetSlot = totemSlots.get(random.nextInt(totemSlots.size()));
            swapping = true;
            hoverTimer = hoverTime.get();
        } else {
            if (hoverTimer > 0) {
                // Move the cursor visually over the target slot
                double x = mc.getWindow().getScaledWidth() / 2.0;
                double y = mc.getWindow().getScaledHeight() / 2.0;

                if (targetSlot != null) {
                    x = ((InventoryScreen) mc.currentScreen).x + targetSlot.x + 8;
                    y = ((InventoryScreen) mc.currentScreen).y + targetSlot.y + 8;
                }

                mc.mouse.setCursorPosition(x * mc.getWindow().getScaleFactor(), y * mc.getWindow().getScaleFactor());
                hoverTimer--;
                return;
            }

            // Instantly swap hovered totem to offhand
            if (targetSlot != null) {
                int offhandSlot = 45;
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, targetSlot.id, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, offhandSlot, 0, SlotActionType.PICKUP, mc.player);
            }

            // Reset + prepare to close
            closeTimer = closeDelay.get();
            swapping = false;
        }
    }
}
