package orbiter.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.level.material.Fluid;
import orbiter.modules.AntiPush;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityFluidInteraction.class)
public abstract class EntityFluidInteractionMixin {
    @Inject(method = "applyCurrentTo", at = @At("HEAD"), cancellable = true)
    private void orbiter$blockFluidCurrent(TagKey<Fluid> fluidTag, Entity entity, double strength, CallbackInfo ci) {
        if (entity != Minecraft.getInstance().player) return;

        Modules modules = Modules.get();
        AntiPush module = modules == null ? null : modules.get(AntiPush.class);
        if (module == null || !module.isActive()) return;

        if (module.shouldBlock(fluidTag)) ci.cancel();
    }
}
