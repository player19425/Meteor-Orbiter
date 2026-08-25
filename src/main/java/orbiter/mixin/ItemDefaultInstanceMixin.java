package orbiter.mixin;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class ItemDefaultInstanceMixin {
    @Inject(method = "getDefaultInstance", at = @At("HEAD"), cancellable = true)
    private void orbiter$safeDefaultInstance(CallbackInfoReturnable<ItemStack> cir) {
        try {
            cir.setReturnValue(new ItemStack((ItemLike) (Object) this));
        } catch (RuntimeException e) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
