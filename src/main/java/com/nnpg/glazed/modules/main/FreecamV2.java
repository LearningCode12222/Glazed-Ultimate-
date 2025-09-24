package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

public class FreecamV2 extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Movement speed while in freecam.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderMax(10.0)
        .build()
    );

    public final Vector3d currentPosition = new Vector3d();
    public final Vector3d previousPosition = new Vector3d();

    private Perspective currentPerspective;
    private double movementSpeed;
    public float yaw, pitch;
    public float prevYaw, prevPitch;

    private boolean moveForward, moveBackward, moveRight, moveLeft, moveUp, moveDown;

    public FreecamV2() {
        super(GlazedAddon.CATEGORY, "FreecamV2", "Lets you move freely around the world without moving your player.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) {
            toggle();
            return;
        }

        yaw = mc.player.getYaw();
        pitch = mc.player.getPitch();
        currentPerspective = mc.options.getPerspective();
        movementSpeed = speed.get();

        Vec3d camPos = mc.gameRenderer.getCamera().getPos();
        currentPosition.set(camPos.x, camPos.y, camPos.z);
        previousPosition.set(camPos.x, camPos.y, camPos.z);

        if (mc.options.getPerspective() == Perspective.THIRD_PERSON_FRONT) {
            yaw += 180f;
            pitch *= -1f;
        }

        prevYaw = yaw;
        prevPitch = pitch;

        resetMovementKeys();
    }

    @Override
    public void onDeactivate() {
        resetMovementKeys();
        previousPosition.set(currentPosition);
        prevYaw = yaw;
        prevPitch = pitch;
    }

    private void resetMovementKeys() {
        moveForward = moveBackward = moveRight = moveLeft = moveUp = moveDown = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        if (!currentPerspective.isFirstPerson()) {
            mc.options.setPerspective(Perspective.FIRST_PERSON);
        }

        Vec3d forwardVec = Vec3d.fromPolar(0f, yaw);
        Vec3d rightVec = Vec3d.fromPolar(0f, yaw + 90f);

        double x = 0, y = 0, z = 0;
        double mult = mc.options.sprintKey.isPressed() ? 1.0 : 0.5;

        boolean horiz = false, lateral = false;

        if (moveForward) {
            x += forwardVec.x * mult * movementSpeed;
            z += forwardVec.z * mult * movementSpeed;
            horiz = true;
        }
        if (moveBackward) {
            x -= forwardVec.x * mult * movementSpeed;
            z -= forwardVec.z * mult * movementSpeed;
            horiz = true;
        }
        if (moveRight) {
            x += rightVec.x * mult * movementSpeed;
            z += rightVec.z * mult * movementSpeed;
            lateral = true;
        }
        if (moveLeft) {
            x -= rightVec.x * mult * movementSpeed;
            z -= rightVec.z * mult * movementSpeed;
            lateral = true;
        }

        if (horiz && lateral) {
            double diag = 1.0 / Math.sqrt(2.0);
            x *= diag;
            z *= diag;
        }

        if (moveUp) y += mult * movementSpeed;
        if (moveDown) y -= mult * movementSpeed;

        previousPosition.set(currentPosition);
        currentPosition.set(currentPosition.x + x, currentPosition.y + y, currentPosition.z + z);
    }

    public void updateRotation(double deltaYaw, double deltaPitch) {
        prevYaw = yaw;
        prevPitch = pitch;
        yaw += (float) deltaYaw;
        pitch += (float) deltaPitch;
        pitch = MathHelper.clamp(pitch, -90f, 90f);
    }

    public double getInterpolatedX(float tickDelta) {
        return MathHelper.lerp(tickDelta, previousPosition.x, currentPosition.x);
    }

    public double getInterpolatedY(float tickDelta) {
        return MathHelper.lerp(tickDelta, previousPosition.y, currentPosition.y);
    }

    public double getInterpolatedZ(float tickDelta) {
        return MathHelper.lerp(tickDelta, previousPosition.z, currentPosition.z);
    }

    public double getInterpolatedYaw(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevYaw, yaw);
    }

    public double getInterpolatedPitch(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevPitch, pitch);
    }
}
