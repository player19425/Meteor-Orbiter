package orbiter.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class ClientSideHudMixin {
    @Inject(method = "extractCrosshair(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"), cancellable = true)
    private void orbiter$renderCrosshair(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.isCrosshairOverrideActive()) return;

        int cx = context.guiWidth() / 2;
        int cy = context.guiHeight() / 2;
        ClientSideThings.CrosshairStyle style = module.getCrosshairStyle();
        if (style != ClientSideThings.CrosshairStyle.None) {
            int size = Math.max(2, (int) Math.round(4 * module.getCrosshairScale()));
            int thickness = Math.max(1, module.getCrosshairThickness());
            int color = 0xFFFFFFFF;
            if (style == ClientSideThings.CrosshairStyle.Dot || style == ClientSideThings.CrosshairStyle.Circle) {
                context.fill(cx - thickness, cy - thickness, cx + thickness + 1, cy + thickness + 1, color);
            } else {
                context.fill(cx - size, cy - thickness, cx + size + 1, cy + thickness + 1, color);
                context.fill(cx - thickness, cy - size, cx + thickness + 1, cy + size + 1, color);
            }
        }
        ci.cancel();
    }

    @WrapMethod(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V")
    private void orbiter$hudRenderScope(GuiGraphicsExtractor context, DeltaTracker tickCounter, Operation<Void> original) {
        ClientSpoofState.pushHudRenderScope();
        try {
            original.call(context, tickCounter);
        } finally {
            ClientSpoofState.popHudRenderScope();
        }
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("RETURN"))
    private void orbiter$renderFakeDeath(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.isShowingFakeDeath()) return;
        int alpha = Math.max(0, Math.min(255, Math.round(module.getFakeDeathAlpha() * module.getFakeDeathBgOpacity())));
        context.fill(0, 0, context.guiWidth(), context.guiHeight(), alpha << 24);
        Font renderer = Minecraft.getInstance().font;
        String title = module.getFakeDeathMessageText();
        context.text(renderer, Component.literal(title), context.guiWidth() / 2 - renderer.width(title) / 2, context.guiHeight() / 2 - 20, 0xFFFFFFFF);
        String hint = "Press the module toggle to dismiss";
        context.text(renderer, Component.literal(hint), context.guiWidth() / 2 - renderer.width(hint) / 2, context.guiHeight() / 2 + 4, 0xFFAAAAAA);
    }
}
