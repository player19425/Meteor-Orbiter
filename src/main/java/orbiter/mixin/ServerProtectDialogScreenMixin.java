package orbiter.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.dialog.DialogConnectionAccess;
import net.minecraft.client.gui.screens.dialog.DialogControlSet;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.Dialog;
import orbiter.modules.misc.ServerProtect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DialogScreen.class)
public abstract class ServerProtectDialogScreenMixin {
    @Shadow
    public abstract Screen previousScreen();

    @Inject(method = "updateHeaderAndFooter(Lnet/minecraft/client/gui/layouts/HeaderAndFooterLayout;Lnet/minecraft/client/gui/screens/dialog/DialogControlSet;Lnet/minecraft/server/dialog/Dialog;Lnet/minecraft/client/gui/screens/dialog/DialogConnectionAccess;)V", at = @At("RETURN"))
    private void orbiter$addEmergencyButton(HeaderAndFooterLayout layout, DialogControlSet controls, Dialog dialog, DialogConnectionAccess access, CallbackInfo ci) {
        ServerProtect module = ServerProtect.get();
        if (module == null || !module.shouldShowDialogEmergencyButton()) return;

        layout.addToHeader(Button.builder(
            Component.literal("Close + block dialogs for 15 min"),
            ignored -> {
                module.suppressDialogs();
                Minecraft.getInstance().gui.setScreen(previousScreen());
            }
        ).width(200).build());
        layout.addToHeader(Button.builder(
            Component.literal("Close once"),
            ignored -> Minecraft.getInstance().gui.setScreen(previousScreen())
        ).width(100).build());
    }
}
