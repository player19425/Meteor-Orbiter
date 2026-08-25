package orbiter.mixin;

import orbiter.modules.LeaveMessage;
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
    @Unique
    private static volatile boolean orbiter$completingStop = false;

    @Inject(method = "stop", at = @At("HEAD"), cancellable = true)
    private void orbiter$onScheduleStop(CallbackInfo ci) {
        if (orbiter$completingStop) return;
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
        Modules modules = Modules.get();
        LeaveMessage module = modules == null ? null : modules.get(LeaveMessage.class);
        boolean sequenceDone = module == null || !module.isActive() || !module.isPendingLeave();

        if ((mc.level == null && mc.getConnection() == null) || sequenceDone) {
            orbiter$deferredStop = false;
            orbiter$completingStop = true;
            try {
                mc.stop();
            } finally {
                orbiter$completingStop = false;
            }
        }
    }
}
