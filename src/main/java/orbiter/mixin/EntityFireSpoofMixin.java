package orbiter.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityFireSpoofMixin {
    @Inject(method = "isOnFire", at = @At("HEAD"), cancellable = true)
    private void orbiter$isOnFire(CallbackInfoReturnable<Boolean> cir) {
        ClientSideThings module = ClientSpoofState.module();
        Minecraft client = Minecraft.getInstance();
        if (module != null && client.player != null && (Object) this == client.player && module.shouldSpoofBurning()) {
            cir.setReturnValue(true);
        }
    }
}
