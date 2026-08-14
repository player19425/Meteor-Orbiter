package orbiter.mixin;

import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DrawContext.class)
public abstract class DrawContextItemScaleMixin {
    @Unique
    private int orbiter$scaleDepth;

    @Inject(method = "drawItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;III)V", at = @At("HEAD"))
    private void orbiter$drawItemHead(LivingEntity entity, ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null) return;

        double scale = module.getItemScale();
        if (Math.abs(scale - 1.0) < 1.0e-6) return;

        DrawContext self = (DrawContext) (Object) this;
        Matrix3x2fStack matrices = self.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x + 8.0f, y + 8.0f);
        matrices.scale((float) scale, (float) scale);
        matrices.translate(-(x + 8.0f), -(y + 8.0f));
        orbiter$scaleDepth++;
    }

    @Inject(method = "drawItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;III)V", at = @At("RETURN"))
    private void orbiter$drawItemReturn(LivingEntity entity, ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        if (orbiter$scaleDepth <= 0) return;

        DrawContext self = (DrawContext) (Object) this;
        self.getMatrices().popMatrix();
        orbiter$scaleDepth--;
    }
}
