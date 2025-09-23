package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;

/**
 * Ghost Totem:
 * Visually shows that you're holding a totem while actually holding a sword or crystal.
 */
public class GhostTotem extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> preferSword = sgGeneral.add(new BoolSetting.Builder()
        .name("prefer-sword")
        .description("Prefer switching to a sword instead of crystal when faking totem.")
        .defaultValue(true)
        .build()
    );

    private int fakeSlot = -1;
    private int realSlot = -1;

    public GhostTotem() {
        super(GlazedAddon.pvp, "ghost-totem", "Shows a totem in hand while actually using sword or crystal.");
    }

    @Override
    public void onActivate() {
        // Nothing special, will be handled in tick.
        fakeSlot = -1;
        realSlot = -1;
    }

    @Override
    public void onDeactivate() {
        // Reset back to player’s original slot
        if (realSlot != -1) {
            mc.player.getInventory().selectedSlot = realSlot;
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(realSlot));
        }
        fakeSlot = -1;
        realSlot = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        // Find totem slot
        int totemSlot = findItemSlot(Items.TOTEM_OF_UNDYING);
        // Find sword/crystal slot
        int swordSlot = findItemSlot(Items.NETHERITE_SWORD);
        int crystalSlot = findItemSlot(Items.END_CRYSTAL);

        if (totemSlot == -1) return; // no totem = nothing to spoof

        // Pick real slot
        realSlot = preferSword.get() && swordSlot != -1 ? swordSlot : crystalSlot;

        if (realSlot == -1) return; // no weapon found

        // Fake being on totem slot (visually)
        if (mc.player.getInventory().selectedSlot != totemSlot) {
            fakeSlot = totemSlot;
            mc.player.getInventory().selectedSlot = fakeSlot;
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(fakeSlot));
        }

        // But internally, keep real slot recorded so attacks work
        // We switch to real slot only right before an attack (see AttackEntityEvent hook)
    }

    private int findItemSlot(net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == item) return i;
        }
        return -1;
    }
}
