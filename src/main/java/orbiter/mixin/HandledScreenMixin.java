package orbiter.mixin;

import orbiter.modules.ClientSideThings;
import orbiter.modules.misc.ItemStealer;
import orbiter.util.ClientSpoofState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.entity.player.PlayerInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    @Shadow protected Slot focusedSlot;

    @Inject(method = "mouseScrolled(DDDD)Z", at = @At("HEAD"), cancellable = true)
    private void orbiter$onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.isFakeCountEnabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (!mc.options.sneakKey.isPressed()) return;
        if (focusedSlot == null || !focusedSlot.hasStack()) return;

        int hotbarSlot = orbiter$resolveHotbarSlot(focusedSlot);
        if (hotbarSlot < 0) return;

        int sign = verticalAmount > 0 ? 1 : (verticalAmount < 0 ? -1 : 0);
        if (sign == 0) return;

        int delta = module.getCountEditStep() * sign;
        module.adjustHotbarCount(hotbarSlot, delta);
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseClicked(Lnet/minecraft/client/gui/Click;Z)Z", at = @At("HEAD"), cancellable = true)
    private void orbiter$onMouseClicked(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (focusedSlot == null || !focusedSlot.hasStack()) return;

        if (ItemStealer.bypassTrade(focusedSlot)) {
            cir.setReturnValue(true);
            return;
        }

        if (click.button() == 0) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null && mc.options.sneakKey.isPressed()) {

                if (focusedSlot != null) {
                    int slotIndex = focusedSlot.id;
                    boolean shouldCancel = ItemStealer.onShiftClickSlot(
                        slotIndex, 0, SlotActionType.QUICK_MOVE
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
                if (ItemStealer.cloneGuiSlot(focusedSlot)) {
                    cir.setReturnValue(true);
                    return;
                }
            }

            if (click.button() != 2) return;
        }

        if (ItemStealer.cloneGuiSlot(focusedSlot)) {
            cir.setReturnValue(true);
            return;
        }

        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.isFakeCountEnabled()) return;

        int hotbarSlot = orbiter$resolveHotbarSlot(focusedSlot);
        if (hotbarSlot < 0) return;

        module.setHotbarCount(hotbarSlot, module.getMaxFakeHotbarCount());
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased(Lnet/minecraft/client/gui/Click;)Z", at = @At("HEAD"), cancellable = true)
    private void orbiter$onMouseReleased(Click click, CallbackInfoReturnable<Boolean> cir) {
        if (focusedSlot == null || !focusedSlot.hasStack()) return;

        if (click.button() == 0) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null && mc.options.sneakKey.isPressed()) {
                int slotIndex = focusedSlot.id;
                boolean shouldCancel = ItemStealer.onShiftClickSlot(
                    slotIndex, 0, SlotActionType.QUICK_MOVE
                );
                if (shouldCancel) {
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }

    private int orbiter$resolveHotbarSlot(Slot slot) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || slot == null) return -1;

        if (slot.inventory instanceof PlayerInventory) {
            int index = slot.getIndex();
            if (index >= 0 && index < 9) return index;
        }

        if (slot.id >= 36 && slot.id <= 44) return slot.id - 36;
        return -1;
    }
}
