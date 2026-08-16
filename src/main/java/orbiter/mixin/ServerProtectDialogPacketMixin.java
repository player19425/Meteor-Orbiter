package orbiter.mixin;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import orbiter.modules.misc.ServerProtect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ServerProtectDialogPacketMixin {
    @Inject(
        method = "handleShowDialog(Lnet/minecraft/network/protocol/common/ClientboundShowDialogPacket;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void orbiter$guardDialog(ClientboundShowDialogPacket packet, CallbackInfo ci) {
        ServerProtect module = ServerProtect.get();
        if (module == null || !module.shouldGuardDialogs()) return;
        if (ServerProtect.areDialogsSuppressed() || module.shouldBlockDialog(packet.dialog().value())) ci.cancel();
    }
}
