package orbiter.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.world.entity.Display;
import net.minecraft.network.chat.Component;
import orbiter.modules.misc.DisplayTextSanitizer;
import orbiter.modules.misc.ServerProtect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Display.TextDisplay.class)
public class CrashFixerTextDisplayMixin {

    @Shadow
    private Display.TextDisplay.CachedInfo clientDisplayCache;
    @Shadow
    private Display.TextDisplay.TextRenderState textRenderState;

    @Inject(method = "cacheDisplay", at = @At("HEAD"), cancellable = true)
    private void orbiter$sanitizeDisplayCache(Display.TextDisplay.LineSplitter splitter,
                                              CallbackInfoReturnable<Display.TextDisplay.CachedInfo> cir) {
        ServerProtect mod = Modules.get() == null ? null : Modules.get().get(ServerProtect.class);
        if (mod == null || !mod.isActive() || !mod.shouldSanitizeTextDisplays()) return;
        if (this.clientDisplayCache != null || this.textRenderState == null) return;

        Component text = this.textRenderState.text();
        if (!DisplayTextSanitizer.shouldSimplify(text,
                mod.getTextDisplayMaxChars(), mod.getTextDisplayMaxNodes(), mod.getTextDisplayMaxDepth(),
                mod.getTextDisplayMaxStyleScore(), mod.getTextDisplayMaxObfuscatedChars(), mod.getTextDisplayMaxComplexNodes())) {
            return;
        }

        this.clientDisplayCache = splitter.split(
            DisplayTextSanitizer.simplifiedText(),
            DisplayTextSanitizer.clampLineWidth(this.textRenderState.lineWidth(), mod.getTextDisplayMaxLineWidth())
        );
        cir.setReturnValue(this.clientDisplayCache);
    }
}
