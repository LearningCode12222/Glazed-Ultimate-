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

    // State (detour removed)
    private FacingDirection currentDirection;
    private float targetYaw;
    private int rotationCooldownTicks = 0;
    private boolean rotatingAvoidance = false;
    private FacingDirection avoidDirection = null;

    private final Map<BlockPos, SettingColor> detectedBlocks = new HashMap<>();
    private final int minY = -64;
    private final int maxY = 0;

    // Hazard scan distance (15 blocks as requested)
    private static final int HAZARD_SCAN_DISTANCE = 15;

    // scan timer for heavy scans
    private int scanTimer = 0;

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
        // do an immediate scan so ESP shows up quick
        notifyFound();
    }

    @Override
    public void onDeactivate() {
        GameOptions options = mc.options;
        options.leftKey.setPressed(false);
        options.rightKey.setPressed(false);
        options.forwardKey.setPressed(false);
        detectedBlocks.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || currentDirection == null) return;

        // keep pitch fixed
        mc.player.setPitch(2.0f);

        // Smoothly move yaw toward targetYaw
        updateYaw();

        // If we're currently rotating to avoid, just wait for rotation to finish
        if (rotationCooldownTicks > 0) {
            // ensure we aren't moving/mining while rotating
            mc.options.forwardKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);

            rotationCooldownTicks--;
            if (rotationCooldownTicks == 0) {
                // finished rotation: resume walking and mining
                rotatingAvoidance = false;
                avoidDirection = null;
                if (autoWalkMine.get()) {
                    mc.options.forwardKey.setPressed(true);
                    mineForward();
                }
            }
            return;
        }

        // Only auto-walk & mine when player is in allowed Y range
        if (autoWalkMine.get()) {
            int y = mc.player.getBlockY();
            if (y <= maxY && y >= minY) {
                // Check for hazard ahead (scan HAZARD_SCAN_DISTANCE)
                if (hazardInFront(HAZARD_SCAN_DISTANCE)) {
                    // Stop moving/mining and perform a single 90° rotation to a safe side
                    mc.options.forwardKey.setPressed(false);

                    FacingDirection left = turnLeft(currentDirection);
                    FacingDirection right = turnRight(currentDirection);

                    boolean leftSafe = isSafeDirection(left, HAZARD_SCAN_DISTANCE);
                    boolean rightSafe = isSafeDirection(right, HAZARD_SCAN_DISTANCE);

                    if (leftSafe && !rightSafe) {
                        // choose left
                        avoidDirection = left;
                        currentDirection = left;
                        targetYaw = mc.player.getYaw() - 90f;
                    } else if (!leftSafe && rightSafe) {
                        // choose right
                        avoidDirection = right;
                        currentDirection = right;
                        targetYaw = mc.player.getYaw() + 90f;
                    } else if (leftSafe) {
                        // both safe -> prefer left
                        avoidDirection = left;
                        currentDirection = left;
                        targetYaw = mc.player.getYaw() - 90f;
                    } else if (rightSafe) {
                        avoidDirection = right;
                        currentDirection = right;
                        targetYaw = mc.player.getYaw() + 90f;
                    } else {
                        // no safe side: stop and do not move
                        warning("Hazard ahead and no safe side within " + HAZARD_SCAN_DISTANCE + " blocks — stopping.");
                        mc.options.forwardKey.setPressed(false);
                        return;
                    }

                    // start rotation cooldown so updateYaw has time to move player yaw
                    rotatingAvoidance = true;
                    // ticks to rotate: compute approximate ticks needed based on rotationSpeed
                    float step = Math.max(1f, rotationSpeed.get());
                    // 90 degrees divided by step -> ticks; add a small buffer
                    rotationCooldownTicks = Math.max(2, (int) Math.ceil(90.0f / step) + 2);
                    return;
                } else {
                    // no hazard -> walk and mine
                    mc.options.forwardKey.setPressed(true);
                    mineForward();
                }
            } else mc.options.forwardKey.setPressed(false);
        }

        // Run the heavy scanning only every scanInterval ticks to reduce lag.
        if (++scanTimer >= scanInterval.get()) {
            scanTimer = 0;
            notifyFound();
        }
    }

    // Scan ahead up to 'distance' blocks for hazards (front column)
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

    // Check if a given side direction is free of hazards within 'distance' blocks
    private boolean isSafeDirection(FacingDirection dir, int distance) {
        BlockPos playerPos = mc.player.getBlockPos();
        for (int i = 1; i <= distance; i++) {
            BlockPos side = playerPos.offset(dir.toMcDirection(), i);
            BlockState bs = mc.world.getBlockState(side);
            if (isHazard(bs)) return false;
            BlockState above = mc.world.getBlockState(side.up());
            if (isHazard(above)) return false;
        }
        return true;
    }

    private void updateYaw() {
        float currentYaw = mc.player.getYaw();
        float delta = targetYaw - currentYaw;

        // normalize delta to [-180, 180]
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

    // Mining forward — quick, safe and unchanged
    private void mineForward() {
        if (mc.player == null || mc.interactionManager == null || mc.world == null) return;
        HitResult hit = mc.player.raycast(5.0, 0.0f, false);
        if (!(hit instanceof BlockHitResult bhr)) return;

        BlockPos target = bhr.getBlockPos();
        BlockState state = mc.world.getBlockState(target);
        if (state.isAir() || state.getBlock() == Blocks.BEDROCK) return;

        if (detectRotatedDeepslate.get() && isRotatedDeepslate(state)) {
            // lightweight log to notify (optional)
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
        if (state.contains(net.minecraft.state.property.Properties.AXIS)) {
            Direction.Axis axis = state.get(net.minecraft.state.property.Properties.AXIS);
            return axis != Direction.Axis.Y;
        }
        if (state.contains(net.minecraft.state.property.Properties.HORIZONTAL_FACING)) return true;
        if (state.contains(net.minecraft.state.property.Properties.FACING)) return true;
        return (b instanceof PillarBlock) || (b instanceof StairsBlock) || (b instanceof SlabBlock);
    }

    // detect hazards: lava, water, gravel, sand, powder_snow
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

                // lightweight rotated deepslate scan near player Y (±10)
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
