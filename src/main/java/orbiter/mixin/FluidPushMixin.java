package orbiter.mixin;

import orbiter.modules.AntiPush;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.tag.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class FluidPushMixin {
    @Inject(method = "updateMovementInFluid", at = @At("HEAD"), cancellable = true)
    private void orbiter$onUpdateMovementInFluid(TagKey<Fluid> fluidTag, double speed, CallbackInfoReturnable<Boolean> cir) {
        if (Modules.get() == null) return;

        AntiPush module = Modules.get().get(AntiPush.class);
        if (module == null || !module.isActive()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if ((Object) this != mc.player) return;

        if (module.shouldBlock(fluidTag)) {
            cir.setReturnValue(false);
        }
    }
}
