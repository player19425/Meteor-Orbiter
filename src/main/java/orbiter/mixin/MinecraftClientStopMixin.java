package orbiter.mixin;

import orbiter.modules.LeaveMessage;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientStopMixin {
    @Inject(method = "scheduleStop", at = @At("HEAD"), cancellable = true)
    private void orbiter$onScheduleStop(CallbackInfo ci) {
        if (Modules.get() == null) return;

        LeaveMessage module = Modules.get().get(LeaveMessage.class);
        if (module == null || !module.isActive()) return;

        if (module.onScheduleStopIntercept()) {
            ci.cancel();
        }
    }
}
