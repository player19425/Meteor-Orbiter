package orbiter.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.world.dimension.DimensionType;
import orbiter.util.ClientSpoofState;
import orbiter.modules.ClientSideThings;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldSkySpoofMixin {
    @Shadow @Final private WorldRenderState worldRenderState;
    @Unique private DimensionType.Skybox orbiter$originalSkybox;

    @Inject(method = "renderSky", at = @At("HEAD"))
    private void orbiter$applySky(FrameGraphBuilder graph, Camera camera, GpuBufferSlice fogBuffer, CallbackInfo ci) {
        if (ClientSpoofState.module() == null || !ClientSpoofState.module().shouldSpoofSky()) return;
        orbiter$originalSkybox = worldRenderState.skyRenderState.skybox;
        ClientSideThings.DimensionSky mode = ClientSpoofState.module().getSkyMode();
        worldRenderState.skyRenderState.skybox = switch (mode) {
            case End -> DimensionType.Skybox.END;
            case Nether -> DimensionType.Skybox.NONE;
            case Overworld -> DimensionType.Skybox.OVERWORLD;
        };
    }

    @Inject(method = "renderSky", at = @At("RETURN"))
    private void orbiter$restoreSky(FrameGraphBuilder graph, Camera camera, GpuBufferSlice fogBuffer, CallbackInfo ci) {
        if (orbiter$originalSkybox == null) return;
        worldRenderState.skyRenderState.skybox = orbiter$originalSkybox;
        orbiter$originalSkybox = null;
    }
}
