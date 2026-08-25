package orbiter.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.util.RandomSource;
import orbiter.modules.misc.ServerProtect;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class CrashFixerClientPacketListenerMixin {

    @Shadow
    private ClientLevel level;
    @Shadow
    @Final
    private RandomSource random;

    @Inject(method = "handleParticleEvent", at = @At("HEAD"), cancellable = true)
    private void orbiter$clampParticlePacket(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        ServerProtect mod = Modules.get() == null ? null : Modules.get().get(ServerProtect.class);
        if (mod == null || !mod.isActive() || !mod.shouldClampParticlePackets()) return;
        int max = mod.getMaxParticlesPerPacket();
        int count = packet.getCount();
        if (count <= max || this.level == null) return;

        for (int i = 0; i < max; i++) {
            double xOff = this.random.nextGaussian() * packet.getXDist();
            double yOff = this.random.nextGaussian() * packet.getYDist();
            double zOff = this.random.nextGaussian() * packet.getZDist();
            double vx = this.random.nextGaussian() * packet.getMaxSpeed();
            double vy = this.random.nextGaussian() * packet.getMaxSpeed();
            double vz = this.random.nextGaussian() * packet.getMaxSpeed();
            try {
                this.level.addParticle(packet.getParticle(), packet.isOverrideLimiter(),
                    packet.alwaysShow(), packet.getX() + xOff, packet.getY() + yOff, packet.getZ() + zOff, vx, vy, vz);
            } catch (Throwable ignored) {
                break;
            }
        }
        ci.cancel();
    }
}
