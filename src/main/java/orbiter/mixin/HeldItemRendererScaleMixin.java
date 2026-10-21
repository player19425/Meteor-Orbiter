package orbiter.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import orbiter.modules.misc.ClientSideThings;
import orbiter.util.ClientSpoofState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class HeldItemRendererScaleMixin {
    @WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"), require = 0)
    private void orbiter$onRenderItem(ItemStackRenderState renderState, PoseStack matrices, SubmitNodeCollector collector, int light, int overlay, int outlineColor, Operation<Void> original) {
        ClientSideThings module = ClientSpoofState.module();

        double scale = module == null ? 1.0 : module.getItemScale();
        if (Math.abs(scale - 1.0) < 1.0e-6) {
            original.call(renderState, matrices, collector, light, overlay, outlineColor);
            return;
        }

        matrices.pushPose();
        matrices.scale((float) scale, (float) scale, (float) scale);
        try {
            original.call(renderState, matrices, collector, light, overlay, outlineColor);
        } finally {
            matrices.popPose();
        }
    }
}