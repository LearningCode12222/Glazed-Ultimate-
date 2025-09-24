package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
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

    private final Setting<Boolean> autoCharge = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-charge")
        .description("Automatically charges anchors with glowstone.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay between actions in ticks.")
        .defaultValue(3)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private int tickCounter = 0;

    public DoubleAnchor() {
        super(GlazedAddon.pvp, "double-anchor", "Handles double respawn anchor interactions.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (tickCounter > 0) {
            tickCounter--;
            return;
        }

        BlockPos targetPos = mc.player.getBlockPos().offset(mc.player.getHorizontalFacing());

        // Auto place respawn anchor
        if (autoPlace.get()) {
            if (mc.world.getBlockState(targetPos).isAir()) {
                int anchorSlot = InvUtils.findItemInHotbar(Items.RESPAWN_ANCHOR);
                if (anchorSlot != -1) {
                    int prevSlot = mc.player.getInventory().selectedSlot;
                    InvUtils.swap(anchorSlot, true);

                    placeBlock(targetPos);
                    InvUtils.swap(prevSlot, true);
                }
            }
        }

        // Auto charge with glowstone
        if (autoCharge.get()) {
            if (mc.world.getBlockState(targetPos).getBlock() == Items.RESPAWN_ANCHOR) {
                int glowSlot = InvUtils.findItemInHotbar(Items.GLOWSTONE);
                if (glowSlot != -1) {
                    int prevSlot = mc.player.getInventory().selectedSlot;
                    InvUtils.swap(glowSlot, true);

                    interactBlock(targetPos);
                    InvUtils.swap(prevSlot, true);
                }
            }
        }

        tickCounter = delay.get();
    }

    private void placeBlock(BlockPos pos) {
        Vec3d hitPos = Vec3d.ofCenter(pos);
        BlockHitResult bhr = new BlockHitResult(hitPos, Direction.UP, pos, false);
        Rotations.rotate(Rotations.getYaw(hitPos), Rotations.getPitch(hitPos), () -> {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
        });
    }

    private void interactBlock(BlockPos pos) {
        Vec3d hitPos = Vec3d.ofCenter(pos);
        BlockHitResult bhr = new BlockHitResult(hitPos, Direction.UP, pos, false);
        Rotations.rotate(Rotations.getYaw(hitPos), Rotations.getPitch(hitPos), () -> {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
        });
    }
}

