package orbiter.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.dialog.DialogScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.text.Text;
import orbiter.modules.misc.ServerProtect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DialogScreen.class)
public abstract class ServerProtectDialogScreenMixin {
    @Shadow
    public abstract Screen getParentScreen();

    @Inject(method = "createHeader()Lnet/minecraft/client/gui/widget/Widget;", at = @At("RETURN"))
    private void orbiter$addEmergencyButton(CallbackInfoReturnable<Widget> cir) {
        ServerProtect module = ServerProtect.get();
        if (module == null || !module.shouldShowDialogEmergencyButton()) return;
        if (!(cir.getReturnValue() instanceof DirectionalLayoutWidget header)) return;

        ButtonWidget button = ButtonWidget.builder(
            Text.literal("Close + block dialogs for 15 min"),
            ignored -> {
                module.suppressDialogs();
                MinecraftClient.getInstance().setScreen(getParentScreen());
            }
        ).width(200).build();
        header.add(button);
        header.add(ButtonWidget.builder(
            Text.literal("Close once"),
            ignored -> MinecraftClient.getInstance().setScreen(getParentScreen())
        ).width(100).build());
    }
}
