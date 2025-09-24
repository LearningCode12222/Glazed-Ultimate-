package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.*;
import net.minecraft.client.option.GameOptions;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.ChunkStatus;

public class TunnelBaseFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> minimumStorage = sgGeneral.add(new DoubleSetting.Builder()
        .name("minimum-storage")
        .description("Minimum number of storage blocks before notifying.")
        .defaultValue(100.0)
        .min(1.0).max(500.0)
        .sliderMax(500.0)
        .build()
    );

    private final Setting<Boolean> spawners = sgGeneral.add(new BoolSetting.Builder()
        .name("spawners")
        .description("Notify when spawners are found.")
        .defaultValue(true)
        .build()
    );

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

    private Direction currentDirection;
    private int spawnerCount;

    // Y limits for tunneling
    private final int minY = -64;
    private final int maxY = 0;

    public TunnelBaseFinder() {
        super(GlazedAddon.CATEGORY, "TunnelBaseFinder", "Finds tunnel bases by digging, scanning, and tunneling.");
    }

    @Override
    public void onActivate() {
        currentDirection = getInitialDirection();
        spawnerCount = 0;
    }

    @Override
    public void onDeactivate() {
        // Reset pressed keys when toggled off
        GameOptions options = mc.options;
        options.leftKey.setPressed(false);
        options.rightKey.setPressed(false);
        options.forwardKey.setPressed(false);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || currentDirection == null) return;

        // Keep player slightly pitched down like original Krypton
        mc.player.setPitch(2.0f);

        // Auto walk + mine if underground
        if (autoWalkMine.get()) {
            int y = mc.player.getBlockY();
            if (y <= maxY && y >= minY) {
                GameOptions options = mc.options;
                options.forwardKey.setPressed(true);
                mineForward();
            } else {
                mc.options.forwardKey.setPressed(false);
            }
        }

        // Scan for bases / spawners
        notifyFound();
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

    private void notifyFound() {
        int storage = 0;
        int spawnerNearby = 0;
        BlockPos foundPos = null;

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

                    if (spawners.get() && be instanceof MobSpawnerBlockEntity) {
                        spawnerNearby++;
                        foundPos = pos;
                    }

                    if (be instanceof ChestBlockEntity
                        || be instanceof EnderChestBlockEntity
                        || be instanceof FurnaceBlockEntity
                        || be instanceof BarrelBlockEntity
                        || be instanceof EnchantingTableBlockEntity) {
                        storage++;
                    }
                }
            }
        }

        if (spawnerNearby > 10 && foundPos != null) {
            notifyFound("Spawner cluster found", foundPos.getX(), foundPos.getY(), foundPos.getZ(), false);
            spawnerCount = 0;
        }

        if (storage > minimumStorage.get()) {
            Vec3d p = mc.player.getPos();
            notifyFound("Base found", (int) p.x, (int) p.y, (int) p.z, true);
        }
    }

    private void notifyFound(String msg, int x, int y, int z, boolean base) {
        if (discordNotification.get()) {
            info("[Discord notify] " + msg + " at " + x + " " + y + " " + z);
        }

        // Gracefully disconnect with message
        disconnectWithMessage(Text.of(msg));
        toggle(); // disable module after notification
    }

    private void disconnectWithMessage(Text text) {
        if (mc.player != null && mc.player.networkHandler != null) {
            MutableText literal = Text.literal("[TunnelBaseFinder] ").append(text);
            mc.player.networkHandler.getConnection().disconnect(literal);
        }
    }

    public boolean isDigging() {
        return false;
    }

    enum Direction {
        NORTH, SOUTH, EAST, WEST
    }
}
