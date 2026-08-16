package orbiter.mixin;

import orbiter.modules.LeaveMessage;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class ClientDisconnectMixin {

    @Inject(method = "disconnectFromWorld(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
    private void onDisconnect(Component reason, CallbackInfo ci) {
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
