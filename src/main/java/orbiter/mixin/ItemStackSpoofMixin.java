package orbiter.mixin;

import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(ItemStack.class)
public abstract class ItemStackSpoofMixin {
    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void orbiter$getName(CallbackInfoReturnable<Text> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null) return;

        ItemStack stack = (ItemStack) (Object) this;
        Text mapped = ClientSpoofState.getFakeName(stack);
        if (mapped != null) {
            cir.setReturnValue(mapped);
            return;
        }

        Text global = module.getGlobalFakeName(stack);
        if (global != null) {
            cir.setReturnValue(global);
        }
    }

    @Inject(method = "getTooltip", at = @At("RETURN"), cancellable = true)
    private void orbiter$getTooltip(Item.TooltipContext context, PlayerEntity player, TooltipType type, CallbackInfoReturnable<List<Text>> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null) return;

        ItemStack stack = (ItemStack) (Object) this;

        List<Text> lore = ClientSpoofState.getFakeLore(stack);
        if (lore.isEmpty()) lore = module.getGlobalFakeLore(stack);
        if (lore.isEmpty()) return;

        List<Text> tooltip = new ArrayList<>(cir.getReturnValue());
        tooltip.addAll(lore);
        cir.setReturnValue(tooltip);
    }
}
