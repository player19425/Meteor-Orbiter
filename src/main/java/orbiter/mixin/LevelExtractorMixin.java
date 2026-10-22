package orbiter.mixin;

import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import orbiter.modules.misc.ClientSideThings;
import orbiter.util.ClientSpoofState;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
    @Redirect(
        require = 1,
        method = "extractPlayerState",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/renderer/state/level/PlayerRenderState;isEyeInWater:Z",
            opcode = Opcodes.GETFIELD
        )
    )
    private boolean orbiter$forceWaterOverlay(PlayerRenderState state) {
        ClientSideThings module = ClientSpoofState.module();
        if (module != null && module.shouldForceWaterOverlay()) return true;
        return state.isEyeInWater;
    }
}
