package orbiter.mixin;

import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerSpoofMixin {
    @Inject(method = "getCurrentGameMode", at = @At("HEAD"), cancellable = true)
    private void orbiter$getCurrentGameMode(CallbackInfoReturnable<GameMode> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.visualCreativeEnabled()) return;

        cir.setReturnValue(GameMode.CREATIVE);
    }

    @Inject(method = "hasStatusBars", at = @At("HEAD"), cancellable = true)
    private void orbiter$hasStatusBars(CallbackInfoReturnable<Boolean> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.visualCreativeEnabled()) return;

        cir.setReturnValue(false);
    }

    @Inject(method = "hasExperienceBar", at = @At("HEAD"), cancellable = true)
    private void orbiter$hasExperienceBar(CallbackInfoReturnable<Boolean> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.visualCreativeEnabled()) return;

        cir.setReturnValue(false);
    }

    @Inject(method = "hasLimitedAttackSpeed", at = @At("HEAD"), cancellable = true)
    private void orbiter$hasLimitedAttackSpeed(CallbackInfoReturnable<Boolean> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.visualCreativeEnabled()) return;

        cir.setReturnValue(false);
    }
}
