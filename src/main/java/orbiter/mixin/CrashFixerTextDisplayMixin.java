package orbiter.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.text.Text;
import orbiter.modules.misc.DisplayTextSanitizer;
import orbiter.modules.misc.ServerProtect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DisplayEntity.TextDisplayEntity.class)
public class CrashFixerTextDisplayMixin {

    @Shadow
    private DisplayEntity.TextDisplayEntity.TextLines textLines;
    @Shadow
    private DisplayEntity.TextDisplayEntity.Data data;

    @Inject(method = "splitLines", at = @At("HEAD"), cancellable = true)
    private void orbiter$sanitizeDisplayCache(DisplayEntity.TextDisplayEntity.LineSplitter splitter,
                                              CallbackInfoReturnable<DisplayEntity.TextDisplayEntity.TextLines> cir) {
        ServerProtect mod = Modules.get() == null ? null : Modules.get().get(ServerProtect.class);
        if (mod == null || !mod.isActive() || !mod.shouldSanitizeTextDisplays()) return;
        if (this.textLines != null || this.data == null) return;

        Text text = this.data.text();
        if (!DisplayTextSanitizer.shouldSimplify(text,
                mod.getTextDisplayMaxChars(), mod.getTextDisplayMaxNodes(), mod.getTextDisplayMaxDepth(),
                mod.getTextDisplayMaxStyleScore(), mod.getTextDisplayMaxObfuscatedChars(), mod.getTextDisplayMaxComplexNodes())) {
            return;
        }

        this.textLines = splitter.split(
            DisplayTextSanitizer.simplifiedText(),
            DisplayTextSanitizer.clampLineWidth(this.data.lineWidth(), mod.getTextDisplayMaxLineWidth())
        );
        cir.setReturnValue(this.textLines);
    }
}
