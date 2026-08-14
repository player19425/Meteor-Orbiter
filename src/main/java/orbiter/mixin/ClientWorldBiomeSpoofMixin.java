package orbiter.mixin;

import orbiter.util.ClientSpoofState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.class)
public abstract class ClientWorldBiomeSpoofMixin {
    @Inject(method = "getGeneratorStoredBiome", at = @At("HEAD"), cancellable = true)
    private void orbiter$getBiome(int x, int y, int z, CallbackInfoReturnable<RegistryEntry<Biome>> cir) {
        if (ClientSpoofState.module() == null || !ClientSpoofState.module().shouldSpoofBiome()) return;
        ClientWorld world = (ClientWorld) (Object) this;
        Identifier id = Identifier.tryParse(ClientSpoofState.module().getSpoofBiomeId());
        if (id == null) return;
        RegistryEntry<Biome> entry = world.getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.BIOME).getEntry(id).orElse(null);
        if (entry != null) cir.setReturnValue(entry);
    }
}
