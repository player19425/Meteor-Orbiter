package orbiter.mixin;

import orbiter.modules.AntiPush;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class FluidPushMixin {
    @Inject(method = "isPushedByFluid", at = @At("HEAD"), cancellable = true)
    private void orbiter$onIsPushedByFluid(CallbackInfoReturnable<Boolean> cir) {
        if (Modules.get() == null) return;

        AntiPush module = Modules.get().get(AntiPush.class);
        if (module == null || !module.isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if ((Object) this != mc.player) return;

        Entity self = (Entity) (Object) this;
        if (module.shouldBlock(FluidTags.WATER) && self.isInWater()) {
            cir.setReturnValue(false);
        } else if (module.shouldBlock(FluidTags.LAVA) && self.isInLava()) {
            cir.setReturnValue(false);
        }
    }
}
