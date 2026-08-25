package orbiter.mixin;

import orbiter.modules.ClientSideThings;
import orbiter.modules.misc.ItemStealer;
import orbiter.util.ClientSpoofState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenMixin {
    @Shadow protected Slot hoveredSlot;

    @Inject(method = "mouseScrolled(DDDD)Z", at = @At("HEAD"), cancellable = true)
    private void orbiter$onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.isFakeCountEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!mc.options.keyShift.isDown()) return;
        if (hoveredSlot == null || !hoveredSlot.hasItem()) return;

        int hotbarSlot = orbiter$resolveHotbarSlot(hoveredSlot);
        if (hotbarSlot < 0) return;

        int sign = verticalAmount > 0 ? 1 : (verticalAmount < 0 ? -1 : 0);
        if (sign == 0) return;

        int delta = module.getCountEditStep() * sign;
        module.adjustHotbarCount(hotbarSlot, delta);
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z", at = @At("HEAD"), cancellable = true)
    private void orbiter$onMouseClicked(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (hoveredSlot == null || !hoveredSlot.hasItem()) return;

        if (ItemStealer.bypassTrade(hoveredSlot)) {
            cir.setReturnValue(true);
            return;
        }

        if (click.button() == 0) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.options.keyShift.isDown()) {

                if (hoveredSlot != null) {
                    int slotIndex = hoveredSlot.index;
                    boolean shouldCancel = ItemStealer.onShiftClickSlot(
                        slotIndex, 0, ContainerInput.QUICK_MOVE
                    );
                    if (shouldCancel) {
                        cir.setReturnValue(true);
                        return;
                    }
                }
            }
        }

        if (click.button() != 2) {

            if (click.button() == 1 && ItemStealer.isRightClickCloneEnabled()) {
                if (ItemStealer.cloneGuiSlot(hoveredSlot)) {
                    cir.setReturnValue(true);
                    return;
                }
            }

            if (click.button() != 2) return;
        }

        if (ItemStealer.cloneGuiSlot(hoveredSlot)) {
            cir.setReturnValue(true);
            return;
        }

        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.isFakeCountEnabled()) return;

        int hotbarSlot = orbiter$resolveHotbarSlot(hoveredSlot);
        if (hotbarSlot < 0) return;

        module.setHotbarCount(hotbarSlot, module.getMaxFakeHotbarCount());
        cir.setReturnValue(true);
    }

    private int orbiter$resolveHotbarSlot(Slot slot) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || slot == null) return -1;
        if (!(slot.container instanceof Inventory)) return -1;

        int containerIndex = slot.getContainerSlot();
        if (containerIndex >= 0 && containerIndex < 9) return containerIndex;
        return -1;
    }
}
