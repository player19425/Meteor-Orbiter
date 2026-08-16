package orbiter.mixin;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import orbiter.commands.HideKeybindCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(KeyBindsList.class)
public abstract class ControlsListWidgetMixin {
    @Redirect(
        method = "<init>",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/Options;keyMappings:[Lnet/minecraft/client/KeyMapping;"
        )
    )
    private static KeyMapping[] orbiter$filterKeybindings(Options options) {
        return HideKeybindCommand.filterKeys(options.keyMappings);
    }
}
