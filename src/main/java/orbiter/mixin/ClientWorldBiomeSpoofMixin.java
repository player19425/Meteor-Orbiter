package orbiter.mixin;

import orbiter.util.ClientSpoofState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class ClientWorldBiomeSpoofMixin {
    @Inject(method = "getUncachedNoiseBiome", at = @At("HEAD"), cancellable = true)
    private void orbiter$getBiome(int x, int y, int z, CallbackInfoReturnable<Holder<Biome>> cir) {
        if (ClientSpoofState.module() == null || !ClientSpoofState.module().shouldSpoofBiome()) return;
        ClientLevel world = (ClientLevel) (Object) this;
        Identifier id = Identifier.tryParse(ClientSpoofState.module().getSpoofBiomeId());
        if (id == null) return;
        Holder<Biome> entry = world.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).get(id).orElse(null);
        if (entry != null) cir.setReturnValue(entry);
    }
}
