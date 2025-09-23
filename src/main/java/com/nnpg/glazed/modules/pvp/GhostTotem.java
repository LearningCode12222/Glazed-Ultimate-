package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.client.render.item.HeldItemRenderer;

public class GhostTotem extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Boolean> onlyWhenSword = sg.add(new BoolSetting.Builder()
        .name("only-when-sword")
        .description("Only show the ghost totem when you're holding a sword in main hand.")
        .defaultValue(true).build()
    );

    public GhostTotem() {
        super(GlazedAddon.pvp, "ghost-totem", "Client-only HUD: shows a totem icon/text while you actually hold a sword.");
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null) return;

        ItemStack main = mc.player.getMainHandStack();
        boolean isSword = main.getItem() == Items.NETHERITE_SWORD ||
                          main.getItem() == Items.DIAMOND_SWORD ||
                          main.getItem() == Items.IRON_SWORD ||
                          main.getItem() == Items.STONE_SWORD ||
                          main.getItem() == Items.GOLDEN_SWORD ||
                          main.getItem() == Items.WOODEN_SWORD;

        if (onlyWhenSword.get() && !isSword) return;

        MatrixStack matrices = event.matrices;
        int x = mc.getWindow().getScaledWidth() / 2 + 10;
        int y = mc.getWindow().getScaledHeight() - 30;

        // Draw a small text + count of totems you actually have in inventory (client-side)
        mc.textRenderer.drawWithShadow(matrices, "Ghost Totem", x, y - 12, 0xFFFFFF);
        int invTotems = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() == Items.TOTEM_OF_UNDYING) invTotems += s.getCount();
        }
        mc.textRenderer.drawWithShadow(matrices, "Totems: " + invTotems, x, y, 0xFFCC00);
    }
}
