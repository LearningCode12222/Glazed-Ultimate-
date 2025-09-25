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

    // ✅ New: Rotated Deepslate Detection
    private final Setting<Boolean> detectRotatedDeepslate = sgDetect.add(new BoolSetting.Builder()
        .name("detect-rotated-deepslate")
        .description("Detect rotated deepslate blocks and mine toward them.")
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
    private final int minY = -64;
    private final int maxY = 0;

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

        mc.player.setPitch(2.0f);
        updateYaw();

        if (rotationCooldownTicks > 0) {
            mc.options.forwardKey.setPressed(false);
            rotationCooldownTicks--;
            if (rotationCooldownTicks == 0 && autoWalkMine.get()) {
                mc.options.forwardKey.setPressed(true);
                mineForward();
            }
            return;
        }

        if (autoWalkMine.get()) {
            int y = mc.player.getBlockY();
            if (y <= maxY && y >= minY) {
                if (avoidingHazard) {
                    mc.options.forwardKey.setPressed(true);
                    if (isBlockInFront()) {
                        mc.options.forwardKey.setPressed(false);
                        avoidingHazard = false;
                        savedDirection = currentDirection;
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

    private boolean isBlockInFront() {
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos target = playerPos.offset(currentDirection.toMcDirection());
        BlockState state = mc.world.getBlockState(target);
        return !state.isAir() && state.getBlock() != Blocks.BEDROCK;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        detectedBlocks.forEach((pos, color) -> {
            event.renderer.box(pos, color, color, ShapeMode.Both, 0);
        });
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

    // ✅ Mining forward with block state respect
    private void mineForward() {
        if (mc.player == null || mc.interactionManager == null || mc.world == null) return;
        HitResult hit = mc.player.raycast(5.0, 0.0f, false);
        if (!(hit instanceof BlockHitResult bhr)) return;
        BlockPos target = bhr.getBlockPos();
        BlockState state = mc.world.getBlockState(target);
        if (state.isAir() || state.getBlock() == Blocks.BEDROCK) return;

        // ✅ Special rotated deepslate handling
        if (detectRotatedDeepslate.get() && state.getBlock() == Blocks.DEEPSLATE && state.contains(Properties.AXIS)) {
            Direction.Axis axis = state.get(Properties.AXIS);
            info("Rotated Deepslate found, mining along axis: " + axis.asString());
        }

        if (mc.interactionManager.updateBlockBreakingProgress(target, bhr.getSide())) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    // ✅ Hazard detection (more reliable: lava, water, gravel, sand, powdered snow)
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
        Block b = state.getBlock();
        return b == Blocks.LAVA || b == Blocks.WATER || b == Blocks.GRAVEL || b == Blocks.SAND || b == Blocks.POWDER_SNOW;
    }

    private boolean isSafeDirection(FacingDirection dir) {
        BlockPos playerPos = mc.player.getBlockPos();
        for (int i = 1; i <= 3; i++) {
            BlockPos side = playerPos.offset(dir.toMcDirection(), i);
            BlockState state = mc.world.getBlockState(side);
            if (isHazard(state)) return false;
        }
        return true;
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

                // ✅ Scan for rotated deepslate
                if (detectRotatedDeepslate.get()) {
                    for (BlockPos pos : BlockPos.iterate(chunk.getPos().getStartX(), mc.player.getBlockY() - 10, chunk.getPos().getStartZ(),
                                                         chunk.getPos().getEndX(), mc.player.getBlockY() + 10, chunk.getPos().getEndZ())) {
                        BlockState state = mc.world.getBlockState(pos);
                        if (state.getBlock() == Blocks.DEEPSLATE && state.contains(Properties.AXIS)) {
                            detectedBlocks.put(pos, rotatedDeepslateColor.get());
                            info("Rotated deepslate detected at " + pos.toShortString());
                        }
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
