package orbiter.mixin;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import orbiter.commands.HideKeybindCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.blamejared.controlling.client.NewKeyBindsList")
public abstract class ControllingNewKeyBindsListMixin {
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
