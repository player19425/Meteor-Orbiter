package orbiter.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.chat.Component;
import orbiter.modules.misc.EntityNameSanitizer;
import orbiter.modules.misc.ServerProtect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class CrashFixerEntityNameMixin {

    @Inject(method = "getName", at = @At("RETURN"), cancellable = true)
    private void orbiter$simplifyLaggyName(CallbackInfoReturnable<Component> cir) {
        ServerProtect mod = Modules.get() == null ? null : Modules.get().get(ServerProtect.class);
        if (mod == null || !mod.isActive() || !mod.shouldSanitizeEntityNames()) return;
        Component name = cir.getReturnValue();
        if (name == null) return;
        if (EntityNameSanitizer.shouldSimplify(name,
                mod.getNameMaxChars(), mod.getNameMaxNodes(), mod.getNameMaxDepth(),
                mod.getNameMaxStyleScore(), mod.getNameMaxObfuscatedChars(), mod.getNameMaxComplexNodes())) {
            cir.setReturnValue(EntityNameSanitizer.simplify((Entity) (Object) this));
        }
    }
}
