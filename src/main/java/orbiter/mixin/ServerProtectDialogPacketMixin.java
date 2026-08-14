package orbiter.mixin;

import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.s2c.common.ShowDialogS2CPacket;
import orbiter.modules.misc.ServerProtect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonNetworkHandler.class)
public abstract class ServerProtectDialogPacketMixin {
    @Inject(
        method = "onShowDialog(Lnet/minecraft/network/packet/s2c/common/ShowDialogS2CPacket;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V",
            shift = At.Shift.AFTER
        ),
        cancellable = true
    )
    private void orbiter$guardDialog(ShowDialogS2CPacket packet, CallbackInfo ci) {
        ServerProtect module = ServerProtect.get();
        if (module == null || !module.shouldGuardDialogs()) return;
        if (ServerProtect.areDialogsSuppressed() || module.shouldBlockDialog(packet.dialog().value())) ci.cancel();
    }
}
