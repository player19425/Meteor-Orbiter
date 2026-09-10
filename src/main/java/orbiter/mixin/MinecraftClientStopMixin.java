package orbiter.mixin;

import orbiter.modules.misc.LeaveMessage;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftClientStopMixin {
    @Unique
    private static volatile boolean orbiter$deferredStop = false;

    @Inject(method = "stop", at = @At("HEAD"), cancellable = true)
    private void orbiter$onScheduleStop(CallbackInfo ci) {
        if (Modules.get() == null) return;

        LeaveMessage module = Modules.get().get(LeaveMessage.class);
        if (module == null || !module.isActive()) return;

        if (module.onScheduleStopIntercept()) {
            orbiter$deferredStop = true;
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void orbiter$completeDeferredStop(CallbackInfo ci) {
        if (!orbiter$deferredStop) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null && mc.getConnection() == null) {
            orbiter$deferredStop = false;
            if (LeaveMessage.shouldQuitAfterLeave()) {
                mc.stop();
            }
        }
    }
}
