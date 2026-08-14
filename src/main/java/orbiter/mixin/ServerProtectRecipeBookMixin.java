package orbiter.mixin;

import orbiter.modules.misc.ServerProtect;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.RecipeBookAddS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ServerProtectRecipeBookMixin {

    @Inject(method = "onRecipeBookAdd", at = @At("HEAD"), cancellable = true)
    private void orbiter$sanitizeRecipeBook(RecipeBookAddS2CPacket packet, CallbackInfo ci) {
        ServerProtect module = ServerProtect.get();
        if (module == null || !module.isActive()) return;
        if (packet.entries().size() > 1000) {
            ci.cancel();
        }
    }
}
