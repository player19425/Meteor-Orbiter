package orbiter.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import orbiter.systems.combat.CombatEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class CameraFreezeMixin {
    @Inject(method = "turn(DD)V", at = @At("HEAD"), cancellable = true)
    private void orbiter$freezeLook(double yawDelta, double pitchDelta, CallbackInfo ci) {
        if (!((Object) this instanceof LocalPlayer)) return;
        if (CombatEngine.get().isFrozen()) ci.cancel();
    }
}
