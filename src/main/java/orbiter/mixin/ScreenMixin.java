package orbiter.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public class ScreenMixin {

    @Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyInput;)Z", at = @At("HEAD"), cancellable = true)
    private void orbiter$onKeyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        Screen self = (Screen) (Object) this;
        if (!(self instanceof HandledScreen<?> screen)) return;

        Slot focusedSlot = ((HandledScreenAccessor) screen).getFocusedSlot();
        if (focusedSlot == null || !focusedSlot.hasStack()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options == null) return;

        KeyBinding pick = mc.options.pickItemKey;
        if (!pick.matchesKey(input)) return;

        if (orbiter.modules.misc.ItemStealer.bypassTrade(focusedSlot)) {
            cir.setReturnValue(true);
            return;
        }

        if (orbiter.modules.misc.ItemStealer.cloneGuiSlot(focusedSlot)) {
            cir.setReturnValue(true);
        }
    }
}
