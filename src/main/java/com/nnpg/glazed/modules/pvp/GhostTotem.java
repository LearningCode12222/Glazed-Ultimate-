package com.nnpg.glazed.modules.pvp;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import com.nnpg.glazed.GlazedAddon;

public class GhostTotem extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Boolean> showHud = sg.add(new BoolSetting.Builder()
        .name("show-hud")
        .description("Show the ghost totem HUD with inventory count.")
        .defaultValue(true)
        .build()
    );

    public GhostTotem() {
        super(GlazedAddon.pvp, "ghost-totem", "Show a small HUD with your totem count.");
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!showHud.get() || mc.player == null) return;

        int x = 6;
        int y = 6;

        int invTotems = 0;
        // Count totems in inventory
        for (ItemStack s : mc.player.getInventory().main) {
            if (s.getItem() == Items.TOTEM_OF_UNDYING) invTotems += s.getCount();
        }
        if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
            invTotems += mc.player.getOffHandStack().getCount();
        }

        MatrixStack matrices = event.matrices;
        mc.textRenderer.drawWithShadow(matrices, "Ghost Totem", x, y, 0xFFFFFF);
        mc.textRenderer.drawWithShadow(matrices, "Totems: " + invTotems, x, y + 10, 0xFFCC00);
    }
}
