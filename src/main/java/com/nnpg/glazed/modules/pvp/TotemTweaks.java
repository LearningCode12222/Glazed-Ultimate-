package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import meteordevelopment.meteorclient.utils.render.color.Color;

import java.util.*;

public class TotemTweaks extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgHud = settings.createGroup("HUD");

    // Delay settings
    private final Setting<Integer> minDelay = sgGeneral.add(new IntSetting.Builder()
        .name("min-delay")
        .description("Minimum delay in ticks between actions.")
        .defaultValue(2)
        .min(1)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> maxDelay = sgGeneral.add(new IntSetting.Builder()
        .name("max-delay")
        .description("Maximum delay in ticks between actions.")
        .defaultValue(5)
        .min(1)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> autoOpenInventory = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-open-inventory")
        .description("Automatically opens inventory if not already open.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoCloseInventory = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-close-inventory")
        .description("Automatically closes inventory after equipping a totem.")
        .defaultValue(true)
        .build()
    );

    // HUD Settings
    private final Setting<Double> hudScale = sgHud.add(new DoubleSetting.Builder()
        .name("hud-scale")
        .description("Scale of the HUD text.")
        .defaultValue(1.5)
        .min(0.5)
        .sliderMax(3.0)
        .build()
    );

    private final Setting<String> hudColor = sgHud.add(new StringSetting.Builder()
        .name("hud-color")
        .description("Color of the HUD text (hex).")
        .defaultValue("#FFD700") // gold
        .build()
    );

    private int delayCounter = 0;
    private int currentDelay = 0;
    private final Random random = new Random();

    // Track popped totems (if PopTotemEvent exists in your version, re-enable it)
    private final Map<String, Integer> poppedMap = new HashMap<>();

    public TotemTweaks() {
        super(GlazedAddon.pvp, "totem-tweaks", "Enhances totem handling with HUD, pop tracking, and randomization.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.interactionManager == null) return;

        // Already has totem in offhand
        if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) return;

        // Auto open inventory
        if (autoOpenInventory.get() && !(mc.currentScreen instanceof InventoryScreen)) {
            mc.setScreen(new InventoryScreen(mc.player));
            return;
        }

        if (!(mc.currentScreen instanceof InventoryScreen)) return;

        if (delayCounter > 0) {
            delayCounter--;
            return;
        }

        List<Integer> totemSlots = new ArrayList<>();

        // Collect all totem slots
        for (int i = 9; i < 45; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i - 9);
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                totemSlots.add(i);
            }
        }

        if (!totemSlots.isEmpty()) {
            // Pick random totem slot
            int totemSlot = totemSlots.get(random.nextInt(totemSlots.size()));
            int offhandSlot = 45;

            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, totemSlot, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, offhandSlot, 0, SlotActionType.PICKUP, mc.player);

            if (autoCloseInventory.get()) mc.player.closeHandledScreen();

            mc.inGameHud.getChatHud().addMessage(Text.literal("[TotemTweaks] Equipped a random totem."));

            currentDelay = random.nextInt(maxDelay.get() - minDelay.get() + 1) + minDelay.get();
            delayCounter = currentDelay;
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        int count = countTotems();
        String text = "Totems: " + count;

        // Draw totem count
        TextRenderer.get().begin(hudScale.get(), false, true);
        TextRenderer.get().render(text, 5, 30, parseColor(hudColor.get()));
        TextRenderer.get().end();

        // Draw popped totem info
        int y = 50;
        for (Map.Entry<String, Integer> entry : poppedMap.entrySet()) {
            String line = entry.getKey() + ": " + entry.getValue();
            TextRenderer.get().begin(hudScale.get(), false, true);
            TextRenderer.get().render(line, 5, y, new Color(255, 85, 85)); // red
            TextRenderer.get().end();
            y += 12;
        }
    }

    private int countTotems() {
        int total = 0;
        for (ItemStack stack : mc.player.getInventory().main) {
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) total += stack.getCount();
        }
        return total + (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING ? 1 : 0);
    }

    private Color parseColor(String hex) {
        try {
            int rgb = Integer.parseInt(hex.replace("#", ""), 16);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            return new Color(r, g, b);
        } catch (Exception e) {
            return new Color(255, 255, 255); // fallback white
        }
    }
}
