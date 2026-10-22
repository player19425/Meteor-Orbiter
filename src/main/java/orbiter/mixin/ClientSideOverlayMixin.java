package orbiter.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import orbiter.modules.misc.ClientSideThings;
import orbiter.util.ClientSpoofState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ScreenEffectRenderer.class)
public abstract class ClientSideOverlayMixin {
    @WrapOperation(
        require = 1,
        method = "submit",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;submitFire(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V")
    )
    private void orbiter$renderFireOverlay(PoseStack matrices, SubmitNodeCollector collector, TextureAtlasSprite sprite, Operation<Void> original) {
        ClientSideThings module = ClientSpoofState.module();

        if (module != null && module.shouldForceOffFireOverlay()) return;

        if (module != null && module.shouldForceFireOverlay()) {
            float height = (float) module.getFireOverlayHeight();
            if (height <= 0.0f) return;
            matrices.pushPose();
            matrices.scale(1.0f, height, 1.0f);
            original.call(matrices, collector, sprite);
            matrices.popPose();
            return;
        }

        original.call(matrices, collector, sprite);
    }

    @WrapOperation(
        require = 1,
        method = "submit",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;submitWater(Lnet/minecraft/client/renderer/state/level/PlayerRenderState$WaterOverlay;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V")
    )
    private void orbiter$renderUnderwaterOverlay(PlayerRenderState.WaterOverlay overlay, PoseStack matrices, SubmitNodeCollector collector, Operation<Void> original) {
        ClientSideThings module = ClientSpoofState.module();
        if (module != null && module.shouldForceOffWaterOverlay()) return;
        original.call(overlay, matrices, collector);
    }
}
