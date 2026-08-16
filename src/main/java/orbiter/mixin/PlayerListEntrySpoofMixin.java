package orbiter.mixin;

import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInfo.class)
public abstract class PlayerListEntrySpoofMixin {
    @Inject(method = "getGameMode", at = @At("HEAD"), cancellable = true)
    private void orbiter$getGameMode(CallbackInfoReturnable<GameType> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.visualCreativeEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        GameProfile profile = ((PlayerInfo) (Object) this).getProfile();
        if (profile == null || profile.id() == null) return;

        if (profile.id().equals(mc.player.getUUID())) {
            cir.setReturnValue(GameType.CREATIVE);
        }
    }
}
