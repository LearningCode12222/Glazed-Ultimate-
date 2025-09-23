package com.nnpg.glazed.modules.pvp;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import com.nnpg.glazed.GlazedAddon;

public class GhostTotem extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> showTotem = sgGeneral.add(new BoolSetting.Builder()
        .name("show-totem")
        .description("Visually shows a totem even if you're holding something else.")
        .defaultValue(true)
        .build()
    );

    public GhostTotem() {
        super(GlazedAddon.pvp, "ghost-totem", "Spoofs your hand to show a totem while actually holding your sword/crystal.");
    }

    @Override
    public void onRender() {
        if (mc.player == null || !showTotem.get()) return;

        // Force client-side visual of holding totem
        mc.player.getInventory().main.set(mc.player.getInventory().selectedSlot, new ItemStack(Items.TOTEM_OF_UNDYING));
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null) mc.player.getInventory().updateItems(); // Reset visuals
    }
}
