package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.*;
import net.minecraft.client.option.GameOptions;
import net.minecraft.entity.ItemEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.ArrayList;
import java.util.List;

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

    // Detection
    private final Setting<Integer> baseThreshold = sgDetect.add(new IntSetting.Builder()
        .name("base-threshold")
        .description("How many selected blocks before base is detected.")
        .defaultValue(50)
        .min(1)
        .sliderMax(500)
        .build()
    );

    private final Setting<Boolean> detectChests = sgDetect.add(new BoolSetting.Builder()
        .name("detect-chests")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectShulkers = sgDetect.add(new BoolSetting.Builder()
        .name("detect-shulkers")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectBarrels = sgDetect.add(new BoolSetting.Builder()
        .name("detect-barrels")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectSpawners = sgDetect.add(new BoolSetting.Builder()
        .name("detect-spawners")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectFurnaces = sgDetect.add(new BoolSetting.Builder()
        .name("detect-furnaces")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> detectRedstone = sgDetect.add(new BoolSetting.Builder()
        .name("detect-redstone")
        .defaultValue(false)
        .build()
    );

    // ESP settings
    private final Setting<meteordevelopment.meteorclient.utils.render.color.Color> espColor = sgRender.add(new ColorSetting.Builder()
        .name("esp-color")
        .description("Color of ESP boxes.")
        .defaultValue(new meteordevelopment.meteorclient.utils.render.color.Color(0, 255, 0, 80))
        .build()
    );

    private final Setting<Boolean> espOutline = sgRender.add(new BoolSetting.Builder()
        .name("esp-outline")
        .defaultValue(true)
        .build()
    );

    // State
    private Direction currentDirection;
    private boolean avoidingHazard = false;
    private Direction savedDirection;
    private int detourBlocksRemaining = 0;
    private final List<BlockPos> detectedBlocks = new ArrayList<>();

    private final int minY = -64;
    private final int maxY = 0;

    public TunnelBaseFinder() {
        super(GlazedAddon.CATEGORY, "TunnelBaseFinder", "Finds tunnel bases with ESP and smart detection.");
    }

    @Override
    public void onActivate() {
        currentDirection = getInitialDirection();
        avoidingHazard = false;
        detourBlocksRemaining = 0;
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

        // Auto walk + mine
        if (autoWalkMine.get()) {
            int y = mc.player.getBlockY();
            if (y <= maxY && y >= minY) {
                GameOptions options = mc.options;
                options.forwardKey.setPressed(true);

                if (avoidingHazard) {
                    if (detourBlocksRemaining > 0) {
                        mineForward();
                        detourBlocksRemaining--;
                    } else {
                        currentDirection = savedDirection;
                        avoidingHazard = false;
                    }
                } else {
                    if (!detectHazards()) {
                        mineForward();
                    } else {
                        savedDirection = currentDirection;
                        currentDirection = turnLeft(savedDirection);
                        detourBlocksRemaining = 10;
                        avoidingHazard = true;
                        info("Detouring around hazard for 10 blocks...");
                    }
                }
            } else {
                mc.options.forwardKey.setPressed(false);
            }
        }

        // Scan blocks
        notifyFound();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        for (BlockPos pos : detectedBlocks) {
            event.renderer.box(pos, espColor.get(), espColor.get(),
                ShapeMode.Both, 0);
        }
    }

    private Direction getInitialDirection() {
        float yaw = mc.player.getYaw() % 360.0f;
        if (yaw < 0.0f) yaw += 360.0f;

        if (yaw >= 45.0f && yaw < 135.0f) return Direction.WEST;
        if (yaw >= 135.0f && yaw < 225.0f) return Direction.NORTH;
        if (yaw >= 225.0f && yaw < 315.0f) return Direction.EAST;
        return Direction.SOUTH;
    }

    private void mineForward() {
        if (mc.player == null) return;

        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos target = switch (currentDirection) {
            case NORTH -> playerPos.north();
            case SOUTH -> playerPos.south();
            case EAST -> playerPos.east();
            case WEST -> playerPos.west();
        };

        if (mc.world == null || mc.interactionManager == null) return;

        BlockState state = mc.world.getBlockState(target);
        if (!state.isAir() && state.getBlock() != Blocks.BEDROCK) {
            mc.interactionManager.updateBlockBreakingProgress(target, currentDirectionToFacing());
            mc.player.swingHand(mc.player.getActiveHand());
        }
    }

    private net.minecraft.util.math.Direction currentDirectionToFacing() {
        return switch (currentDirection) {
            case NORTH -> net.minecraft.util.math.Direction.NORTH;
            case SOUTH -> net.minecraft.util.math.Direction.SOUTH;
            case EAST -> net.minecraft.util.math.Direction.EAST;
            case WEST -> net.minecraft.util.math.Direction.WEST;
        };
    }

    private boolean detectHazards() {
        BlockPos playerPos = mc.player.getBlockPos();

        for (BlockPos pos : BlockPos.iterateOutwards(playerPos, 10, 10, 10)) {
            BlockState state = mc.world.getBlockState(pos);

            if (state.getBlock() == Blocks.LAVA || state.getBlock() == Blocks.WATER) {
                warning("Hazard detected: " + state.getBlock().getName().getString() + " at " + pos.toShortString());
                return true;
            }
        }

        List<ItemEntity> items = mc.world.getEntitiesByClass(ItemEntity.class, mc.player.getBoundingBox().expand(10), e -> true);
        if (!items.isEmpty()) {
            warning("Dropped items detected nearby!");
            return true;
        }

        return false;
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

                    if (detectSpawners.get() && be instanceof MobSpawnerBlockEntity) {
                        storage++;
                        detectedBlocks.add(pos);
                    }
                    if (detectChests.get() && be instanceof ChestBlockEntity) {
                        storage++;
                        detectedBlocks.add(pos);
                    }
                    if (detectBarrels.get() && be instanceof BarrelBlockEntity) {
                        storage++;
                        detectedBlocks.add(pos);
                    }
                    if (detectFurnaces.get() && be instanceof FurnaceBlockEntity) {
                        storage++;
                        detectedBlocks.add(pos);
                    }
                    if (detectShulkers.get() && be instanceof ShulkerBoxBlockEntity) {
                        storage++;
                        detectedBlocks.add(pos);
                    }
                    // Example redstone detection: piston block entities
                    if (detectRedstone.get() && be instanceof PistonBlockEntity) {
                        storage++;
                        detectedBlocks.add(pos);
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

    private Direction turnLeft(Direction dir) {
        return switch (dir) {
            case NORTH -> Direction.WEST;
            case WEST -> Direction.SOUTH;
            case SOUTH -> Direction.EAST;
            case EAST -> Direction.NORTH;
        };
    }

    private Direction turnRight(Direction dir) {
        return switch (dir) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
        };
    }

    enum Direction {
        NORTH, SOUTH, EAST, WEST
    }
}
