package orbiter.mixin;

import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import net.minecraft.world.BossEvent;
import net.minecraft.network.chat.Component;
import orbiter.util.LegacyTextFormatter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BossEvent.class)
public abstract class BossBarSpoofMixin {
    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void orbiter$getName(CallbackInfoReturnable<Component> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module != null && module.shouldOverrideBossbar()) {
            String text = module.getBossbarTextOverride();
            if (text != null) cir.setReturnValue(LegacyTextFormatter.parse(text));
        }
    }

    @Inject(method = "getProgress", at = @At("HEAD"), cancellable = true)
    private void orbiter$getPercent(CallbackInfoReturnable<Float> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module != null && module.shouldOverrideBossbarPercent()) cir.setReturnValue(module.getBossbarPercentOverride());
    }

    @Inject(method = "getColor", at = @At("HEAD"), cancellable = true)
    private void orbiter$getColor(CallbackInfoReturnable<BossEvent.BossBarColor> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module != null && module.shouldOverrideBossbarColor()) cir.setReturnValue(module.getBossbarColorOverride());
    }
}
