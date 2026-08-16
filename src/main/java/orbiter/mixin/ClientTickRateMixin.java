package orbiter.mixin;

import orbiter.util.ClientSpoofState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

@Mixin(Minecraft.class)
public abstract class ClientTickRateMixin {
    @ModifyReturnValue(method = "getTickTargetMillis", at = @At("RETURN"))
    private float orbiter$scaleClientTickRate(float original) {
        if (ClientSpoofState.module() == null || !ClientSpoofState.module().shouldSpoofClientTickRate()) return original;
        return original / ClientSpoofState.module().getSpoofClientTickRate();
    }
}
