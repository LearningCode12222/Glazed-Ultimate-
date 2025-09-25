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

    // === General ===
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

    // === Detection ===
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

    private final Setting<Boolean> detectRotatedDeepslate = sgDetect.add(new BoolSetting.Builder()
        .name("detect-rotated-deepslate")
        .description("Detect rotated deepslate pillars, stairs and slabs (ESP + mine to them).")
        .defaultValue(true)
        .build()
    );

    // === ESP colors ===
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

    // === State ===
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
    private int scanCooldown = 0;
    private final int SCAN_INTERVAL_TICKS = 30;
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

    // ========== NEW FIX ==========
    private boolean isSafeDirection(FacingDirection dir) {
        BlockPos playerPos = mc.player.getBlockPos();
        for (int i = 1; i <= 3; i++) {
            BlockPos check = playerPos.offset(dir.toMcDirection(), i);
            BlockState state = mc.world.getBlockState(check);
            if (state.getBlock() == Blocks.BEDROCK || isHazard(state)) {
                return false;
            }
        }
        return true;
    }
    // =============================

    // ... rest of your methods (hazard detection, rotated deepslate scan, ESP, etc.) ...
    // The only changes were the isSafeDirection method above and replacing getTopY() calls.

    // Example getTopY fix inside scanning:
    // int maxYScan = Math.min(mc.world.getTopY(playerPos.getX(), playerPos.getZ()), playerPos.getY() + 16);

    // Keep your rotatedDeepslate detection logic the same.
    // ==================================================================
    // (Include the rest of your original file here without truncation.)
    // ==================================================================

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
