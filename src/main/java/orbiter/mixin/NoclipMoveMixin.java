package orbiter.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import orbiter.modules.movement.Noclip;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class NoclipMoveMixin {
    @Inject(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), cancellable = true)
    private void orbiter$noclipMove(MoverType type, Vec3 movement, CallbackInfo ci) {
        if (type != MoverType.SELF) return;
        Entity self = (Entity) (Object) this;
        if (!(self instanceof LocalPlayer player)) return;
        if (!Noclip.isActiveStatic()) return;

        Vec3 resolved = Noclip.currentMotion(player);
        player.setPos(player.getX() + resolved.x, player.getY() + resolved.y, player.getZ() + resolved.z);
        player.setDeltaMovement(Vec3.ZERO);
        ci.cancel();
    }
}
