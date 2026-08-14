package orbiter.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import orbiter.modules.misc.ServerProtect;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(ClientPlayNetworkHandler.class)
public class CrashFixerClientPacketListenerMixin {

    @Shadow
    private ClientWorld world;
    @Shadow
    @Final
    private Random random;

    @Unique
    private static final Map<Integer, Integer> trackedEntityCounts = new HashMap<>();

    @Inject(method = "onParticle", at = @At("HEAD"), cancellable = true)
    private void orbiter$clampParticlePacket(ParticleS2CPacket packet, CallbackInfo ci) {
        ServerProtect mod = Modules.get() == null ? null : Modules.get().get(ServerProtect.class);
        if (mod == null || !mod.isActive() || !mod.shouldClampParticlePackets()) return;
        int max = mod.getMaxParticlesPerPacket();
        int count = packet.getCount();
        if (count <= max || this.world == null) return;

        for (int i = 0; i < max; i++) {
            double xOff = this.random.nextGaussian() * packet.getOffsetX();
            double yOff = this.random.nextGaussian() * packet.getOffsetY();
            double zOff = this.random.nextGaussian() * packet.getOffsetZ();
            double vx = this.random.nextGaussian() * packet.getSpeed();
            double vy = this.random.nextGaussian() * packet.getSpeed();
            double vz = this.random.nextGaussian() * packet.getSpeed();
            try {
                this.world.addParticleClient(packet.getParameters(), packet.shouldForceSpawn(),
                    packet.isImportant(), packet.getX() + xOff, packet.getY() + yOff, packet.getZ() + zOff, vx, vy, vz);
            } catch (Throwable ignored) {
                break;
            }
        }
        ci.cancel();
    }

    @Inject(method = "clearWorld", at = @At("HEAD"))
    private void orbiter$clearTrackedEntities(CallbackInfo ci) {
        trackedEntityCounts.clear();
    }

    @Inject(method = "onEntitiesDestroy", at = @At("HEAD"))
    private void orbiter$forgetRemovedEntities(EntitiesDestroyS2CPacket packet, CallbackInfo ci) {
        var it = packet.getEntityIds().iterator();
        while (it.hasNext()) {
            int entityId = it.nextInt();
            trackedEntityCounts.remove(entityId);
        }
    }
}
