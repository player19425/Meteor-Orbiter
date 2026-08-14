package orbiter.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(InGameOverlayRenderer.class)
public abstract class ClientSideOverlayMixin {
    @WrapOperation(
        method = "renderOverlays",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameOverlayRenderer;renderFireOverlay(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/texture/Sprite;)V")
    )
    private void orbiter$renderFireOverlay(MatrixStack matrices, VertexConsumerProvider consumers, Sprite sprite, Operation<Void> original) {
        ClientSideThings module = ClientSpoofState.module();
        MinecraftClient client = MinecraftClient.getInstance();

        if (module != null && module.shouldForceOffFireOverlay()) return;

        if (module != null && module.shouldForceFireOverlay() && module.shouldSpoofBurning()) {
            float height = (float) module.getFireOverlayHeight();
            if (height <= 0.0f) return;
            matrices.push();
            matrices.scale(1.0f, height, 1.0f);
            original.call(matrices, consumers, sprite);
            matrices.pop();
            return;
        }

        if (client.player != null && !client.player.isOnFire()) return;

        if (module != null && module.shouldForceFireOverlay()) {
            float height = (float) module.getFireOverlayHeight();
            if (height <= 0.0f) return;
            matrices.push();
            matrices.scale(1.0f, height, 1.0f);
            original.call(matrices, consumers, sprite);
            matrices.pop();
            return;
        }

        original.call(matrices, consumers, sprite);
    }

    @WrapOperation(
        method = "renderOverlays",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameOverlayRenderer;renderUnderwaterOverlay(Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V")
    )
    private void orbiter$renderUnderwaterOverlay(MinecraftClient client, MatrixStack matrices, VertexConsumerProvider consumers, Operation<Void> original) {
        ClientSideThings module = ClientSpoofState.module();
        if (module != null && module.shouldForceOffWaterOverlay()) return;
        original.call(client, matrices, consumers);
    }
}
