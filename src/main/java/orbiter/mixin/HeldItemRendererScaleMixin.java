package orbiter.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class HeldItemRendererScaleMixin {
    @Unique
    private int orbiter$itemScaleDepth;

    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V", at = @At("HEAD"))
    private void orbiter$onRenderItemHead(LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext, PoseStack matrices, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        orbiter$pushScale(matrices);
    }

    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V", at = @At("RETURN"))
    private void orbiter$onRenderItemReturn(LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext, PoseStack matrices, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        orbiter$popScale(matrices);
    }

    @Unique
    private void orbiter$pushScale(PoseStack matrices) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null) return;

        double scale = module.getItemScale();
        if (Math.abs(scale - 1.0) < 1.0e-6) return;

        matrices.pushPose();
        matrices.scale((float) scale, (float) scale, (float) scale);
        orbiter$itemScaleDepth++;
    }

    @Unique
    private void orbiter$popScale(PoseStack matrices) {
        if (orbiter$itemScaleDepth <= 0) return;
        orbiter$itemScaleDepth--;
        matrices.popPose();
    }
}
