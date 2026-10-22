package orbiter.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.util.RandomSource;
import orbiter.modules.misc.ServerProtect;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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

    @Unique
    private static final int orbiter$MAX_CLAMPED = 512;

    @Inject(method = "handleParticleEvent", at = @At("HEAD"), cancellable = true)
    private void orbiter$clampParticlePacket(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        ServerProtect mod = Modules.get() == null ? null : Modules.get().get(ServerProtect.class);
        if (mod == null || !mod.isActive() || !mod.shouldClampParticlePackets()) return;
        int max = mod.getMaxParticlesPerPacket();
        int count = packet.getCount();
        if (count <= max || this.level == null) return;

        ClientLevel level = this.level;
        RandomSource random = this.random;
        int clamped = Math.min(max, orbiter$MAX_CLAMPED);

        Runnable spawn = () -> {
            try {
                for (int i = 0; i < clamped; i++) {
                    double xOff = random.nextGaussian() * packet.getXDist();
                    double yOff = random.nextGaussian() * packet.getYDist();
                    double zOff = random.nextGaussian() * packet.getZDist();
                    double vx = random.nextGaussian() * packet.getMaxSpeed();
                    double vy = random.nextGaussian() * packet.getMaxSpeed();
                    double vz = random.nextGaussian() * packet.getMaxSpeed();
                    level.addParticle(packet.getParticle(), packet.isOverrideLimiter(),
                        packet.alwaysShow(), packet.getX() + xOff, packet.getY() + yOff, packet.getZ() + zOff, vx, vy, vz);
                }
            } catch (Throwable ignored) {
            }
        };

        Minecraft.getInstance().execute(spawn);
        ci.cancel();
    }
}
