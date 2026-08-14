package orbiter.mixin;

import orbiter.modules.render.BeaconOptimizer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(BeaconBlockEntityRenderer.class)
public abstract class BeaconBlockEntityRendererOptimizerMixin {
    @ModifyVariable(
        method = "renderBeam(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/util/Identifier;FFIIIFF)V",
        at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private static float orbiter$throttleAnimationState(float tickProgress) {
        BeaconOptimizer optimizer = Modules.get().get(BeaconOptimizer.class);
        if (optimizer == null || !optimizer.isActive()) return tickProgress;
        return optimizer.quantizeTickProgress(tickProgress);
    }
}
