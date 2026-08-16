package orbiter.mixin;

import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.world.item.ItemStack.class)
public abstract class ItemDamageHideMixin {
    @Inject(method = "isDamaged", at = @At("HEAD"), cancellable = true)
    private void orbiter$isDamaged(CallbackInfoReturnable<Boolean> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.hideItemDamageEnabled()) return;

        cir.setReturnValue(false);
    }

    @Inject(method = "isBarVisible", at = @At("HEAD"), cancellable = true)
    private void orbiter$isItemBarVisible(CallbackInfoReturnable<Boolean> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.hideItemDamageEnabled()) return;

        cir.setReturnValue(false);
    }
}
