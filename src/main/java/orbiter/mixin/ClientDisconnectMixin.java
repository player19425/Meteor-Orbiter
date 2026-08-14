package orbiter.mixin;

import orbiter.modules.LeaveMessage;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class ClientDisconnectMixin {

    @Inject(method = "disconnect(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), cancellable = true)
    private void onDisconnect(Text reason, CallbackInfo ci) {
        if (Modules.get() == null) return;

        LeaveMessage module = Modules.get().get(LeaveMessage.class);
        if (module != null && module.isActive()) {

            String reasonStr = reason != null ? reason.getString() : "";
            if (reasonStr.contains("[LeaveMessage]")) {

                return;
            }

            if (module.onPlayerDisconnect()) {
                ci.cancel();
            }
        }
    }
}
