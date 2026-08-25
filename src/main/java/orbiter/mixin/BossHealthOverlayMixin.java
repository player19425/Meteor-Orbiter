package orbiter.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {
    @WrapMethod(method = "update(Lnet/minecraft/network/protocol/game/ClientboundBossEventPacket;)V")
    private void orbiter$tolerantUpdate(ClientboundBossEventPacket packet, Operation<Void> original) {
        try {
            original.call(packet);
        } catch (NullPointerException ignored) {
        }
    }
}
