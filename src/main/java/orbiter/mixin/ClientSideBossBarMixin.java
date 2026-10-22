package orbiter.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import orbiter.util.ClientSpoofState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class ClientSideBossBarMixin {
    @Inject(method = "renderBossOverlay(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void orbiter$hideBossBars(GuiGraphics context, DeltaTracker tracker, CallbackInfo ci) {
        if (ClientSpoofState.module() != null && ClientSpoofState.module().shouldHideAllBossbars()) ci.cancel();
    }
}
