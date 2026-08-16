package orbiter.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import orbiter.util.ClientSpoofState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class ClientSideBossBarMixin {
    @Inject(method = "extractBossOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"), cancellable = true)
    private void orbiter$hideBossBars(GuiGraphicsExtractor extractor, DeltaTracker tracker, CallbackInfo ci) {
        if (ClientSpoofState.module() != null && ClientSpoofState.module().shouldHideAllBossbars()) ci.cancel();
    }
}
