package orbiter.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import orbiter.modules.misc.ClientSideThings;
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
            switch (style) {
                case Dot -> context.fill(cx - thickness, cy - thickness, cx + thickness + 1, cy + thickness + 1, color);
                case Circle -> {
                    int radius = size + thickness;
                    for (int angle = 0; angle < 64; angle++) {
                        double a = Math.toRadians(angle * (360.0 / 64));
                        int px = cx + (int) Math.round(radius * Math.cos(a));
                        int py = cy + (int) Math.round(radius * Math.sin(a));
                        context.fill(px, py, px + 1, py + 1, color);
                    }
                }
                case Cross, Thin -> {
                    int half = style == ClientSideThings.CrosshairStyle.Thin ? size / 2 : size;
                    int lineThickness = style == ClientSideThings.CrosshairStyle.Thin ? 1 : thickness;
                    context.fill(cx - half, cy - lineThickness, cx + half + 1, cy + lineThickness + 1, color);
                    context.fill(cx - lineThickness, cy - half, cx + lineThickness + 1, cy + half + 1, color);
                }
                default -> {
                    context.fill(cx - size, cy - thickness, cx + size + 1, cy + thickness + 1, color);
                    context.fill(cx - thickness, cy - size, cx + thickness + 1, cy + size + 1, color);
                }
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
