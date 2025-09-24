package com.nnpg.glazed.modules.main;

import com.mojang.authlib.GameProfile;
import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.meteor.MouseButtonEvent;
import meteordevelopment.meteorclient.events.meteor.MouseScrollEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.ChunkOcclusionEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

public class FreecamV2 extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Your speed while in freecam.")
        .onChanged(aDouble -> speedValue = aDouble)
        .defaultValue(1.0)
        .min(0.0)
        .build()
    );

    private final Setting<Double> speedScrollSensitivity = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed-scroll-sensitivity")
        .description("Allows you to change speed value using scroll wheel. 0 to disable.")
        .defaultValue(0)
        .min(0)
        .sliderMax(2)
        .build()
    );

    private final Setting<Boolean> staySneaking = sgGeneral.add(new BoolSetting.Builder()
        .name("stay-sneaking")
        .description("If you are sneaking when you enter freecam, whether your player should remain sneaking.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> toggleOnDamage = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-on-damage")
        .description("Disables freecam when you take damage.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> toggleOnDeath = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-on-death")
        .description("Disables freecam when you die.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> toggleOnLog = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-on-log")
        .description("Disables freecam when you disconnect from a server.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> reloadChunks = sgGeneral.add(new BoolSetting.Builder()
        .name("reload-chunks")
        .description("Reloads chunks on freecam toggle.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> renderHands = sgGeneral.add(new BoolSetting.Builder()
        .name("show-hands")
        .description("Whether or not to render your hands in freecam.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates to the block or entity you are looking at.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> staticView = sgGeneral.add(new BoolSetting.Builder()
        .name("static")
        .description("Disables settings that move the view.")
        .defaultValue(true)
        .build()
    );

    private double speedValue;
    private Perspective oldPerspective;
    private PlayerEntity oldCamera;
    private OtherClientPlayerEntity dummy;

    public FreecamV2() {
        super(Categories.Render, "freecam-v2", "Allows the camera to move away from the player.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) return;

        oldPerspective = mc.options.getPerspective();
        oldCamera = mc.cameraEntity instanceof PlayerEntity ? (PlayerEntity) mc.cameraEntity : null;

        // Proper GameProfile construction
        GameProfile profile = new GameProfile(mc.getSession().getUuidOrNull(), mc.getSession().getUsername());
        dummy = new OtherClientPlayerEntity(mc.world, profile);
        dummy.copyPositionAndRotation(mc.player);
        dummy.setHeadYaw(mc.player.getHeadYaw());

        // Correct addEntity usage
        mc.world.addEntity(dummy);

        // Switch camera
        mc.cameraEntity = dummy;

        if (reloadChunks.get()) mc.worldRenderer.reload();
    }

    @Override
    public void onDeactivate() {
        if (mc.player == null || mc.world == null) return;

        // Remove dummy
        if (dummy != null) {
            dummy.discard();
            dummy = null;
        }

        // Restore camera
        if (oldCamera != null) {
            mc.cameraEntity = oldCamera;
        }

        // Restore perspective
        mc.options.setPerspective(oldPerspective);

        if (reloadChunks.get()) mc.worldRenderer.reload();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (dummy == null) return;

        Vec3d move = Vec3d.ZERO;
        if (Input.isPressed(mc.options.forwardKey)) move = move.add(0, 0, 1);
        if (Input.isPressed(mc.options.backKey)) move = move.add(0, 0, -1);
        if (Input.isPressed(mc.options.leftKey)) move = move.add(1, 0, 0);
        if (Input.isPressed(mc.options.rightKey)) move = move.add(-1, 0, 0);
        if (Input.isPressed(mc.options.jumpKey)) move = move.add(0, 1, 0);
        if (Input.isPressed(mc.options.sneakKey)) move = move.add(0, -1, 0);

        if (move.lengthSquared() > 0) {
            move = move.normalize().multiply(speedValue);
            dummy.setPos(dummy.getX() + move.x, dummy.getY() + move.y, dummy.getZ() + move.z);
        }
    }
}
