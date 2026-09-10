package orbiter.mixin;

import net.minecraft.client.gui.screens.dialog.DialogScreen;
import orbiter.modules.misc.AutoLogin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DialogScreen.class)
public abstract class DialogScreenAutoLoginMixin {
    @Inject(method = "init()V", at = @At("TAIL"))
    private void orbiter$onDialogControlsReady(CallbackInfo ci) {
        DialogScreen<?> screen = (DialogScreen<?>) (Object) this;
        AutoLogin.onDialogLayout(screen);
    }
}
