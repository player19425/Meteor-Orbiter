package orbiter.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.dimension.DimensionType;
import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class WorldSkySpoofMixin {
    @Inject(method = "lambda$addSkyPass$0(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/state/level/SkyRenderState;)V", at = @At("HEAD"))
    private void orbiter$applySky(GpuBufferSlice fogBuffer, SkyRenderState skyRenderState, CallbackInfo ci) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.shouldSpoofSky()) return;

        skyRenderState.skybox = switch (module.getSkyMode()) {
            case End -> DimensionType.Skybox.END;
            case Nether -> DimensionType.Skybox.NONE;
            case Overworld -> DimensionType.Skybox.OVERWORLD;
        };
    }
}
