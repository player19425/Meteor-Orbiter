package orbiter.mixin;

import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.server.dialog.Dialog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DialogScreen.class)
public interface DialogScreenAccessor {
    @Accessor(value = "dialog")
    Dialog orbiter$getDialog();

    @Accessor(value = "layout")
    HeaderAndFooterLayout orbiter$getLayout();
}
