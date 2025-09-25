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
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.client.option.GameOptions;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.state.property.Properties;
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

    // Rotated deepslate setting
    private final Setting<Boolean> detectRotatedDeepslate = sgDetect.add(new BoolSetting.Builder()
        .name("detect-rotated-deepslate")
        .description("Detect rotated deepslate pillars, stairs and slabs (ESP + mine to them).")
        .defaultValue(true)
        .build()
    );

    // ESP colors
    private final Setting<SettingColor> chestColor = sgRender.add(new ColorSetting.Builder().name("chest-color").defaultValue(new SettingColor(255, 165, 0, 80)).build());
    private final Setting<SettingColor> shulkerColor = sgRender.add(new ColorSetting.Builder().name("shulker-color").defaultValue(new SettingColor(255, 0, 255, 80)).build());
    private final Setting<SettingColor> barrelColor = sgRender.add(new ColorSetting.Builder().name("barrel-color").defaultValue(new SettingColor(139, 69, 19, 80)).build());
    private final Setting<SettingColor> spawnerColor = sgRender.add(new ColorSetting.Builder().name("spawner-color").defaultValue(new SettingColor(0, 0, 255, 80)).build());
    private final Setting<SettingColor> furnaceColor = sgRender.add(new ColorSetting.Builder().name("furnace-color").defaultValue(new SettingColor(128, 128, 128, 80)).build());
    private final Setting<SettingColor> redstoneColor = sgRender.add(new ColorSetting.Builder().name("redstone-color").defaultValue(new SettingColor(255, 0, 0, 80)).build());
    private final Setting<SettingColor> pistonColor = sgRender.add(new ColorSetting.Builder().name("piston-color").defaultValue(new SettingColor(200, 200, 200, 80)).build());
    private final Setting<SettingColor> observerColor = sgRender.add(new ColorSetting.Builder().name("observer-color").defaultValue(new SettingColor(100, 100, 100, 80)).build());
    private final Setting<SettingColor> rotatedDeepslateColor = sgRender.add(new ColorSetting.Builder().name("rotated-deepslate-color").defaultValue(new SettingColor(120, 120, 120, 120)).build());
    private final Setting<Boolean> espOutline = sgRender.add(new BoolSetting.Builder().name("esp-outline").defaultValue(true).build());

    // State
    private FacingDirection currentDirection;
    private FacingDirection savedDirection;
    private boolean avoidingHazard = false;
    private int detourBlocksRemaining = 0;
    private float targetYaw;
    private int rotationCooldownTicks = 0;
    private final Map<BlockPos, SettingColor> detectedBlocks = new HashMap<>();
    private final Set<BlockPos> rotatedDeepslatePositions = new HashSet<>();
    private final int minY = -64;
    private final int maxY = 0;

    // scanning control
    private int scanCooldown = 0;
    private final int SCAN_INTERVAL_TICKS = 30; // scan every 30 ticks (1.5s)

    // current target (nearest rotated deepslate)
    private BlockPos currentDeepslateTarget = null;

    public TunnelBaseFinder() {
        super(GlazedAddon.CATEGORY, "TunnelBaseFinder", "Finds tunnel bases with ESP, rotated deepslate, and smart hazard detection.");
    }

    @Override
    public void onActivate() {
        currentDirection = getInitialDirection();
        targetYaw = mc.player.getYaw();
        avoidingHazard = false;
        detourBlocksRemaining = 0;
        rotationCooldownTicks = 0;
        detectedBlocks.clear();
        rotatedDeepslatePositions.clear();
        scanCooldown = 0;
        currentDeepslateTarget = null;
    }

    @Override
    public void onDeactivate() {
        GameOptions options = mc.options;
        options.leftKey.setPressed(false);
        options.rightKey.setPressed(false);
        options.forwardKey.setPressed(false);
        detectedBlocks.clear();
        rotatedDeepslatePositions.clear();
        currentDeepslateTarget = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || currentDirection == null) return;

        // update rotation smoothly
        mc.player.setPitch(2.0f);
        updateYaw();

        // do periodic scan for rotated deepslate (not every tick)
        if (detectRotatedDeepslate.get()) {
            if (scanCooldown <= 0) {
                scanRotatedDeepslateInRenderDistance();
                scanCooldown = SCAN_INTERVAL_TICKS;
            } else scanCooldown--;
        } else {
            rotatedDeepslatePositions.clear();
            currentDeepslateTarget = null;
        }

        // if we have a deepslate target, prioritize rotating/moving to it (but still respect hazards)
        if (currentDeepslateTarget == null && !rotatedDeepslatePositions.isEmpty()) {
            // pick nearest
            currentDeepslateTarget = findNearest(rotatedDeepslatePositions);
        }

        // rotation cooldown handles smooth turning — while rotating, pause forward/mining actions
        if (rotationCooldownTicks > 0) {
            mc.options.forwardKey.setPressed(false);
            rotationCooldownTicks--;
            if (rotationCooldownTicks == 0 && autoWalkMine.get()) {
                mc.options.forwardKey.setPressed(true);
                mineForward();
            }
            return;
        }

        // If we have a deepslate target within reasonable distance, rotate to its placement orientation and mine it.
        if (detectRotatedDeepslate.get() && currentDeepslateTarget != null) {
            // if target gone (air), clear and continue
            BlockState bs = safeGetBlockState(currentDeepslateTarget);
            if (bs == null || bs.isAir()) {
                rotatedDeepslatePositions.remove(currentDeepslateTarget);
                currentDeepslateTarget = null;
            } else {
                // rotate to face the block according to its placement orientation
                rotateTowardBlockPlacement(currentDeepslateTarget, bs);
                // set a small rotation cooldown so updateYaw has time to approach targetYaw
                rotationCooldownTicks = Math.max(2, 20 / Math.max(1, rotationSpeed.get()));
                // after rotation completes, we'll mine it inside the next tick when rotationCooldownTicks reaches 0
                return;
            }
        }

        // NORMAL tunnel logic & hazard handling follows (mostly preserved)
        if (autoWalkMine.get()) {
            int y = mc.player.getBlockY();
            if (y <= maxY && y >= minY) {
                if (avoidingHazard) {
                    mc.options.forwardKey.setPressed(true);
                    if (isBlockInFront()) {
                        mc.options.forwardKey.setPressed(false);
                        avoidingHazard = false;
                        savedDirection = currentDirection;
                        // choose safe side
                        FacingDirection left = turnLeft(savedDirection);
                        FacingDirection right = turnRight(savedDirection);
                        if (isSafeDirection(left)) {
                            currentDirection = left;
                            targetYaw = mc.player.getYaw() - 90f;
                            info("Hazard: Turning LEFT (safe)");
                        } else if (isSafeDirection(right)) {
                            currentDirection = right;
                            targetYaw = mc.player.getYaw() + 90f;
                            info("Hazard: Turning RIGHT (safe)");
                        } else {
                            warning("No safe direction, stopping!");
                            mc.options.forwardKey.setPressed(false);
                            return;
                        }
                        rotationCooldownTicks = 30;
                        detourBlocksRemaining = detourLength.get();
                    }
                } else {
                    mc.options.forwardKey.setPressed(true);
                    if (!detectHazards()) {
                        mineForward();
                    } else {
                        avoidingHazard = true;
                        info("Hazard detected! Walking until bump...");
                    }
                }
            } else mc.options.forwardKey.setPressed(false);
        }

        notifyFound();
    }

    // Scans all loaded chunks within render distance for rotated deepslate blocks and populates rotatedDeepslatePositions.
    private void scanRotatedDeepslateInRenderDistance() {
        rotatedDeepslatePositions.clear();
        int viewDistChunks = mc.options.getViewDistance().getValue();
        BlockPos playerPos = mc.player.getBlockPos();

        // iterate chunks within view distance
        for (int dx = -viewDistChunks; dx <= viewDistChunks; dx++) {
            for (int dz = -viewDistChunks; dz <= viewDistChunks; dz++) {
                WorldChunk chunk = mc.world.getChunkManager().getChunk(
                    (playerPos.getX() >> 4) + dx,
                    (playerPos.getZ() >> 4) + dz,
                    ChunkStatus.FULL,
                    false
                );
                if (chunk == null) continue;

                // scan vertical slice near player Y (limit vertical scan to +/- 16 blocks to reduce load)
                int minYScan = Math.max(mc.world.getBottomY(), playerPos.getY() - 16);
                int maxYScan = Math.min(mc.world.getTopY(), playerPos.getY() + 16);

                for (int x = chunk.getPos().getStartX(); x <= chunk.getPos().getEndX(); x++) {
                    for (int z = chunk.getPos().getStartZ(); z <= chunk.getPos().getEndZ(); z++) {
                        for (int y = minYScan; y <= maxYScan; y++) {
                            BlockPos pos = new BlockPos(x, y, z);
                            BlockState state = safeGetBlockState(pos);
                            if (state == null) continue;
                            if (isRotatedDeepslate(state)) {
                                rotatedDeepslatePositions.add(pos);
                            }
                        }
                    }
                }
            }
        }
    }

    // Find nearest BlockPos from player among a set
    private BlockPos findNearest(Collection<BlockPos> positions) {
        BlockPos player = mc.player.getBlockPos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : positions) {
            double d = mc.player.getPos().squaredDistanceTo(p.toCenterPos());
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    // Rotate the player so the yaw lines up with the block's placement orientation
    private void rotateTowardBlockPlacement(BlockPos pos, BlockState state) {
        // If block uses AXIS (pillars), face along its axis (east/west for X, north/south for Z).
        if (state.contains(Properties.AXIS)) {
            Direction.Axis axis = state.get(Properties.AXIS);
            // Choose which cardinal direction to face based on player relative position to block
            double px = mc.player.getX();
            double pz = mc.player.getZ();
            double bx = pos.getX() + 0.5;
            double bz = pos.getZ() + 0.5;

            if (axis == Direction.Axis.X) {
                // face EAST or WEST (choose closer)
                if (Math.abs(px - bx) <= Math.abs(pz - bz)) {
                    // prefer EAST/WEST based on player x
                    targetYaw = (px < bx) ? -90f : 90f; // -90 east, 90 west
                } else {
                    // fallback to east
                    targetYaw = -90f;
                }
            } else if (axis == Direction.Axis.Z) {
                // face NORTH or SOUTH
                if (Math.abs(pz - bz) <= Math.abs(px - bx)) {
                    targetYaw = (pz < bz) ? 0f : 180f; // 0 south, 180 north
                } else {
                    targetYaw = 0f;
                }
            } else { // axis == Y -> not rotated pillar
                // face directly toward block center
                rotateTowardPos(pos);
                return;
            }
            info("Rotating to pillar axis " + axis.asString() + " for deepslate at " + pos.toShortString());
            return;
        }

        // If block contains horizontal facing (stairs, slabs), use that facing
        if (state.contains(Properties.HORIZONTAL_FACING)) {
            Direction facing = state.get(Properties.HORIZONTAL_FACING);
            targetYaw = yawForFacing(facing);
            info("Rotating to block facing " + facing.asString() + " at " + pos.toShortString());
            return;
        }

        // If block contains a direct FACING (3D), use that too
        if (state.contains(Properties.FACING)) {
            Direction facing = state.get(Properties.FACING);
            targetYaw = yawForFacing(facing);
            info("Rotating to block facing " + facing.asString() + " at " + pos.toShortString());
            return;
        }

        // otherwise, rotate to face block center
        rotateTowardPos(pos);
    }

    private void rotateTowardPos(BlockPos pos) {
        Vec3d p = mc.player.getPos();
        double dx = (pos.getX() + 0.5) - p.x;
        double dz = (pos.getZ() + 0.5) - p.z;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        targetYaw = (float) yaw;
    }

    private float yawForFacing(Direction facing) {
        return switch (facing) {
            case NORTH -> 180f;
            case SOUTH -> 0f;
            case WEST -> 90f;
            case EAST -> -90f;
            default -> 0f;
        };
    }

    private BlockState safeGetBlockState(BlockPos pos) {
        try {
            return mc.world.getBlockState(pos);
        } catch (Exception e) {
            return null;
        }
    }

    // If player is looking at a block, attempt to mine; when we are targeting rotated deepslate,
    // this will be called shortly after rotation target was set and rotationCooldown expires.
    private void mineForward() {
        if (mc.player == null || mc.interactionManager == null || mc.world == null) return;
        HitResult hit = mc.player.raycast(5.0, 0.0f, false);
        if (!(hit instanceof BlockHitResult bhr)) return;
        BlockPos target = bhr.getBlockPos();
        BlockState state = mc.world.getBlockState(target);
        if (state.isAir() || state.getBlock() == Blocks.BEDROCK) return;

        // If this is rotated deepslate, attempt to mine while oriented as above.
        if (detectRotatedDeepslate.get() && isRotatedDeepslate(state)) {
            // We assume rotateTowardBlockPlacement was called, so player will be turned to the correct yaw.
            // Use the hit side if available, otherwise default to UP.
            mc.interactionManager.updateBlockBreakingProgress(target, bhr.getSide());
            mc.player.swingHand(Hand.MAIN_HAND);
            // after mining attempt, clear the target so we can rescan/pick next
            rotatedDeepslatePositions.remove(target);
            if (currentDeepslateTarget != null && currentDeepslateTarget.equals(target)) currentDeepslateTarget = null;
            return;
        }

        // default mining behavior
        if (mc.interactionManager.updateBlockBreakingProgress(target, bhr.getSide())) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    // improved hazard detection (lava/water/gravel/sand/powder_snow)
    private boolean detectHazards() {
        BlockPos playerPos = mc.player.getBlockPos();

        // check 3 blocks above
        for (int i = 1; i <= 3; i++) {
            BlockState state = mc.world.getBlockState(playerPos.up(i));
            if (isHazard(state)) {
                warning("Hazard above: " + state.getBlock().getName().getString());
                return true;
            }
        }

        // check 3 blocks forward
        for (int i = 1; i <= 3; i++) {
            BlockPos front = playerPos.offset(currentDirection.toMcDirection(), i);
            BlockState state = mc.world.getBlockState(front);
            if (isHazard(state)) {
                warning("Hazard in front: " + state.getBlock().getName().getString());
                return true;
            }
        }

        return false;
    }

    private boolean isHazard(BlockState state) {
        if (state == null) return false;
        Block b = state.getBlock();
        return b == Blocks.LAVA || b == Blocks.WATER || b == Blocks.GRAVEL || b == Blocks.SAND || b == Blocks.POWDER_SNOW;
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

                    if (detectRedstone.get()) {
                        BlockState state = mc.world.getBlockState(pos);
                        if (state.getBlock() == Blocks.OBSERVER) color = observerColor.get();
                    }

                    if (color != null) {
                        storage++;
                        detectedBlocks.put(pos, color);
                    }
                }

                // add rotated deepslate positions to ESP map
                if (detectRotatedDeepslate.get()) {
                    for (BlockPos p : rotatedDeepslatePositions) {
                        detectedBlocks.put(p, rotatedDeepslateColor.get());
                    }
                }
            }
        }

        if (storage > baseThreshold.get()) {
            Vec3d p = mc.player.getPos();
            notifyFound("Base found", (int) p.x, (int) p.y, (int) p.z);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        // Draw ESP boxes for detected block entities + rotated deepslate positions
        detectedBlocks.forEach((pos, color) -> event.renderer.box(pos, color, color, ShapeMode.Both, 0));
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

    private FacingDirection getInitialDirection() {
        float yaw = mc.player.getYaw() % 360.0f;
        if (yaw < 0.0f) yaw += 360.0f;
        if (yaw >= 45.0f && yaw < 135.0f) return FacingDirection.WEST;
        if (yaw >= 135.0f && yaw < 225.0f) return FacingDirection.NORTH;
        if (yaw >= 225.0f && yaw < 315.0f) return FacingDirection.EAST;
        return FacingDirection.SOUTH;
    }

    private boolean isBlockInFront() {
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos target = playerPos.offset(currentDirection.toMcDirection());
        BlockState state = mc.world.getBlockState(target);
        return !state.isAir() && state.getBlock() != Blocks.BEDROCK;
    }

    private boolean isRotatedDeepslate(BlockState state) {
        if (state == null) return false;
        Block b = state.getBlock();
        // Basic filter: block's translation key contains "deepslate" AND it has an orientation property we can use
        String key = b.getTranslationKey().toLowerCase(Locale.ROOT);
        if (!key.contains("deepslate")) return false;

        // Many deepslate variants use Pillar/Stairs/Slab blocks or have AXIS/HORIZONTAL_FACING/FACING properties
        if (state.contains(Properties.AXIS)) {
            Direction.Axis axis = state.get(Properties.AXIS);
            return axis != Direction.Axis.Y; // Y axis usually not a rotated pillar
        }

        if (state.contains(Properties.HORIZONTAL_FACING)) return true;
        if (state.contains(Properties.FACING)) return true;

        // As fallback, check block classes
        return (b instanceof PillarBlock) || (b instanceof StairsBlock) || (b instanceof SlabBlock);
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
