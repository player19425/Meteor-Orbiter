package orbiter.mixin;

import orbiter.modules.misc.ItemInfo;
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
public class ItemInfoMixin {
    @Inject(method = "getTooltip", at = @At("RETURN"))
    private void orbiter$appendItemInfo(Item.TooltipContext context, PlayerEntity player, TooltipType type, CallbackInfoReturnable<List<Text>> cir) {
        List<Text> original = cir.getReturnValue();
        if (original == null) return;

        ItemInfo.appendTooltip((ItemStack) (Object) this, original);
    }
}
