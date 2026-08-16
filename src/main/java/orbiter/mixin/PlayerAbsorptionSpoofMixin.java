package orbiter.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerAbsorptionSpoofMixin {
    @Inject(method = "getAbsorptionAmount", at = @At("HEAD"), cancellable = true)
    private void orbiter$getAbsorptionAmount(CallbackInfoReturnable<Float> cir) {
        ClientSideThings module = ClientSpoofState.module();
        Minecraft client = Minecraft.getInstance();
        if (module != null && module.shouldFakeAbsorption() && client.player != null && (Object) this == client.player) {
            cir.setReturnValue(module.getFakeAbsorption());
        }
    }
}
