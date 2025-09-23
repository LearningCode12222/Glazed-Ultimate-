package com.nnpg.glazed.modules.pvp;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;

import com.nnpg.glazed.GlazedAddon;

public class GhostTotem extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> showHud = sgGeneral.add(new BoolSetting.Builder()
        .name("show-hud")
        .description("Displays Ghost Totem info on screen.")
        .defaultValue(true)
        .build()
    );

    public GhostTotem() {
        super(GlazedAddon.pvp, "ghost-totem", "Shows a fake totem in hand but really uses sword/crystal.");
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!showHud.get() || mc.player == null) return;

        int x = 5;
        int y = 50;

        // Count totems in inventory
        int invTotems = 0;
        for (ItemStack stack : mc.player.getInventory().main) {
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                invTotems += stack.getCount();
            }
        }

        // 🚀 In newer Meteor, drawWithShadow uses: drawWithShadow(DrawContext, String, x, y, color)
        event.drawContext.drawTextWithShadow(mc.textRenderer, "Ghost Totem", x, y, 0xFFFFFF);
        event.drawContext.drawTextWithShadow(mc.textRenderer, "Totems: " + invTotems, x, y + 10, 0xFFCC00);
    }
}
