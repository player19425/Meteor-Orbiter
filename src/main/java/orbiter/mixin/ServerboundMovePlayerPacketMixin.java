package orbiter.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import orbiter.modules.render.Camera360;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = {ServerboundMovePlayerPacket.Rot.class, ServerboundMovePlayerPacket.PosRot.class})
public abstract class ServerboundMovePlayerPacketMixin {

    private static boolean orbiter$is360Active() {
        Camera360 mod = Modules.get().get(Camera360.class);
        return mod != null && mod.isActive();
    }

    @Redirect(method = "write", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/FriendlyByteBuf;writeFloat(F)Lnet/minecraft/network/FriendlyByteBuf;", ordinal = 0))
    private static FriendlyByteBuf orbiter$wrapPacketYaw(FriendlyByteBuf buf, float yaw) {
        if (!orbiter$is360Active()) return buf.writeFloat(yaw);
        return buf.writeFloat(Mth.wrapDegrees(yaw));
    }

    @Redirect(method = "write", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/FriendlyByteBuf;writeFloat(F)Lnet/minecraft/network/FriendlyByteBuf;", ordinal = 1))
    private static FriendlyByteBuf orbiter$clampPacketPitch(FriendlyByteBuf buf, float pitch) {
        if (!orbiter$is360Active()) return buf.writeFloat(pitch);
        return buf.writeFloat(Mth.clamp(pitch, -90f, 90f));
    }
}
