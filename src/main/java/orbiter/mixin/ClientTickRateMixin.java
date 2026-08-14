package orbiter.mixin;

import orbiter.util.ClientSpoofState;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

@Mixin(MinecraftClient.class)
public abstract class ClientTickRateMixin {
    @ModifyReturnValue(method = "getTargetMillisPerTick", at = @At("RETURN"))
    private float orbiter$scaleClientTickRate(float original) {
        if (ClientSpoofState.module() == null || !ClientSpoofState.module().shouldSpoofClientTickRate()) return original;
        return original / ClientSpoofState.module().getSpoofClientTickRate();
    }
}
