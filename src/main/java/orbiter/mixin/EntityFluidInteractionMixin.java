package orbiter.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import orbiter.modules.movement.AntiPush;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Entity.class)
public abstract class EntityFluidInteractionMixin {
    @Redirect(
        method = "updateFluidHeightAndDoFluidPushing",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"),
        require = 1
    )
    private void orbiter$blockFluidCurrent(Entity entity, Vec3 movement, @Local(argsOnly = true) TagKey<Fluid> fluidTag) {
        if (entity == Minecraft.getInstance().player) {
            Modules modules = Modules.get();
            AntiPush module = modules == null ? null : modules.get(AntiPush.class);
            if (module != null && module.isActive() && module.shouldBlock(fluidTag)) return;
        }

        entity.setDeltaMovement(movement);
    }
}
