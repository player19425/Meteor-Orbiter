package orbiter.mixin;

import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class ClientSideHudMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void orbiter$renderCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.isCrosshairOverrideActive()) return;

        int cx = context.getScaledWindowWidth() / 2;
        int cy = context.getScaledWindowHeight() / 2;
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

    @Inject(method = "render", at = @At("RETURN"))
    private void orbiter$renderFakeDeath(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.isShowingFakeDeath()) return;
        int alpha = Math.max(0, Math.min(255, Math.round(module.getFakeDeathAlpha() * module.getFakeDeathBgOpacity())));
        context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), alpha << 24);
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        context.drawCenteredTextWithShadow(renderer, Text.literal(module.getFakeDeathMessageText()), context.getScaledWindowWidth() / 2, context.getScaledWindowHeight() / 2 - 20, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(renderer, Text.literal("Press the module toggle to dismiss"), context.getScaledWindowWidth() / 2, context.getScaledWindowHeight() / 2 + 4, 0xFFAAAAAA);
    }
}
