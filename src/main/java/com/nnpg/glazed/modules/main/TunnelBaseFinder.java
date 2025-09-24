package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;

/**
 * Meteor/Glazed port of TunnelBaseFinder from Krypton.
 * WARNING: Some Krypton-specific systems (DiscordWebhook, /shop) were stripped.
 */
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

    private final Setting<Boolean> autoTotemBuy = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-totem-buy")
        .description("Automatically buys totems from server shop.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> totemSlot = sgGeneral.add(new DoubleSetting.Builder()
        .name("totem-slot")
        .description("Hotbar slot for totems (1-9).")
        .defaultValue(8.0)
        .min(1.0).max(9.0)
        .build()
    );

    private final Setting<Boolean> autoMend = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-mend")
        .description("Automatically mends pickaxe with XP bottles.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> xpBottleSlot = sgGeneral.add(new DoubleSetting.Builder()
        .name("xp-bottle-slot")
        .description("Hotbar slot for XP bottles (1-9).")
        .defaultValue(9.0)
        .min(1.0).max(9.0)
        .build()
    );

    private final Setting<Boolean> discordNotification = sgGeneral.add(new BoolSetting.Builder()
        .name("discord-notification")
        .description("Send notification to Discord (requires webhook system).")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> webhook = sgGeneral.add(new StringSetting.Builder()
        .name("webhook")
        .description("Discord webhook URL.")
        .defaultValue("")
        .build()
    );

    private final Setting<Boolean> totemCheck = sgGeneral.add(new BoolSetting.Builder()
        .name("totem-check")
        .description("Notify when you run out of totems.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> totemCheckTime = sgGeneral.add(new DoubleSetting.Builder()
        .name("totem-check-time")
        .description("Time in ticks before notifying about missing totems.")
        .defaultValue(20.0)
        .min(1.0).max(120.0)
        .build()
    );

    private Direction currentDirection;
    private int blocksMined;
    private int spawnerCount;
    private int idleTicks;
    private Vec3d lastPosition;
    private boolean isDigging = false;
    private boolean shouldDig = false;
    private int totemCheckCounter = 0;
    private int totemBuyCounter = 0;
    private double actionDelay = 0.0;

    public TunnelBaseFinder() {
        super(GlazedAddon.CATEGORY, "TunnelBaseFinder", "Finds tunnel bases by digging and scanning.");
    }

    @Override
    public void onActivate() {
        currentDirection = getInitialDirection();
        blocksMined = 0;
        idleTicks = 0;
        spawnerCount = 0;
        lastPosition = null;
    }

    @Override
    public void onDeactivate() {
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.forwardKey.setPressed(false);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || currentDirection == null) return;

        // Simplified from Krypton logic
        mc.player.setPitch(2.0f);

        // Check for storage & spawners nearby
        notifyFound();

        // TODO: Add back AutoTotem/AutoEat compatibility if needed
    }

    private Direction getInitialDirection() {
        float yaw = mc.player.getYaw() % 360.0f;
        if (yaw < 0.0f) yaw += 360.0f;

        if (yaw >= 45.0f && yaw < 135.0f) return Direction.WEST;
        if (yaw >= 135.0f && yaw < 225.0f) return Direction.NORTH;
        if (yaw >= 225.0f && yaw < 315.0f) return Direction.EAST;
        return Direction.SOUTH;
    }

    private void notifyFound() {
        int storage = 0;
        int spawnerNearby = 0;
        BlockPos foundPos = null;

        for (WorldChunk chunk : mc.world.getChunkManager().getLoadedChunks()) {
            for (BlockPos pos : chunk.getBlockEntityPositions()) {
                BlockEntity be = mc.world.getBlockEntity(pos);
                if (spawners.get() && be instanceof MobSpawnerBlockEntity) {
                    spawnerNearby++;
                    foundPos = pos;
                }
                if (be instanceof ChestBlockEntity || be instanceof EnderChestBlockEntity ||
                    be instanceof FurnaceBlockEntity || be instanceof BarrelBlockEntity ||
                    be instanceof EnchantingTableBlockEntity) {
                    storage++;
                }
            }
        }

        if (spawnerNearby > 10) {
            notifyFound("Spawner found", foundPos.getX(), foundPos.getY(), foundPos.getZ(), false);
            spawnerCount = 0;
        }

        if (storage > minimumStorage.get()) {
            Vec3d p = mc.player.getPos();
            notifyFound("Base found", (int) p.x, (int) p.y, (int) p.z, true);
        }
    }

    private void notifyFound(String msg, int x, int y, int z, boolean base) {
        if (discordNotification.get()) {
            // Meteor has no built-in DiscordWebhook - you’d need a library or API call
            info("Discord notify: " + msg + " at " + x + " " + y + " " + z);
        }
        toggle();
        disconnectWithMessage(Text.of(msg));
    }

    private void disconnectWithMessage(Text text) {
        MutableText literal = Text.literal("[TunnelBaseFinder] ").append(text);
        toggle();
        mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(literal));
    }

    public boolean isDigging() {
        return isDigging;
    }

    enum Direction {
        NORTH, SOUTH, EAST, WEST
    }
}

