package orbiter.mixin;

import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityVisualSpoofMixin {
    @Inject(method = "isInCreativeMode", at = @At("HEAD"), cancellable = true)
    private void orbiter$isInCreativeMode(CallbackInfoReturnable<Boolean> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.visualCreativeEnabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if ((Object) this == mc.player) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isCreative", at = @At("HEAD"), cancellable = true)
    private void orbiter$isCreative(CallbackInfoReturnable<Boolean> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.visualCreativeEnabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if ((Object) this == mc.player) {
            cir.setReturnValue(true);
        }
    }
}
