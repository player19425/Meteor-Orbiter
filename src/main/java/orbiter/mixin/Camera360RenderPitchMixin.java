package orbiter.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.Mth;
import orbiter.modules.render.Camera360;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LocalPlayer.class)
public abstract class Camera360RenderPitchMixin {

    private boolean orbiter$is360Active() {
        Camera360 mod = (Camera360) Modules.get().get("360-camera");
        return mod != null && mod.isActive();
    }

    @WrapOperation(method = "applyInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F", ordinal = 0))
    private float orbiter$wrapRenderPitchTarget(LocalPlayer player, Operation<Float> original) {
        float pitch = original.call(player);
        if (!orbiter$is360Active()) return pitch;
        return player.getXRot(1.0f) + Mth.wrapDegrees(pitch - player.getXRot(1.0f));
    }

    @WrapOperation(method = "applyInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F", ordinal = 0))
    private float orbiter$wrapRenderYawTarget(LocalPlayer player, Operation<Float> original) {
        float yaw = original.call(player);
        if (!orbiter$is360Active()) return yaw;
        return player.getYRot(1.0f) + Mth.wrapDegrees(yaw - player.getYRot(1.0f));
    }
}
