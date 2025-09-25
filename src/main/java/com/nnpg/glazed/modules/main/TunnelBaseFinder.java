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

/**
 * Enhanced TunnelBaseFinder:
 * - A* pathfinder (bounded) to detected targets (prioritizes rotated deepslate)
 * - Avoids hazards (lava, water, gravel, sand, powder_snow)
 * - Scans chunks on an interval to reduce lag
 * - Smooth rotations and movement while following path and mining
 *
 * Note: A* is intentionally bounded to avoid big lag. Adjust MAX_PATH_NODES if needed.
 */
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

    private final Setting<Integer> rotationSpeed = sgGeneral.add(new IntSetting.Builder()
        .name("rotation-speed")
        .description("How fast yaw turns per tick (degrees).")
        .defaultValue(5)
        .min(1)
        .sliderMax(20)
        .build()
    );

    // How often to run the heavy scan (in ticks). 20 ticks ~= 1 second.
    private final Setting<Integer> scanInterval = sgGeneral.add(new IntSetting.Builder()
        .name("scan-interval")
        .description("Ticks between heavy chunk scans (higher = less lag, updates less often).")
        .defaultValue(20)
        .min(10)
        .sliderMax(120)
        .build()
    );

    // Pathfinding limits/tuning
    private final Setting<Integer> maxPathNodes = sgGeneral.add(new IntSetting.Builder()
        .name("max-path-nodes")
        .description("Maximum nodes A* will expand (helps limit lag).")
        .defaultValue(5000)
        .min(500)
        .sliderMax(20000)
        .build()
    );

    private final Setting<Integer> maxTargetDistance = sgGeneral.add(new IntSetting.Builder()
        .name("max-target-distance")
        .description("Maximum horizontal distance (in blocks) to consider moving to a target.")
        .defaultValue(200)
        .min(20)
        .sliderMax(1000)
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

    // Rotated Deepslate Detection
    private final Setting<Boolean> detectRotatedDeepslate = sgDetect.add(new BoolSetting.Builder()
        .name("detect-rotated-deepslate")
        .description("Detect rotated deepslate pillars, stairs, and slabs.")
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
    private float targetYaw;
    private int rotationCooldownTicks = 0;
    private boolean rotatingAvoidance = false;
    private FacingDirection avoidDirection = null;

    // A* and pathing state
    private List<BlockPos> currentPath = new ArrayList<>();
    private int pathIndex = 0;
    private boolean pathing = false;
    private BlockPos pathGoal = null;

    // Detected blocks (from scanning) -> position -> color
    private final Map<BlockPos, SettingColor> detectedBlocks = new HashMap<>();
    private final int minY = -64;
    private final int maxY = 0;

    // Hazard scan distance (15 blocks as requested)
    private static final int HAZARD_SCAN_DISTANCE = 15;

    // scan timer for heavy scans
    private int scanTimer = 0;

    // A* tuning constants
    private static final int NEIGHBOR_Y_DIFF = 1; // allow stepping up/down by 1 block

    public TunnelBaseFinder() {
        super(GlazedAddon.CATEGORY, "TunnelBaseFinder", "Finds tunnel bases with ESP, rotated deepslate, and smart hazard detection.");
    }

    @Override
    public void onActivate() {
        currentDirection = getInitialDirection();
        targetYaw = mc.player.getYaw();
        rotationCooldownTicks = 0;
        rotatingAvoidance = false;
        avoidDirection = null;
        detectedBlocks.clear();
        scanTimer = 0;
        currentPath.clear();
        pathIndex = 0;
        pathing = false;
        pathGoal = null;
        notifyFound(); // immediate scan
    }

    @Override
    public void onDeactivate() {
        GameOptions options = mc.options;
        options.leftKey.setPressed(false);
        options.rightKey.setPressed(false);
        options.forwardKey.setPressed(false);
        detectedBlocks.clear();
        currentPath.clear();
        pathing = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || currentDirection == null) return;

        // keep pitch fixed
        mc.player.setPitch(2.0f);

        // Smoothly move yaw toward targetYaw
        updateYaw();

        // rotation cooldown handling
        if (rotationCooldownTicks > 0) {
            mc.options.forwardKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);

            rotationCooldownTicks--;
            if (rotationCooldownTicks == 0) {
                rotatingAvoidance = false;
                avoidDirection = null;
                if (autoWalkMine.get() && !pathing) {
                    mc.options.forwardKey.setPressed(true);
                    mineForward();
                }
            }
            return;
        }

        // Run heavy scan periodically
        if (++scanTimer >= scanInterval.get()) {
            scanTimer = 0;
            notifyFound(); // fills detectedBlocks
        }

        // If we have a current path, follow it (pathing takes precedence)
        if (pathing && currentPath != null && !currentPath.isEmpty()) {
            followPathTick();
            return;
        }

        // Otherwise, normal auto-walk and hazard avoidance behavior
        if (autoWalkMine.get()) {
            int y = mc.player.getBlockY();
            if (y <= maxY && y >= minY) {
                if (hazardInFront(HAZARD_SCAN_DISTANCE)) {
                    mc.options.forwardKey.setPressed(false);
                    FacingDirection left = turnLeft(currentDirection);
                    FacingDirection right = turnRight(currentDirection);

                    boolean leftSafe = isSafeDirection(left, HAZARD_SCAN_DISTANCE);
                    boolean rightSafe = isSafeDirection(right, HAZARD_SCAN_DISTANCE);

                    if (leftSafe && !rightSafe) {
                        avoidDirection = left; currentDirection = left; targetYaw = mc.player.getYaw() - 90f;
                    } else if (!leftSafe && rightSafe) {
                        avoidDirection = right; currentDirection = right; targetYaw = mc.player.getYaw() + 90f;
                    } else if (leftSafe) {
                        avoidDirection = left; currentDirection = left; targetYaw = mc.player.getYaw() - 90f;
                    } else if (rightSafe) {
                        avoidDirection = right; currentDirection = right; targetYaw = mc.player.getYaw() + 90f;
                    } else {
                        warning("Hazard ahead and no safe side within " + HAZARD_SCAN_DISTANCE + " blocks — stopping.");
                        mc.options.forwardKey.setPressed(false);
                        return;
                    }

                    rotatingAvoidance = true;
                    float step = Math.max(1f, rotationSpeed.get());
                    rotationCooldownTicks = Math.max(2, (int) Math.ceil(90.0f / step) + 2);
                    return;
                } else {
                    // try to pick targets and build path if any prioritized target exists
                    BlockPos chosen = pickPriorityTarget();
                    if (chosen != null) {
                        // compute A* path to chosen target
                        if (computeAndStartPathTo(chosen)) {
                            // pathing will take over next tick
                            return;
                        } else {
                            // could not compute path; fallback to normal mining
                            mc.options.forwardKey.setPressed(true);
                            mineForward();
                        }
                    } else {
                        // no targets -> continue normal behavior
                        mc.options.forwardKey.setPressed(true);
                        mineForward();
                    }
                }
            } else {
                mc.options.forwardKey.setPressed(false);
            }
        }
    }

    // Called every tick while pathing
    private void followPathTick() {
        if (mc.player == null || mc.world == null) return;
        if (currentPath == null || currentPath.isEmpty()) {
            pathing = false;
            return;
        }

        // ensure pathIndex valid
        if (pathIndex >= currentPath.size()) {
            // Reached goal (or path exhausted)
            pathing = false;
            currentPath.clear();
            pathIndex = 0;
            pathGoal = null;
            mc.options.forwardKey.setPressed(false);
            return;
        }

        BlockPos next = currentPath.get(pathIndex);
        Vec3d playerPos = mc.player.getPos();
        double dx = (next.getX() + 0.5) - playerPos.x;
        double dz = (next.getZ() + 0.5) - playerPos.z;
        double dy = (next.getY() + 0.5) - playerPos.y;
        double horizDist = Math.sqrt(dx * dx + dz * dz);

        // face the next node
        float yawTo = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        targetYaw = yawTo;

        // If there's a blocking hazardous block on the next step, try to abort path
        BlockState nextState = mc.world.getBlockState(next);
        if (isHazard(nextState)) {
            // abort path and try to re-plan later
            pathing = false;
            currentPath.clear();
            pathIndex = 0;
            pathGoal = null;
            mc.options.forwardKey.setPressed(false);
            return;
        }

        // If block at next is not air, start mining it (we will move into it after it's broken)
        if (!nextState.isAir() && nextState.getBlock() != Blocks.BEDROCK) {
            // stop forward movement while mining
            mc.options.forwardKey.setPressed(false);

            // aim at the block (raycast/mine)
            BlockHitResult bhr = new BlockHitResult(new Vec3d(next.getX() + 0.5, next.getY() + 0.5, next.getZ() + 0.5),
                Direction.UP, next, false);
            if (mc.interactionManager.updateBlockBreakingProgress(next, Direction.UP)) {
                mc.player.swingHand(Hand.MAIN_HAND);
            }

            // If player near the block face, allow to break and then step into it next tick
            if (horizDist < 0.8 && Math.abs(dy) < 1.2) {
                // we will advance to next index after block is broken in-world (detection next tick)
                // Simple check: if now air, advance
                BlockState refreshed = mc.world.getBlockState(next);
                if (refreshed.isAir()) {
                    pathIndex++;
                }
            }
            return;
        }

        // If the next node is free (air), move toward it
        // center player onto the block column as you requested (move to middle XZ)
        double moveThreshold = 0.25;
        double targetX = next.getX() + 0.5;
        double targetZ = next.getZ() + 0.5;
        double cx = playerPos.x;
        double cz = playerPos.z;
        double offX = targetX - cx;
        double offZ = targetZ - cz;

        // Small steering: if offset big, slightly strafe to center, else forward
        if (Math.abs(offX) > moveThreshold || Math.abs(offZ) > moveThreshold) {
            // compute simple forward/back movement with left/right to center faster
            mc.options.forwardKey.setPressed(true);
            // we don't have direct strafe control here besides leftKey/rightKey, which is coarse.
            // Try to approximate centering by toggling left/right keys depending on cross product sign.
            // Use a simple left/right decision: if heading to the right relative to facing, press rightKey
            Vec3d dirVec = new Vec3d(Math.cos(Math.toRadians(mc.player.getYaw())), 0, Math.sin(Math.toRadians(mc.player.getYaw())));
            double cross = dirVec.x * offZ - dirVec.z * offX;
            mc.options.leftKey.setPressed(cross > 0);
            mc.options.rightKey.setPressed(cross < 0);
        } else {
            // centered enough: go straight
            mc.options.forwardKey.setPressed(true);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
        }

        // If we are close enough to the node, advance
        if (horizDist < 0.6 && Math.abs(dy) < 1.2) {
            pathIndex++;
            // reached goal?
            if (pathIndex >= currentPath.size()) {
                // reached final node: if it's a rotated deepslate target, orient to its face and mine
                pathing = false;
                BlockPos reached = next;
                onReachedTarget(reached);
                currentPath.clear();
                pathIndex = 0;
                pathGoal = null;
                mc.options.forwardKey.setPressed(false);
            }
        }
    }

    // Called after reaching a target node
    private void onReachedTarget(BlockPos reached) {
        BlockState state = mc.world.getBlockState(reached);
        if (state == null) return;

        // If rotated deepslate, rotate to face its placement direction
        if (detectRotatedDeepslate.get() && isRotatedDeepslate(state)) {
            // attempt to get facing/axis and set targetYaw accordingly
            try {
                if (state.contains(Properties.HORIZONTAL_FACING)) {
                    Direction face = state.get(Properties.HORIZONTAL_FACING);
                    targetYaw = directionToYaw(face);
                } else if (state.contains(Properties.FACING)) {
                    Direction face = state.get(Properties.FACING);
                    targetYaw = directionToYaw(face);
                } else if (state.contains(Properties.AXIS)) {
                    Direction.Axis axis = state.get(Properties.AXIS);
                    // axis X -> either EAST/WEST; prefer EAST (90) or WEST (-90)
                    if (axis == Direction.Axis.X) targetYaw = 90f;
                    else targetYaw = 0f;
                }
            } catch (Exception ignored) {}
        }

        // Try to mine the block (if it's not air)
        if (!state.isAir() && state.getBlock() != Blocks.BEDROCK) {
            if (mc.interactionManager.updateBlockBreakingProgress(reached, Direction.UP)) {
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    // Pick a target: prefer rotated deepslate near player first; then storage blocks if threshold triggered.
    private BlockPos pickPriorityTarget() {
        if (detectedBlocks.isEmpty()) return null;
        BlockPos playerPos = mc.player.getBlockPos();

        // First gather rotated deepslate targets
        List<BlockPos> deeps = new ArrayList<>();
        List<BlockPos> storages = new ArrayList<>();

        for (Map.Entry<BlockPos, SettingColor> e : detectedBlocks.entrySet()) {
            BlockPos pos = e.getKey();
            SettingColor c = e.getValue();
            if (pos == null) continue;
            // use color equality: rotatedDeepslateColor -> prioritized
            if (detectRotatedDeepslate.get() && c != null && c.equals(rotatedDeepslateColor.get())) {
                deeps.add(pos);
            } else {
                // consider as storage/block-entity candidate
                storages.add(pos);
            }
        }

        // choose nearest rotated deepslate within maxTargetDistance
        BlockPos best = nearestInListWithin(deeps, playerPos, maxTargetDistance.get());
        if (best != null) return best;

        // if storage count exceeds threshold, pick nearest storage
        if (storages.size() >= baseThreshold.get()) {
            return nearestInListWithin(storages, playerPos, maxTargetDistance.get());
        }

        // else no high priority target
        return null;
    }

    private BlockPos nearestInListWithin(List<BlockPos> list, BlockPos from, int maxDist) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : list) {
            double d = from.getSquaredDistance(p);
            if (d < bestD && Math.sqrt(d) <= maxDist) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    /**
     * Compute and start path to target using A*.
     * Returns true if path found and started.
     */
    private boolean computeAndStartPathTo(BlockPos goal) {
        if (mc.player == null || mc.world == null || goal == null) return false;

        BlockPos start = mc.player.getBlockPos();
        // quick reject if goal too far
        if (start.getSquaredDistance(goal) > (long) maxTargetDistance.get() * maxTargetDistance.get()) return false;

        List<BlockPos> path = computeAStar(start, goal, maxPathNodes.get());
        if (path == null || path.isEmpty()) return false;

        // store and start pathing
        currentPath = path;
        pathIndex = 0;
        pathing = true;
        pathGoal = goal;
        // ensure we stop normal forward movement and let pathing manage movement
        mc.options.forwardKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        return true;
    }

    // A* pathfinding on grid (bounded). Allowed to move into blocks (we will mine them), but we avoid hazards.
    private List<BlockPos> computeAStar(BlockPos start, BlockPos goal, int maxNodes) {
        final int maxExpanded = Math.max(500, maxNodes);

        class Node {
            BlockPos pos;
            int g;
            int f;
            Node parent;
            Node(BlockPos pos, int g, int f, Node parent) { this.pos = pos; this.g = g; this.f = f; this.parent = parent; }
        }

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingInt(n -> n.f));
        Map<BlockPos, Integer> bestG = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();

        int h0 = heuristic(start, goal);
        open.add(new Node(start, 0, h0, null));
        bestG.put(start, 0);

        int expanded = 0;
        Direction[] cardinals = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

        while (!open.isEmpty() && expanded < maxExpanded) {
            Node cur = open.poll();
            if (cur == null) break;
            expanded++;
            if (cur.pos.equals(goal)) {
                // reconstruct path
                LinkedList<BlockPos> out = new LinkedList<>();
                Node n = cur;
                while (n != null) {
                    out.addFirst(n.pos);
                    n = n.parent;
                }
                return out;
            }

            closed.add(cur.pos);

            // neighbors: cardinals, allow +/- y by 1 to step up/down
            for (Direction d : cardinals) {
                BlockPos neighbor = cur.pos.offset(d);
                // allow stepping up or down by NEIGHBOR_Y_DIFF
                for (int dy = -NEIGHBOR_Y_DIFF; dy <= NEIGHBOR_Y_DIFF; dy++) {
                    BlockPos nb = neighbor.add(0, dy, 0);

                    // quick reject by horizontal distance from player to keep search local
                    if (mc.player.getBlockPos().getSquaredDistance(nb) > maxTargetDistance.get() * maxTargetDistance.get()) continue;

                    if (closed.contains(nb)) continue;
                    // reject definitely bad positions: bedrock or hazard at nb or hazard above nb
                    BlockState bs = mc.world.getBlockState(nb);
                    BlockState above = mc.world.getBlockState(nb.up());
                    if (bs.getBlock() == Blocks.BEDROCK) continue;
                    if (isHazard(bs) || isHazard(above)) continue;

                    int tentativeG = cur.g + 1 + Math.abs(dy); // penalize vertical moves slightly

                    Integer recorded = bestG.get(nb);
                    if (recorded != null && tentativeG >= recorded) continue;

                    bestG.put(nb, tentativeG);
                    int f = tentativeG + heuristic(nb, goal);
                    open.add(new Node(nb, tentativeG, f, cur));
                }
            }
        }

        // no path found (or exceeded budget)
        return null;
    }

    private int heuristic(BlockPos a, BlockPos b) {
        // Manhattan on XZ plus Y penalty
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ()) + Math.abs(a.getY() - b.getY());
    }

    // helper to convert direction to yaw
    private float directionToYaw(Direction d) {
        return switch (d) {
            case NORTH -> 180f;
            case SOUTH -> 0f;
            case EAST -> 90f;
            case WEST -> -90f;
            case UP, DOWN -> mc.player.getYaw();
        };
    }

    // original utility methods (hazard detection, rotated deepslate detection, mining)
    private boolean hazardInFront(int distance) {
        BlockPos playerPos = mc.player.getBlockPos();

        for (int i = 1; i <= distance; i++) {
            BlockPos forward = playerPos.offset(currentDirection.toMcDirection(), i);
            BlockState bs = mc.world.getBlockState(forward);
            if (isHazard(bs)) return true;

            // also check above the forward block (falling liquids/gravel/sand above)
            BlockState above = mc.world.getBlockState(forward.up());
            if (isHazard(above)) return true;
        }
        return false;
    }

    private boolean isSafeDirection(FacingDirection dir, int distance) {
        BlockPos playerPos = mc.player.getBlockPos();
        for (int i = 1; i <= distance; i++) {
            BlockPos side = playerPos.offset(dir.toMcDirection(), i);
            BlockState state = mc.world.getBlockState(side);
            if (isHazard(state)) return false;
            BlockState above = mc.world.getBlockState(side.up());
            if (isHazard(above)) return false;
        }
        return true;
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

    private void mineForward() {
        if (mc.player == null || mc.interactionManager == null || mc.world == null) return;
        HitResult hit = mc.player.raycast(5.0, 0.0f, false);
        if (!(hit instanceof BlockHitResult bhr)) return;

        BlockPos target = bhr.getBlockPos();
        BlockState state = mc.world.getBlockState(target);
        if (state.isAir() || state.getBlock() == Blocks.BEDROCK) return;

        if (detectRotatedDeepslate.get() && isRotatedDeepslate(state)) {
            info("Rotated Deepslate found at " + target.toShortString());
        }

        if (mc.interactionManager.updateBlockBreakingProgress(target, bhr.getSide())) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private boolean isRotatedDeepslate(BlockState state) {
        if (state == null) return false;
        Block b = state.getBlock();
        String key = b.getTranslationKey().toLowerCase(Locale.ROOT);
        if (!key.contains("deepslate")) return false;
        if (state.contains(Properties.AXIS)) {
            Direction.Axis axis = state.get(Properties.AXIS);
            return axis != Direction.Axis.Y;
        }
        if (state.contains(Properties.HORIZONTAL_FACING)) return true;
        if (state.contains(Properties.FACING)) return true;
        return (b instanceof PillarBlock) || (b instanceof StairsBlock) || (b instanceof SlabBlock);
    }

    private boolean isHazard(BlockState state) {
        if (state == null) return false;
        Block b = state.getBlock();
        return b == Blocks.LAVA || b == Blocks.WATER || b == Blocks.GRAVEL || b == Blocks.SAND || b == Blocks.POWDER_SNOW;
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

                // Scan for rotated deepslate around player Y (±10)
                if (detectRotatedDeepslate.get()) {
                    for (BlockPos pos : BlockPos.iterate(
                        chunk.getPos().getStartX(), mc.player.getBlockY() - 10, chunk.getPos().getStartZ(),
                        chunk.getPos().getEndX(), mc.player.getBlockY() + 10, chunk.getPos().getEndZ()
                    )) {
                        BlockState state = mc.world.getBlockState(pos);
                        if (isRotatedDeepslate(state)) detectedBlocks.put(pos, rotatedDeepslateColor.get());
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
        detectedBlocks.forEach((pos, color) -> event.renderer.box(pos, color, color, ShapeMode.Both, 0));
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
