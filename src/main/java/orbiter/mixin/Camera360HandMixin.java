package orbiter.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.Mth;
import orbiter.modules.render.Camera360;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemInHandRenderer.class)
public abstract class Camera360HandMixin {

    private boolean orbiter$is360Active() {
        Camera360 mod = (Camera360) Modules.get().get("360-camera");
        return mod != null && mod.isActive();
    }

    @WrapOperation(method = "submitHandsWithItems", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getXRot(F)F"))
    private float orbiter$wrapHandTiltPitch(LocalPlayer player, float tickProgress, Operation<Float> original) {
        float pitch = original.call(player, tickProgress);
        if (!orbiter$is360Active()) return pitch;
        float h = player.getXRot(tickProgress);
        return h + Mth.wrapDegrees(pitch - h);
    }
}
