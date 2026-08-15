package orbiter.mixin;

import orbiter.modules.misc.ServerProtect;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ServerProtectRecipeBookMixin {

    @Inject(method = "handleRecipeBookAdd", at = @At("HEAD"), cancellable = true)
    private void orbiter$sanitizeRecipeBook(ClientboundRecipeBookAddPacket packet, CallbackInfo ci) {
        ServerProtect module = ServerProtect.get();
        if (module == null || !module.isActive()) return;
        if (packet.entries().size() > 1000) {
            ci.cancel();
        }
    }
}
