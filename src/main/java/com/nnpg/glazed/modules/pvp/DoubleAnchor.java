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

    private final Setting<Boolean> autoPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-place")
        .description("Automatically places anchors when enabled.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoExplode = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-explode")
        .description("Automatically charges + explodes anchors with glowstone.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("cycle-delay")
        .description("Delay between full anchor explosions (ticks).")
        .defaultValue(0)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private int tickCounter = 0;

    public DoubleAnchor() {
        super(GlazedAddon.pvp, "double-anchor", "Spams respawn anchors by instantly placing and exploding them.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (tickCounter > 0) {
            tickCounter--;
            return;
        }

        BlockPos targetPos = mc.player.getBlockPos().offset(mc.player.getHorizontalFacing());

        // Place anchor if air
        if (autoPlace.get() && mc.world.getBlockState(targetPos).isAir()) {
            var anchor = InvUtils.findInHotbar(Items.RESPAWN_ANCHOR);
            if (anchor.found()) {
                int prevSlot = mc.player.getInventory().selectedSlot;
                InvUtils.swap(anchor.slot(), true);
                placeBlock(targetPos);
                InvUtils.swap(prevSlot, true);
            }
        }

        // Charge + explode instantly
        if (autoExplode.get() && mc.world.getBlockState(targetPos).getBlock() == Blocks.RESPAWN_ANCHOR) {
            var glow = InvUtils.findInHotbar(Items.GLOWSTONE);
            if (glow.found()) {
                int prevSlot = mc.player.getInventory().selectedSlot;
                InvUtils.swap(glow.slot(), true);
                interactBlock(targetPos); // charges + triggers explosion instantly
                InvUtils.swap(prevSlot, true);
                tickCounter = delay.get(); // cycle reset
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
