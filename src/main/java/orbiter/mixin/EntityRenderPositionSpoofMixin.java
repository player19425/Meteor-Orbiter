package orbiter.mixin;

import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRenderPositionSpoofMixin {
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void orbiter$offsetRenderState(Entity entity, EntityRenderState state, float tickProgress, CallbackInfo ci) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.shouldSpoofPosition() || entity != Minecraft.getInstance().player) return;
        state.x += module.getPositionOffsetX();
        state.y += module.getPositionOffsetY();
        state.z += module.getPositionOffsetZ();
    }
}
