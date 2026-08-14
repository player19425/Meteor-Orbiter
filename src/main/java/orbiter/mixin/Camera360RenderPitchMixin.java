package orbiter.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import orbiter.modules.render.Camera360;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPlayerEntity.class)
public abstract class Camera360RenderPitchMixin {

    private boolean orbiter$is360Active() {
        Camera360 mod = (Camera360) Modules.get().get("360-camera");
        return mod != null && mod.isActive();
    }

    @WrapOperation(method = "tickMovementInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getPitch()F", ordinal = 0))
    private float orbiter$wrapRenderPitchTarget(Entity entity, Operation<Float> original) {
        float pitch = original.call(entity);
        if (!orbiter$is360Active()) return pitch;
        ClientPlayerEntity player = (ClientPlayerEntity)(Object)this;
        return player.renderPitch + MathHelper.wrapDegrees(pitch - player.renderPitch);
    }

    @WrapOperation(method = "tickMovementInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw()F", ordinal = 0))
    private float orbiter$wrapRenderYawTarget(Entity entity, Operation<Float> original) {
        float yaw = original.call(entity);
        if (!orbiter$is360Active()) return yaw;
        ClientPlayerEntity player = (ClientPlayerEntity)(Object)this;
        return player.renderYaw + MathHelper.wrapDegrees(yaw - player.renderYaw);
    }
}
