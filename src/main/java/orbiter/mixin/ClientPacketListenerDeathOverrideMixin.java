package orbiter.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import orbiter.modules.world.DeathOverride;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerDeathOverrideMixin {
    @Inject(method = "handleRespawn(Lnet/minecraft/network/protocol/game/ClientboundRespawnPacket;)V", at = @At("HEAD"), cancellable = true)
    private void orbiter$holdRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        if (DeathOverride.shouldBlockWorldSwap()) ci.cancel();
    }
}
