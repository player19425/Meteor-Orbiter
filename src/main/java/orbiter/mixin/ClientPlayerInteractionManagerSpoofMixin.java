package orbiter.mixin;

import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class ClientPlayerInteractionManagerSpoofMixin {
    @Inject(method = "getPlayerMode", at = @At("HEAD"), cancellable = true)
    private void orbiter$getCurrentGameMode(CallbackInfoReturnable<GameType> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.visualCreativeEnabled()) return;

        cir.setReturnValue(GameType.CREATIVE);
    }

    @Inject(method = "hasExperience", at = @At("HEAD"), cancellable = true)
    private void orbiter$hasExperienceBar(CallbackInfoReturnable<Boolean> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.visualCreativeEnabled()) return;

        cir.setReturnValue(false);
    }

}
