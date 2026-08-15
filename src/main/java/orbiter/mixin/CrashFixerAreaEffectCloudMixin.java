package orbiter.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import orbiter.modules.misc.ServerProtect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AreaEffectCloud.class)
public class CrashFixerAreaEffectCloudMixin {

    @Redirect(method = "clientTick",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/AreaEffectCloud;getParticle()Lnet/minecraft/core/particles/ParticleOptions;"))
    private ParticleOptions orbiter$replaceElderGuardianParticle(AreaEffectCloud entity) {
        ParticleOptions effect = entity.getParticle();
        ServerProtect mod = Modules.get() == null ? null : Modules.get().get(ServerProtect.class);
        if (mod == null || !mod.isActive() || !mod.shouldFixElderGuardianParticle()) return effect;
        if (effect == ParticleTypes.ELDER_GUARDIAN) {
            entity.setCustomParticle(null);
            return ParticleTypes.ELECTRIC_SPARK;
        }
        return effect;
    }
}
