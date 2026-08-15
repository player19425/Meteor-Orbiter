package orbiter.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
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

    @Shadow public abstract float getXRot();
    @Shadow public abstract float getYRot();
    @Shadow public abstract void setYRot(float yaw);
    @Shadow public abstract void setXRot(float pitch);
    @Shadow public float yRotO;
    @Shadow public float xRotO;

    private boolean orbiter$is360Active() {
        Camera360 mod = (Camera360) Modules.get().get("360-camera");
        return mod != null && mod.isActive();
    }

    private boolean orbiter$shouldInvertMouse() {
        Camera360 mod = (Camera360) Modules.get().get("360-camera");
        return mod != null && mod.isActive() && mod.shouldInvertMouse();
    }

    private float orbiter$normalizedPitch() {
        return ((getXRot() + 180) % 360 + 360) % 360 - 180;
    }

    private boolean orbiter$isUpsideDown() {
        float np = orbiter$normalizedPitch();
        return np > 90 || np < -90;
    }

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void orbiter$changeLookDirection(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if (!orbiter$is360Active()) return;
        ci.cancel();

        float f = (float) cursorDeltaY * 0.15F;
        float g = (float) cursorDeltaX * 0.15F;

        setXRot(getXRot() + f);
        xRotO += f;

        if (orbiter$shouldInvertMouse() && orbiter$isUpsideDown()) {
            setYRot(getYRot() - g);
            yRotO -= g;
        } else {
            setYRot(getYRot() + g);
            yRotO += g;
        }


    }

    @WrapOperation(
        method = "setXRot",
        at = @At(value = "INVOKE", target = "Ljava/lang/Math;clamp(FFF)F")
    )
    private float orbiter$unlockPitchSet(float value, float min, float max, Operation<Float> original) {
        if (orbiter$is360Active()) {
            float pitch = ((Entity)(Object)this).getXRot();
            return ((pitch + 180) % 360 + 360) % 360 - 180;
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
        return ((((Entity)(Object)this).getXRot() + 180) % 360 + 360) % 360 - 180;
    }

    private boolean orbiter$shouldInvertMovement() {
        Camera360 mod = (Camera360) Modules.get().get("360-camera");
        return mod != null && mod.isActive();
    }

    @Redirect(method = "jumpFromGround", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;sin(D)F"))
    private float orbiter$invertJumpSin(double value) {
        float result = Mth.sin(value);
        if (orbiter$is360Active() && orbiter$shouldInvertMovement()) {
            float np = orbiter$normalizedPitch();
            if (np < -90 || np > 90) return -result;
        }
        return result;
    }

    @Redirect(method = "jumpFromGround", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;cos(D)F"))
    private float orbiter$invertJumpCos(double value) {
        float result = Mth.cos(value);
        if (orbiter$is360Active() && orbiter$shouldInvertMovement()) {
            float np = orbiter$normalizedPitch();
            if (np < -90 || np > 90) return -result;
        }
        return result;
    }
}

@Mixin(Player.class)
abstract class Camera360TravelMixin {

    private boolean orbiter$is360Active() {
        Camera360 mod = (Camera360) Modules.get().get("360-camera");
        return mod != null && mod.isActive();
    }

    private float orbiter$normalizedPitch() {
        return ((getXRot() + 180) % 360 + 360) % 360 - 180;
    }

    private float getXRot() {
        return ((Entity)(Object)this).getXRot();
    }

    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true)
    private Vec3 orbiter$invertMovement(Vec3 movementInput) {
        if (!orbiter$is360Active()) return movementInput;
        float np = orbiter$normalizedPitch();
        if (np > 90 || np < -90) {
            return movementInput.multiply(-1, 1, -1);
        }
        return movementInput;
    }
}
