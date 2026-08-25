package orbiter.mixin;

import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(ItemStack.class)
public abstract class ItemStackSpoofMixin {
    @Inject(method = "getHoverName", at = @At("HEAD"), cancellable = true)
    private void orbiter$getName(CallbackInfoReturnable<Component> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null) return;

        Component global = module.getGlobalFakeName((ItemStack) (Object) this);
        if (global != null) {
            cir.setReturnValue(global);
        }
    }

    @Inject(method = "getTooltipLines", at = @At("RETURN"), cancellable = true)
    private void orbiter$getTooltipLines(Item.TooltipContext context, Player player, TooltipFlag type, CallbackInfoReturnable<List<Component>> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null) return;

        List<Component> lore = module.getGlobalFakeLore((ItemStack) (Object) this);
        if (lore.isEmpty()) return;

        List<Component> tooltip = new ArrayList<>(cir.getReturnValue());
        tooltip.addAll(lore);
        cir.setReturnValue(tooltip);
    }
}
