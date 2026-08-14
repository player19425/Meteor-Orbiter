package orbiter.mixin;

import orbiter.util.ClientSpoofState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossBarHud.class)
public abstract class ClientSideBossBarMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void orbiter$hideBossBars(DrawContext context, CallbackInfo ci) {
        if (ClientSpoofState.module() != null && ClientSpoofState.module().shouldHideAllBossbars()) ci.cancel();
    }
}
