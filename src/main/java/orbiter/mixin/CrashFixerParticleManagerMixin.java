package orbiter.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import orbiter.modules.misc.ServerProtect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleManager.class)
public class CrashFixerParticleManagerMixin {

    @Unique
    private int spawnedThisTick = 0;

    @Inject(method = "tick", at = @At("HEAD"))
    private void orbiter$resetParticleCounter(CallbackInfo ci) {
        this.spawnedThisTick = 0;
    }

    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
    private void orbiter$throttleParticles(Particle particle, CallbackInfo ci) {
        ServerProtect mod = Modules.get() == null ? null : Modules.get().get(ServerProtect.class);
        if (mod == null || !mod.isActive() || !mod.shouldThrottleParticles()) return;
        int max = mod.getMaxParticlesPerTick();
        if (this.spawnedThisTick >= max) {
            ci.cancel();
            return;
        }
        this.spawnedThisTick++;
    }
}
