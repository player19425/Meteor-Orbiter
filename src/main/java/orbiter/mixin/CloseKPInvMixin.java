package orbiter.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import orbiter.modules.player.CloseKPInv;

@Mixin(ScreenHandler.class)
public abstract class CloseKPInvMixin {

    @Inject(method = "onClosed", at = @At("HEAD"), cancellable = true)
    private void closekpinv$skipDrain(PlayerEntity player, CallbackInfo ci) {
        if (!(player instanceof ClientPlayerEntity)) return;

        CloseKPInv mod = CloseKPInv.get();
        if (mod == null || !mod.isActive()) return;

        ScreenHandler self = (ScreenHandler) (Object) this;
        ClientPlayerEntity localPlayer = (ClientPlayerEntity) player;
        if (localPlayer.playerScreenHandler != self) return;

        ci.cancel();
    }
}
