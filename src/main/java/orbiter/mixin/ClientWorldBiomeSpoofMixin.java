package orbiter.mixin;

import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(ClientLevel.class)
public abstract class ClientWorldBiomeSpoofMixin {
    @Unique
    private static final Map<String, Holder<Biome>> orbiter$biomeCache = new ConcurrentHashMap<>();

    @Inject(method = "getUncachedNoiseBiome", at = @At("HEAD"), cancellable = true)
    private void orbiter$getBiome(int x, int y, int z, CallbackInfoReturnable<Holder<Biome>> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.shouldSpoofBiome()) return;
        Identifier id = Identifier.tryParse(module.getSpoofBiomeId());
        if (id == null) return;

        String key = id.toString();
        Holder<Biome> entry = orbiter$biomeCache.get(key);
        if (entry == null) {
            ClientLevel world = (ClientLevel) (Object) this;
            entry = world.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).get(id).orElse(null);
            if (entry != null) orbiter$biomeCache.put(key, entry);
        }
        if (entry != null) cir.setReturnValue(entry);
    }
}
