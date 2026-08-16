package orbiter.mixin;

import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerEntityVisualSpoofMixin {
    @Inject(method = "isCreative", at = @At("HEAD"), cancellable = true)
    private void orbiter$isCreative(CallbackInfoReturnable<Boolean> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.visualCreativeEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if ((Object) this == mc.player) {
            cir.setReturnValue(true);
        }
    }
}
