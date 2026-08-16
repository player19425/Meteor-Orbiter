package orbiter.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public class ScreenMixin {

    @Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z", at = @At("HEAD"), cancellable = true)
    private void orbiter$onKeyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        Screen self = (Screen) (Object) this;
        if (!(self instanceof AbstractContainerScreen<?> screen)) return;

        Slot hoveredSlot = ((HandledScreenAccessor) screen).getHoveredSlot();
        if (hoveredSlot == null || !hoveredSlot.hasItem()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return;

        KeyMapping pick = mc.options.keyPickItem;
        if (!pick.matches(input)) return;

        if (orbiter.modules.misc.ItemStealer.bypassTrade(hoveredSlot)) {
            cir.setReturnValue(true);
            return;
        }

        if (orbiter.modules.misc.ItemStealer.cloneGuiSlot(hoveredSlot)) {
            cir.setReturnValue(true);
        }
    }
}
