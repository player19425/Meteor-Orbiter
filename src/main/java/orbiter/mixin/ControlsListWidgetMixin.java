package orbiter.mixin;

import net.minecraft.client.gui.screen.option.ControlsListWidget;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import orbiter.commands.HideKeybindCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ControlsListWidget.class)
public abstract class ControlsListWidgetMixin {
    @Redirect(
        method = "update",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/option/GameOptions;allKeys:[Lnet/minecraft/client/option/KeyBinding;"
        )
    )
    private static KeyBinding[] orbiter$filterKeybindings(GameOptions options) {
        return HideKeybindCommand.filterKeys(options.allKeys);
    }
}
