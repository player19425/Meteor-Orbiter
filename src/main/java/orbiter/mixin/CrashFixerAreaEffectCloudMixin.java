package orbiter.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import orbiter.modules.misc.ServerProtect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AreaEffectCloudEntity.class)
public class CrashFixerAreaEffectCloudMixin {

    @Redirect(method = "clientTick",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/AreaEffectCloudEntity;getParticleType()Lnet/minecraft/particle/ParticleEffect;"))
    private ParticleEffect orbiter$replaceElderGuardianParticle(AreaEffectCloudEntity entity) {
        ParticleEffect effect = entity.getParticleType();
        ServerProtect mod = Modules.get() == null ? null : Modules.get().get(ServerProtect.class);
        if (mod == null || !mod.isActive() || !mod.shouldFixElderGuardianParticle()) return effect;
        if (effect == ParticleTypes.ELDER_GUARDIAN) {
            entity.setParticleType(null);
            return ParticleTypes.ELECTRIC_SPARK;
        }
        return effect;
    }
}
