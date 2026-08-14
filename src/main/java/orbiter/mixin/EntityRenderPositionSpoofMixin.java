package orbiter.mixin;

import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRenderPositionSpoofMixin {
    @Inject(method = "updateRenderState", at = @At("RETURN"))
    private void orbiter$offsetRenderState(Entity entity, EntityRenderState state, float tickProgress, CallbackInfo ci) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.shouldSpoofPosition() || entity != MinecraftClient.getInstance().player) return;
        state.x += module.getPositionOffsetX();
        state.y += module.getPositionOffsetY();
        state.z += module.getPositionOffsetZ();
    }
}
