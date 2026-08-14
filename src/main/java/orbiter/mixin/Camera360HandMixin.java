package orbiter.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import orbiter.modules.render.Camera360;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(HeldItemRenderer.class)
public abstract class Camera360HandMixin {

    private boolean orbiter$is360Active() {
        Camera360 mod = (Camera360) Modules.get().get("360-camera");
        return mod != null && mod.isActive();
    }

    @WrapOperation(method = "renderItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getPitch(F)F"))
    private float orbiter$wrapHandTiltPitch(Entity entity, float tickProgress, Operation<Float> original) {
        float pitch = original.call(entity, tickProgress);
        if (!orbiter$is360Active()) return pitch;
        ClientPlayerEntity player = (ClientPlayerEntity) entity;
        float h = MathHelper.lerp(tickProgress, player.lastRenderPitch, player.renderPitch);
        return h + MathHelper.wrapDegrees(pitch - h);
    }

    @WrapOperation(method = "renderItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw(F)F"))
    private float orbiter$wrapHandTiltYaw(Entity entity, float tickProgress, Operation<Float> original) {
        float yaw = original.call(entity, tickProgress);
        if (!orbiter$is360Active()) return yaw;
        ClientPlayerEntity player = (ClientPlayerEntity) entity;
        float i = MathHelper.lerp(tickProgress, player.lastRenderYaw, player.renderYaw);
        return i + MathHelper.wrapDegrees(yaw - i);
    }
}
