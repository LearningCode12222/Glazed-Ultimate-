package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.*;
import net.minecraft.client.option.GameOptions;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
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
    private final Setting<Boolean> discordNotification = sgGeneral.add(new BoolSetting.Builder()
        .name("discord-notification")
        .description("Send notification to Discord (requires webhook system).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoWalkMine = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-walk-mine")
        .description("Automatically walk forward and mine when underground (Y between -64 and 0).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> detourLength = sgGeneral.add(new IntSetting.Builder()
        .name("detour-length")
        .description("How many blocks to dig during hazard detour.")
        .defaultValue(30)
        .min(5)
        .sliderMax(100)
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
    private final Setting<Integer> baseThreshold = sgDetect.add(new IntSetting.Builder()
        .name("base-threshold")
        .description("How many selected blocks before base is detected.")
        .defaultValue(50)
        .min(1)
        .sliderMax(500)
        .build()
    );

    private final Setting<Boolean> detectChests = sgDetect.add(new BoolSetting.Builder().name("detect-chests").defaultValue(true).build());
    private final Setting<Boolean> detectShulkers = sgDetect.add(new BoolSetting.Builder().name("detect-shulkers").defaultValue(true).build());
    private final Setting<Boolean> detectBarrels = sgDetect.add(new BoolSetting.Builder().name("detect-barrels").defaultValue(true).build());
    private final Setting<Boolean> detectSpawners = sgDetect.add(new BoolSetting.Builder().name("detect-spawners").defaultValue(true).build());
    private final Setting<Boolean> detectFurnaces = sgDetect.add(new BoolSetting.Builder().name("detect-furnaces").defaultValue(false).build());
    private final Setting<Boolean> detectRedstone = sgDetect.add(new BoolSetting.Builder().name("detect-redstone").defaultValue(false).build());
    private final Setting<Boolean> detectRotatedDeepslate = sgDetect.add(new BoolSetting.Builder().name("detect-rotated-deepslate").description("Detect rotated deepslate blocks and move towards them").defaultValue(false).build());

    // ESP colors
    private final Setting<SettingColor> chestColor = sgRender.add(new ColorSetting.Builder().name("chest-color").defaultValue(new SettingColor(255, 165, 0, 80)).build());
    private final Setting<SettingColor> shulkerColor = sgRender.add(new ColorSetting.Builder().name("shulker-color").defaultValue(new SettingColor(255, 0, 255, 80)).build());
    private final Setting<SettingColor> barrelColor = sgRender.add(new ColorSetting.Builder().name("barrel-color").defaultValue(new SettingColor(139, 69, 19, 80)).build());
    private final Setting<SettingColor> spawnerColor = sgRender.add(new ColorSetting.Builder().name("spawner-color").defaultValue(new SettingColor(0, 0, 255, 80)).build());
    private final Setting<SettingColor> furnaceColor = sgRender.add(new ColorSetting.Builder().name("furnace-color").defaultValue(new SettingColor(128, 128, 128, 80)).build());
    private final Setting<SettingColor> redstoneColor = sgRender.add(new ColorSetting.Builder().name("redstone-color").defaultValue(new SettingColor(255, 0, 0, 80)).build());
    private final Setting<SettingColor> pistonColor = sgRender.add(new ColorSetting.Builder().name("piston-color").defaultValue(new SettingColor(200, 200, 200, 80)).build());
    private final Setting<SettingColor> observerColor = sgRender.add(new ColorSetting.Builder().name("observer-color").defaultValue(new SettingColor(100, 100, 100, 80)).build());
    private final Setting<SettingColor> deepslateColor = sgRender.add(new ColorSetting.Builder().name("deepslate-color").defaultValue(new SettingColor(60, 60, 80, 120)).build());
    private final Setting<Boolean> espOutline = sgRender.add(new BoolSetting.Builder().name("esp-outline").defaultValue(true).build());

    // State
    private FacingDirection currentDirection;
    private FacingDirection savedDirection;
    private boolean avoidingHazard = false;
    private int detourBlocksRemaining = 0;
    private float targetYaw;
    private int rotationCooldownTicks = 0;
    private final Map<BlockPos, SettingColor> detectedBlocks = new HashMap<>();
    private final int minY = -64;
    private final int maxY = 0;

    // target for deepslate mining
    private BlockPos deepslateTarget = null;

    public TunnelBaseFinder() {
        super(GlazedAddon.CATEGORY, "TunnelBaseFinder", "Finds tunnel bases with ESP and smart detection.");
    }

    @Override
    public void onActivate() {
        currentDirection = getInitialDirection();
        targetYaw = mc.player.getYaw();
        avoidingHazard = false;
        detourBlocksRemaining = 0;
        rotationCooldownTicks = 0;
        detectedBlocks.clear();
        deepslateTarget = null;
    }

    @Override
    public void onDeactivate() {
        GameOptions options = mc.options;
        options.leftKey.setPressed(false);
        options.rightKey.setPressed(false);
        options.forwardKey.setPressed(false);
        detectedBlocks.clear();
        deepslateTarget = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || currentDirection == null) return;

        mc.player.setPitch(2.0f);
        updateYaw();

        // if we are in a cooldown while rotating, don't do other actions
        if (rotationCooldownTicks > 0) {
            mc.options.forwardKey.setPressed(false);
            rotationCooldownTicks--;
            if (rotationCooldownTicks == 0 && autoWalkMine.get()) {
                // resume walking and try to mine immediately after finishing rotation
                mc.options.forwardKey.setPressed(true);
                mineForward();
            }
            return;
        }

        if (!autoWalkMine.get()) {
            // nothing else to do
            notifyFound();
            return;
        }

        int y = mc.player.getBlockY();
        if (y > maxY || y < minY) {
            mc.options.forwardKey.setPressed(false);
            notifyFound();
            return;
        }

        // If rotated deepslate option enabled, scan short-range for deepslate targets and prioritize
        if (detectRotatedDeepslate.get()) {
            if (deepslateTarget == null) {
                deepslateTarget = findNearestDeepslate(12); // scan 12 blocks radius
                if (deepslateTarget != null) {
                    // rotate toward it
                    rotateToward(deepslateTarget);
                    rotationCooldownTicks = Math.max(4, 30 / Math.max(1, rotationSpeed.get())); // small wait to rotate
                    info("Rotated deepslate target found - moving toward it");
                    notifyFound();
                    return;
                }
            } else {
                // we already have a target; check if still exists and is reachable
                BlockState bs = mc.world.getBlockState(deepslateTarget);
                if (bs.isAir()) {
                    deepslateTarget = null; // target gone
                } else {
                    // rotate and mine toward it
                    rotateToward(deepslateTarget);
                    mineForward();
                    notifyFound();
                    return;
                }
            }
        }

        // Hazard avoidance flow
        if (avoidingHazard) {
            // continue walking forward until we bump into something in front, then choose safe side
            mc.options.forwardKey.setPressed(true);
            if (isBlockInFront()) {
                mc.options.forwardKey.setPressed(false);
                avoidingHazard = false;
                savedDirection = currentDirection;

                // pick safe direction (checks wider area)
                FacingDirection left = turnLeft(savedDirection);
                FacingDirection right = turnRight(savedDirection);

                boolean leftSafe = isSafeDirection(left);
                boolean rightSafe = isSafeDirection(right);

                if (leftSafe && !rightSafe) {
                    currentDirection = left;
                    setYawForDirection(left);
                } else if (!leftSafe && rightSafe) {
                    currentDirection = right;
                    setYawForDirection(right);
                } else if (leftSafe && rightSafe) {
                    // pick the direction with the larger safe margin
                    int leftScore = safetyScore(left);
                    int rightScore = safetyScore(right);
                    currentDirection = leftScore >= rightScore ? left : right;
                    setYawForDirection(currentDirection);
                } else {
                    // no safe direction -> stop moving
                    mc.options.forwardKey.setPressed(false);
                    warning("No safe direction to detour, stopped.");
                    return;
                }

                rotationCooldownTicks = 30;
                detourBlocksRemaining = detourLength.get();
            } else {
                // still walking into hazard avoidance
                // do nothing else (we avoid mining while walking into bump)
            }
            notifyFound();
            return;
        }

        // Normal operation - check hazards ahead (smarter detection)
        if (!detectHazards()) {
            // safe: mine what we're looking at
            mc.options.forwardKey.setPressed(true);
            mineForward();
        } else {
            // hazard detected -> start walking until bump
            avoidingHazard = true;
            info("Hazard detected! Walking forward until bump to rotate away.");
            mc.options.forwardKey.setPressed(true);
        }

        notifyFound();
    }

    // rotate player toward a BlockPos target (sets targetYaw) - no immediate teleport, we smoothly turn in updateYaw
    private void rotateToward(BlockPos target) {
        Vec3d p = mc.player.getPos();
        double dx = (target.getX() + 0.5) - p.x;
        double dz = (target.getZ() + 0.5) - p.z;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz)); // Minecraft yaw conversion
        targetYaw = (float) yaw;
    }

    // compute a simple safety score for a direction: how many non-hazard blocks in forward two-range + sides
    private int safetyScore(FacingDirection dir) {
        BlockPos playerPos = mc.player.getBlockPos();
        int score = 0;
        for (int f = 1; f <= 3; f++) {
            BlockPos p = playerPos.offset(dir.toMcDirection(), f);
            BlockState st = mc.world.getBlockState(p);
            if (!isHazardBlock(st)) score += 2;
            // check above a bit
            BlockState up = mc.world.getBlockState(p.up(1));
            if (!isHazardBlock(up)) score++;
        }
        return score;
    }

    private void setYawForDirection(FacingDirection dir) {
        switch (dir) {
            case NORTH -> targetYaw = 180f;
            case SOUTH -> targetYaw = 0f;
            case WEST -> targetYaw = 90f;
            case EAST -> targetYaw = -90f;
        }
    }

    private boolean isBlockInFront() {
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos target = playerPos.offset(currentDirection.toMcDirection());
        BlockState state = mc.world.getBlockState(target);
        return !state.isAir() && state.getBlock() != Blocks.BEDROCK;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        // draw ESP boxes for detectedBlocks (this is used for chests/shulkers/etc)
        detectedBlocks.forEach((pos, color) -> {
            event.renderer.box(pos, color, color, ShapeMode.Both, 0);
        });

        // if deepslateTarget present, draw box
        if (deepslateTarget != null && detectRotatedDeepslate.get()) {
            SettingColor col = deepslateColor.get();
            event.renderer.box(deepslateTarget, col, col, ShapeMode.Both, 0);
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof EntityStatusS2CPacket packet) {
            if (packet.getStatus() == 35 && packet.getEntity(mc.world) == mc.player) {
                disconnectWithMessage(Text.of("Totem Popped"));
                toggle();
            }
        }
    }

    private void updateYaw() {
        float currentYaw = mc.player.getYaw();
        float delta = targetYaw - currentYaw;
        delta = ((delta + 180) % 360 + 360) % 360 - 180;
        float step = rotationSpeed.get();
        if (Math.abs(delta) <= step) mc.player.setYaw(targetYaw);
        else mc.player.setYaw(currentYaw + Math.signum(delta) * step);
    }

    // server-compatible mining: mines the block the player is looking at
    private void mineForward() {
        if (mc.player == null || mc.interactionManager == null || mc.world == null) return;
        HitResult hit = mc.player.raycast(5.0, 0.0f, false);
        if (!(hit instanceof BlockHitResult bhr)) return;
        BlockPos target = bhr.getBlockPos();
        BlockState state = mc.world.getBlockState(target);
        if (state.isAir() || state.getBlock() == Blocks.BEDROCK) return;

        // only break if the target is safe to break into (double-check hazard conditions)
        if (isHazardBlock(state)) {
            // don't break hazards
            return;
        }

        if (mc.interactionManager.updateBlockBreakingProgress(target, bhr.getSide())) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    // detect hazards with more conservative ranges:
    // - lava/water: scan 10 blocks ahead, 2 blocks to each side, 2 above, 1 below
    // - gravel: treat as hazard when 2 above player, or 1 in front, or within 2 blocks to the sides in front
    private boolean detectHazards() {
        BlockPos playerPos = mc.player.getBlockPos();

        // check liquids (lava/water) in big forward area: 10 forward, 2 side, 2 up, 1 down
        Direction forward = currentDirection.toMcDirection();
        for (int f = 1; f <= 10; f++) {
            for (int side = -2; side <= 2; side++) {
                for (int dy = -1; dy <= 2; dy++) {
                    BlockPos p = playerPos.offset(forward, f).add(forward.getAxis() == Direction.Axis.X ? 0 : side, dy, forward.getAxis() == Direction.Axis.Z ? 0 : side);
                    BlockState st = safeGetBlockState(p);
                    if (st == null) continue;
                    if (isLiquid(st)) {
                        warning("Liquid hazard detected at " + p.toShortString() + " (" + st.getBlock().getName().getString() + ")");
                        return true;
                    }
                }
            }
        }

        // gravel detection: 2 above player
        for (int up = 1; up <= 2; up++) {
            BlockPos p = playerPos.up(up);
            BlockState st = safeGetBlockState(p);
            if (st != null && st.getBlock() == Blocks.GRAVEL) {
                warning("Gravel hazard above at " + p.toShortString());
                return true;
            }
        }

        // gravel 1 in front
        BlockPos front1 = playerPos.offset(forward, 1);
        BlockState f1 = safeGetBlockState(front1);
        if (f1 != null && f1.getBlock() == Blocks.GRAVEL) {
            warning("Gravel hazard 1 in front at " + front1.toShortString());
            return true;
        }

        // gravel 2 to the sides ahead (front + side offsets)
        for (int side = -2; side <= 2; side++) {
            if (side == 0) continue;
            BlockPos sidePos = playerPos.offset(forward, 1).add(forward.getAxis() == Direction.Axis.X ? 0 : side, 0, forward.getAxis() == Direction.Axis.Z ? 0 : side);
            BlockState sst = safeGetBlockState(sidePos);
            if (sst != null && sst.getBlock() == Blocks.GRAVEL) {
                warning("Gravel hazard near front side at " + sidePos.toShortString());
                return true;
            }
        }

        return false;
    }

    // helper: retrieve blockstate safely
    private BlockState safeGetBlockState(BlockPos pos) {
        try {
            return mc.world.getBlockState(pos);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isLiquid(BlockState st) {
        Block b = st.getBlock();
        return b == Blocks.LAVA || b == Blocks.WATER;
    }

    private boolean isHazardBlock(BlockState state) {
        if (state == null) return false;
        if (isLiquid(state)) return true;
        if (state.getBlock() == Blocks.GRAVEL) return true;
        return false;
    }

    // isSafeDirection checks forward 1..2 and above blocks to ensure no hazard
    private boolean isSafeDirection(FacingDirection dir) {
        BlockPos playerPos = mc.player.getBlockPos();
        Direction d = dir.toMcDirection();
        for (int f = 1; f <= 2; f++) {
            BlockPos p = playerPos.offset(d, f);
            BlockState st = safeGetBlockState(p);
            if (isHazardBlock(st)) return false;
            // check above the forward blocks
            BlockState stUp1 = safeGetBlockState(p.up(1));
            if (isHazardBlock(stUp1)) return false;
            BlockState stUp2 = safeGetBlockState(p.up(2));
            if (isHazardBlock(stUp2)) return false;
        }
        return true;
    }

    // find nearest deepslate-type block within radius and return its BlockPos (or null)
    private BlockPos findNearestDeepslate(int radius) {
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos p = playerPos.add(dx, dy, dz);
                    BlockState st = safeGetBlockState(p);
                    if (st == null) continue;
                    Block b = st.getBlock();
                    if (b == Blocks.DEEPSLATE || b == Blocks.DEEPSLATE_BRICKS || b == Blocks.POLISHED_DEEPSLATE || b == Blocks.CRACKED_DEEPSLATE_BRICKS) {
                        double dist = mc.player.getPos().distanceTo(Vec3d.ofCenter(p));
                        if (dist < bestDist) {
                            bestDist = dist;
                            best = p;
                        }
                    }
                }
            }
        }
        return best;
    }

    private void notifyFound() {
        int storage = 0;
        detectedBlocks.clear();
        int viewDist = mc.options.getViewDistance().getValue();
        BlockPos playerPos = mc.player.getBlockPos();

        for (int dx = -viewDist; dx <= viewDist; dx++) {
            for (int dz = -viewDist; dz <= viewDist; dz++) {
                WorldChunk chunk = mc.world.getChunkManager().getChunk(
                    (playerPos.getX() >> 4) + dx,
                    (playerPos.getZ() >> 4) + dz,
                    ChunkStatus.FULL,
                    false
                );
                if (chunk == null) continue;

                for (BlockPos pos : chunk.getBlockEntityPositions()) {
                    BlockEntity be = mc.world.getBlockEntity(pos);
                    if (be == null) continue;

                    SettingColor color = null;
                    if (detectSpawners.get() && be instanceof MobSpawnerBlockEntity) color = spawnerColor.get();
                    if (detectChests.get() && be instanceof ChestBlockEntity) color = chestColor.get();
                    if (detectBarrels.get() && be instanceof BarrelBlockEntity) color = barrelColor.get();
                    if (detectFurnaces.get() && be instanceof FurnaceBlockEntity) color = furnaceColor.get();
                    if (detectShulkers.get() && be instanceof ShulkerBoxBlockEntity) color = shulkerColor.get();
                    if (detectRedstone.get() && be instanceof PistonBlockEntity) color = pistonColor.get();

                    // Observers are blocks, not block entities; check block at pos
                    if (detectRedstone.get()) {
                        BlockState state = safeGetBlockState(pos);
                        if (state != null && state.getBlock() == Blocks.OBSERVER) {
                            color = observerColor.get();
                        }
                    }

                    if (color != null) {
                        storage++;
                        detectedBlocks.put(pos, color);
                    }
                }
            }
        }

        if (storage > baseThreshold.get()) {
            Vec3d p = mc.player.getPos();
            notifyFound("Base found", (int) p.x, (int) p.y, (int) p.z);
        }
    }

    private void notifyFound(String msg, int x, int y, int z) {
        if (discordNotification.get()) {
            info("[Discord notify] " + msg + " at " + x + " " + y + " " + z);
        }
        disconnectWithMessage(Text.of(msg));
        toggle();
    }

    private void disconnectWithMessage(Text text) {
        if (mc.player != null && mc.player.networkHandler != null) {
            MutableText literal = Text.literal("[TunnelBaseFinder] ").append(text);
            mc.player.networkHandler.getConnection().disconnect(literal);
        }
    }

    private FacingDirection turnLeft(FacingDirection dir) {
        return switch (dir) {
            case NORTH -> FacingDirection.WEST;
            case WEST -> FacingDirection.SOUTH;
            case SOUTH -> FacingDirection.EAST;
            case EAST -> FacingDirection.NORTH;
        };
    }

    private FacingDirection turnRight(FacingDirection dir) {
        return switch (dir) {
            case NORTH -> FacingDirection.EAST;
            case EAST -> FacingDirection.SOUTH;
            case SOUTH -> FacingDirection.WEST;
            case WEST -> FacingDirection.NORTH;
        };
    }

    enum FacingDirection {
        NORTH, SOUTH, EAST, WEST;
        public Direction toMcDirection() {
            return switch (this) {
                case NORTH -> Direction.NORTH;
                case SOUTH -> Direction.SOUTH;
                case EAST -> Direction.EAST;
                case WEST -> Direction.WEST;
            };
        }
    }
}
