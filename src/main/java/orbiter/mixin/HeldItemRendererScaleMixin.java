package orbiter.mixin;

import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererScaleMixin {
    @Unique
    private int orbiter$itemScaleDepth;

    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V", at = @At("HEAD"))
    private void orbiter$onRenderItemHead(LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        orbiter$pushScale(matrices);
    }

    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V", at = @At("RETURN"))
    private void orbiter$onRenderItemReturn(LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        orbiter$popScale(matrices);
    }

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void orbiter$onRenderFirstPersonItemHead(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack stack, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        orbiter$pushScale(matrices);
    }

    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
    private void orbiter$onRenderFirstPersonItemReturn(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack stack, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        orbiter$popScale(matrices);
    }

    @Unique
    private void orbiter$pushScale(MatrixStack matrices) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null) return;

        double scale = module.getItemScale();
        if (Math.abs(scale - 1.0) < 1.0e-6) return;

        matrices.push();
        matrices.scale((float) scale, (float) scale, (float) scale);
        orbiter$itemScaleDepth++;
    }

    @Unique
    private void orbiter$popScale(MatrixStack matrices) {
        if (orbiter$itemScaleDepth <= 0) return;
        orbiter$itemScaleDepth--;
        matrices.pop();
    }
}
