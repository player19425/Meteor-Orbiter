package orbiter.mixin;

import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListEntry.class)
public abstract class PlayerListEntrySpoofMixin {
    @Inject(method = "getGameMode", at = @At("HEAD"), cancellable = true)
    private void orbiter$getGameMode(CallbackInfoReturnable<GameMode> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.visualCreativeEnabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        GameProfile profile = ((PlayerListEntry) (Object) this).getProfile();
        if (profile == null || profile.id() == null) return;

        if (profile.id().equals(mc.player.getUuid())) {
            cir.setReturnValue(GameMode.CREATIVE);
        }
    }
}
