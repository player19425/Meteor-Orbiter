package orbiter.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemInHandRenderer.class)
public abstract class HeldItemRendererScaleMixin {
    @WrapMethod(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V")
    private void orbiter$onRenderItem(LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext, PoseStack matrices, SubmitNodeCollector collector, int light, Operation<Void> original) {
        ClientSideThings module = ClientSpoofState.module();

        double scale = module == null ? 1.0 : module.getItemScale();
        if (Math.abs(scale - 1.0) < 1.0e-6) {
            original.call(entity, stack, displayContext, matrices, collector, light);
            return;
        }

        matrices.pushPose();
        matrices.scale((float) scale, (float) scale, (float) scale);
        try {
            original.call(entity, stack, displayContext, matrices, collector, light);
        } finally {
            matrices.popPose();
        }
    }
}
