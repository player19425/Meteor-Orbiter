package orbiter.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import orbiter.modules.render.Camera360;

@Mixin(Entity.class)
public abstract class Camera360Mixin {

    @Shadow public abstract float getPitch();
    @Shadow public abstract float getYaw();
    @Shadow public abstract void setYaw(float yaw);
    @Shadow public abstract void setPitch(float pitch);
    @Shadow public float lastYaw;
    @Shadow public float lastPitch;

    private boolean orbiter$is360Active() {
        Camera360 mod = (Camera360) Modules.get().get("360-camera");
        return mod != null && mod.isActive();
    }

    private boolean orbiter$shouldInvertMouse() {
        Camera360 mod = (Camera360) Modules.get().get("360-camera");
        return mod != null && mod.isActive() && mod.shouldInvertMouse();
    }

    private float orbiter$normalizedPitch() {
        return ((getPitch() + 180) % 360 + 360) % 360 - 180;
    }

    private boolean orbiter$isUpsideDown() {
        float np = orbiter$normalizedPitch();
        return np > 90 || np < -90;
    }

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void orbiter$changeLookDirection(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if (!orbiter$is360Active()) return;
        ci.cancel();

        float f = (float) cursorDeltaY * 0.15F;
        float g = (float) cursorDeltaX * 0.15F;

        setPitch(getPitch() + f);
        lastPitch += f;

        if (orbiter$shouldInvertMouse() && orbiter$isUpsideDown()) {
            setYaw(getYaw() - g);
            lastYaw -= g;
        } else {
            setYaw(getYaw() + g);
            lastYaw += g;
        }

        Entity vehicle = ((Entity)(Object)this).getVehicle();
        if (vehicle != null) {
            vehicle.onPassengerLookAround((Entity)(Object)this);
        }
    }

    @Redirect(method = "setAngles", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;clamp(FFF)F"))
    private float orbiter$normalizePitchInSetAngles(float value, float min, float max) {
        if (orbiter$is360Active()) {
            float pitch = ((Entity)(Object)this).getPitch();
            return ((pitch + 180) % 360 + 360) % 360 - 180;
        }
        return MathHelper.clamp(value, min, max);
    }

    @WrapOperation(
        method = "setPitch",
        at = @At(value = "INVOKE", target = "Ljava/lang/Math;clamp(FFF)F")
    )
    private float orbiter$unlockPitchSet(float value, float min, float max, Operation<Float> original) {
        if (orbiter$is360Active()) {
            return value;
        }
        return original.call(value, min, max);
    }
}

@Mixin(LivingEntity.class)
abstract class Camera360JumpMixin {

    private boolean orbiter$is360Active() {
        Camera360 mod = (Camera360) Modules.get().get("360-camera");
        return mod != null && mod.isActive();
    }

    private float orbiter$normalizedPitch() {
        return ((((Entity)(Object)this).getPitch() + 180) % 360 + 360) % 360 - 180;
    }

    private boolean orbiter$shouldInvertMovement() {
        Camera360 mod = (Camera360) Modules.get().get("360-camera");
        return mod != null && mod.isActive();
    }

    @Redirect(method = "jump", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;sin(D)F"))
    private float orbiter$invertJumpSin(double value) {
        float result = MathHelper.sin(value);
        if (orbiter$is360Active() && orbiter$shouldInvertMovement()) {
            float np = orbiter$normalizedPitch();
            if (np < -90 || np > 90) return -result;
        }
        return result;
    }

    @Redirect(method = "jump", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;cos(D)F"))
    private float orbiter$invertJumpCos(double value) {
        float result = MathHelper.cos(value);
        if (orbiter$is360Active() && orbiter$shouldInvertMovement()) {
            float np = orbiter$normalizedPitch();
            if (np < -90 || np > 90) return -result;
        }
        return result;
    }
}

@Mixin(PlayerEntity.class)
abstract class Camera360TravelMixin {

    private boolean orbiter$is360Active() {
        Camera360 mod = (Camera360) Modules.get().get("360-camera");
        return mod != null && mod.isActive();
    }

    private float orbiter$normalizedPitch() {
        return ((getPitch() + 180) % 360 + 360) % 360 - 180;
    }

    private float getPitch() {
        return ((Entity)(Object)this).getPitch();
    }

    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true)
    private Vec3d orbiter$invertMovement(Vec3d movementInput) {
        if (!orbiter$is360Active()) return movementInput;
        float np = orbiter$normalizedPitch();
        if (np > 90 || np < -90) {
            return movementInput.multiply(-1, 1, -1);
        }
        return movementInput;
    }
}
