package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.*;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.*;

public class TunnelBaseFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDetect = settings.createGroup("Detection");
    private final SettingGroup sgRender = settings.createGroup("ESP");

    // General
    private final Setting<Boolean> autoWalkMine = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-walk-mine")
        .description("Automatically walk forward and mine when underground (Y between -64 and 0).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> rotationSpeed = sgGeneral.add(new IntSetting.Builder()
        .name("rotation-speed")
        .description("How fast yaw turns per tick (degrees).")
        .defaultValue(5)
        .min(1)
        .sliderMax(20)
        .build()
    );

    // Detection
    private final Setting<Boolean> detectRotatedDeepslate = sgDetect.add(new BoolSetting.Builder()
        .name("detect-rotated-deepslate")
        .description("Detect rotated deepslate blocks and mine towards them (cover-up trails).")
        .defaultValue(true)
        .build()
    );

    // ESP colors
    private final Setting<SettingColor> chestColor = sgRender.add(new ColorSetting.Builder().name("chest-color").defaultValue(new SettingColor(255, 165, 0, 80)).build());

    // State
    private final Map<BlockPos, SettingColor> detectedBlocks = new HashMap<>();
    private BlockPos targetRotatedDeepslate = null;
    private boolean miningRotatedDeepslate = false;

    public TunnelBaseFinder() {
        super(GlazedAddon.CATEGORY, "TunnelBaseFinder", "Finds tunnel bases with ESP, hazard safety, and rotated deepslate tracking.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // Handle rotated deepslate mining
        if (detectRotatedDeepslate.get()) handleRotatedDeepslate();
    }

    // ✅ Rotated deepslate detection
    private boolean isRotatedDeepslate(BlockState state) {
        return state.getBlock() == Blocks.DEEPSLATE && state.getProperties().size() > 0;
    }

    // ✅ Path-mining logic for rotated deepslate
    private void handleRotatedDeepslate() {
        if (!detectRotatedDeepslate.get()) return;

        if (miningRotatedDeepslate && targetRotatedDeepslate != null) {
            BlockState state = mc.world.getBlockState(targetRotatedDeepslate);
            if (!isRotatedDeepslate(state)) {
                miningRotatedDeepslate = false;
                targetRotatedDeepslate = null;
                return;
            }

            rotateAndMine(targetRotatedDeepslate, state);
            return;
        }

        // Find nearest rotated deepslate
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos closest = null;
        double bestDist = Double.MAX_VALUE;

        for (BlockPos pos : detectedBlocks.keySet()) {
            BlockState st = mc.world.getBlockState(pos);
            if (isRotatedDeepslate(st)) {
                double dist = playerPos.getSquaredDistance(pos);
                if (dist < bestDist && dist < 100) {
                    bestDist = dist;
                    closest = pos;
                }
            }
        }

        if (closest != null) {
            targetRotatedDeepslate = closest;
            miningRotatedDeepslate = true;
        }
    }

    // ✅ Rotation + mining logic
    private void rotateAndMine(BlockPos pos, BlockState state) {
        Vec3d hitVec = Vec3d.ofCenter(pos);
        double dx = hitVec.x - mc.player.getX();
        double dz = hitVec.z - mc.player.getZ();
        double dy = hitVec.y - (mc.player.getY() + mc.player.getStandingEyeHeight());

        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90F);
        float pitch = (float) -(Math.toDegrees(Math.atan2(dy, dist)));

        // Smooth rotate toward block
        mc.player.setYaw(approachAngle(mc.player.getYaw(), yaw, rotationSpeed.get()));
        mc.player.setPitch(approachAngle(mc.player.getPitch(), pitch, rotationSpeed.get()));

        // Swing + mine
        mc.interactionManager.updateBlockBreakingProgress(pos, Direction.UP);
        mc.player.swingHand(Hand.MAIN_HAND);

        // ✅ Follow placement axis
        if (state.contains(net.minecraft.state.property.Properties.AXIS)) {
            Direction.Axis axis = state.get(net.minecraft.state.property.Properties.AXIS);
            BlockPos next = pos.offset(Direction.from(axis, 1));
            if (mc.world.getBlockState(next).getBlock() == Blocks.DEEPSLATE) {
                targetRotatedDeepslate = next;
            }
        }
    }

    // Helpers
    private float approachAngle(float current, float target, float step) {
        float delta = wrapAngleTo180(target - current);
        if (delta > step) delta = step;
        if (delta < -step) delta = -step;
        return current + delta;
    }

    private float wrapAngleTo180(float angle) {
        angle %= 360.0F;
        if (angle >= 180.0F) angle -= 360.0F;
        if (angle < -180.0F) angle += 360.0F;
        return angle;
    }

    // ESP + detection system stays unchanged
    private void notifyFound() {
        detectedBlocks.clear();
        int viewDist = mc.options.getViewDistance().getValue();
        BlockPos playerPos = mc.player.getBlockPos();

        for (int dx = -viewDist; dx <= viewDist; dx++) {
            for (int dz = -viewDist; dz <= viewDist; dz++) {
                WorldChunk chunk = mc.world.getChunkManager().getChunk(
                    (playerPos.getX() >> 4) + dx,
                    (playerPos.getZ() >> 4) + dz,
                    ChunkStatus.FULL, false
                );
                if (chunk == null) continue;

                if (detectRotatedDeepslate.get()) {
                    for (BlockPos pos : BlockPos.iterate(
                        chunk.getPos().getStartX(), mc.world.getBottomY(), chunk.getPos().getStartZ(),
                        chunk.getPos().getEndX(), mc.world.getTopY(), chunk.getPos().getEndZ())) {

                        BlockState state = mc.world.getBlockState(pos);
                        if (isRotatedDeepslate(state)) {
                            detectedBlocks.put(pos, new SettingColor(100, 100, 100, 120));
                        }
                    }
                }
            }
        }
    }
}
