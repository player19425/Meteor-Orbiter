package orbiter.mixin;

import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.world.World.class)
public abstract class WorldWeatherMixin {
    @Inject(method = "getRainGradient", at = @At("HEAD"), cancellable = true)
    private void orbiter$getRainGradient(float tickDelta, CallbackInfoReturnable<Float> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null) return;

        float value = module.getForcedRainGradient();
        if (value >= 0.0f) cir.setReturnValue(value);
    }

    @Inject(method = "getThunderGradient", at = @At("HEAD"), cancellable = true)
    private void orbiter$getThunderGradient(float tickDelta, CallbackInfoReturnable<Float> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null) return;

        float value = module.getForcedThunderGradient();
        if (value >= 0.0f) cir.setReturnValue(value);
    }

    @Inject(method = "isRaining", at = @At("HEAD"), cancellable = true)
    private void orbiter$isRaining(CallbackInfoReturnable<Boolean> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null) return;

        ClientSideThings.WeatherMode mode = module.getWeatherMode();
        if (mode == ClientSideThings.WeatherMode.Clear) cir.setReturnValue(false);
        else if (mode == ClientSideThings.WeatherMode.Rain || mode == ClientSideThings.WeatherMode.Snow) cir.setReturnValue(true);
    }

    @Inject(method = "getTimeOfDay", at = @At("HEAD"), cancellable = true)
    private void orbiter$getTimeOfDay(CallbackInfoReturnable<Long> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.shouldFakeTime()) return;

        cir.setReturnValue(module.getFakeTimeOfDay());
    }
}
