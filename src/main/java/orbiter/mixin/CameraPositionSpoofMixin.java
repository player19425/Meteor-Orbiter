package orbiter.mixin;

import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraPositionSpoofMixin {
    @Shadow protected abstract void setPosition(double x, double y, double z);

    @Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V", at = @At("RETURN"))
    private void orbiter$offsetCamera(DeltaTracker deltaTracker, CallbackInfo ci) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.shouldSpoofPosition()) return;
        Camera camera = (Camera) (Object) this;
        Entity focusedEntity = camera.entity();
        if (focusedEntity != Minecraft.getInstance().player) return;
        var pos = camera.position();
        setPosition(pos.x + module.getPositionOffsetX(), pos.y + module.getPositionOffsetY(), pos.z + module.getPositionOffsetZ());
    }
}
