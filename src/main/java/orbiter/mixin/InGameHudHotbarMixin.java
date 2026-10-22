package orbiter.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import orbiter.modules.misc.ClientSideThings;
import orbiter.util.ClientSpoofState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Gui.class)
public abstract class InGameHudHotbarMixin {
    @WrapOperation(
        require = 0,
        method = "renderItemHotbar",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderSlot(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/client/DeltaTracker;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;I)V")
    )
    private void orbiter$renderSlot(Gui gui, GuiGraphics context, int x, int y, DeltaTracker tickCounter, Player player, ItemStack stack, int slot, Operation<Void> original) {
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
        original.call(gui, context, x, y, tickCounter, player, render, slot);
    }
}
