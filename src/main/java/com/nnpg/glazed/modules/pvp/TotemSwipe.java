package com.nnpg.glazed.modules.pvp;

import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

import com.nnpg.glazed.GlazedAddon;

public class TotemSwipe extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> onlyWithTotem = sgGeneral.add(new BoolSetting.Builder()
        .name("only-with-totem")
        .description("Only triggers when holding a totem in hand.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> swordSlot = sgGeneral.add(new IntSetting.Builder()
        .name("sword-slot")
        .description("Hotbar slot index (0-8) where your sword is located.")
        .defaultValue(0)
        .range(0, 8)
        .build()
    );

    public TotemSwipe() {
        super(GlazedAddon.pvp, "totem-swipe", "Makes it look like you attack with a totem by quickly swapping to a sword and back.");
    }

    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        ItemStack mainHand = mc.player.getMainHandStack();

        // Only trigger if holding totem, if enabled
        if (onlyWithTotem.get() && mainHand.getItem() != Items.TOTEM_OF_UNDYING) return;

        int originalSlot = mc.player.getInventory().selectedSlot;

        // Switch to sword slot
        mc.player.getInventory().selectedSlot = swordSlot.get();

        // Attack with sword
        mc.interactionManager.attackEntity(mc.player, event.entity);
        mc.player.swingHand(Hand.MAIN_HAND);

        // Switch back instantly to original slot (totem)
        mc.player.getInventory().selectedSlot = originalSlot;
    }
}
