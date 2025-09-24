package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class DoubleAnchor extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> cycleDelay = sgGeneral.add(new IntSetting.Builder()
        .name("cycle-delay")
        .description("Delay in ticks between double anchor cycles.")
        .defaultValue(0)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> totemSlot = sgGeneral.add(new IntSetting.Builder()
        .name("totem-slot")
        .description("Hotbar slot to swap back to after anchoring.")
        .defaultValue(1)
        .min(1)
        .sliderMax(9)
        .build()
    );

    private int step = 0;
    private int delayCounter = 0;

    public DoubleAnchor() {
        super(GlazedAddon.pvp, "double-anchor", "Automatically places + explodes 2 respawn anchors quickly.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (delayCounter > 0) {
            delayCounter--;
            return;
        }

        BlockPos targetPos = mc.player.getBlockPos().offset(mc.player.getHorizontalFacing());
        if (mc.crosshairTarget == null) return;

        switch (step) {
            case 0 -> {
                // swap to anchor
                var anchor = InvUtils.findInHotbar(Items.RESPAWN_ANCHOR);
                if (!anchor.found()) return;
                InvUtils.swap(anchor.slot(), true);
                step++;
            }
            case 1 -> {
                // place anchor
                if (mc.world.getBlockState(targetPos).isAir()) placeBlock(targetPos);
                step++;
            }
            case 2 -> {
                // swap to glowstone
                var glow = InvUtils.findInHotbar(Items.GLOWSTONE);
                if (!glow.found()) return;
                InvUtils.swap(glow.slot(), true);
                step++;
            }
            case 3 -> {
                // charge + explode
                if (mc.world.getBlockState(targetPos).getBlock() == Blocks.RESPAWN_ANCHOR) {
                    interactBlock(targetPos);
                }
                step++;
            }
            case 4 -> {
                // swap to 2nd anchor
                var anchor = InvUtils.findInHotbar(Items.RESPAWN_ANCHOR);
                if (!anchor.found()) return;
                InvUtils.swap(anchor.slot(), true);
                step++;
            }
            case 5 -> {
                // place 2nd anchor
                if (mc.world.getBlockState(targetPos).isAir()) placeBlock(targetPos);
                step++;
            }
            case 6 -> {
                // swap glowstone again
                var glow = InvUtils.findInHotbar(Items.GLOWSTONE);
                if (!glow.found()) return;
                InvUtils.swap(glow.slot(), true);
                step++;
            }
            case 7 -> {
                // charge + explode second anchor
                if (mc.world.getBlockState(targetPos).getBlock() == Blocks.RESPAWN_ANCHOR) {
                    interactBlock(targetPos);
                }
                step++;
            }
            case 8 -> {
                // swap back to totem slot
                int slot = totemSlot.get() - 1;
                if (slot >= 0 && slot < 9) InvUtils.swap(slot, true);
                step++;
            }
            case 9 -> {
                // reset cycle
                step = 0;
                delayCounter = cycleDelay.get();
            }
        }
    }

    private void placeBlock(BlockPos pos) {
        Vec3d hitPos = Vec3d.ofCenter(pos);
        BlockHitResult bhr = new BlockHitResult(hitPos, Direction.UP, pos, false);
        Rotations.rotate(Rotations.getYaw(hitPos), Rotations.getPitch(hitPos), () ->
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr)
        );
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void interactBlock(BlockPos pos) {
        Vec3d hitPos = Vec3d.ofCenter(pos);
        BlockHitResult bhr = new BlockHitResult(hitPos, Direction.UP, pos, false);
        Rotations.rotate(Rotations.getYaw(hitPos), Rotations.getPitch(hitPos), () ->
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr)
        );
        mc.player.swingHand(Hand.MAIN_HAND);
    }
}
