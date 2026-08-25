package orbiter.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Hud.class)
public abstract class InGameHudHotbarMixin {
    @WrapOperation(
        method = "extractItemHotbar",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractSlot(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/client/DeltaTracker;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;I)V")
    )
    private void orbiter$extractSlot(Hud hud, GuiGraphicsExtractor extractor, int x, int y, DeltaTracker tickCounter, Player player, ItemStack stack, int slot, Operation<Void> original) {
        ItemStack render = stack;
        ClientSideThings module = ClientSpoofState.module();
        if (module != null && module.isFakeHotbarItemsEnabled()) {
            ItemStack spoof = module.getFakeHotbarStack(slot, stack);
            if (spoof != null && !spoof.isEmpty()) {
                ItemStack out = spoof.copy();
                int fallback = stack.isEmpty() ? out.getCount() : stack.getCount();
                int fake = module.getHotbarSpoofCount(slot, fallback);
                int allowed = out.getMaxStackSize() <= 1 ? 1 : module.getMaxFakeHotbarCount();
                out.setCount(Math.min(allowed, Math.max(1, fake)));
                render = out;
            }
        }
        original.call(hud, extractor, x, y, tickCounter, player, render, slot);
    }
}
