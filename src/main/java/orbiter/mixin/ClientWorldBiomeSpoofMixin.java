package orbiter.mixin;

import orbiter.modules.misc.ClientSideThings;
import orbiter.util.ClientSpoofState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
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

    @Unique
    private static RegistryAccess orbiter$cachedRegistry;

    @Inject(method = "getUncachedNoiseBiome", at = @At("HEAD"), cancellable = true, require = 0)
    private void orbiter$getBiome(int x, int y, int z, CallbackInfoReturnable<Holder<Biome>> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.shouldSpoofBiome()) return;
        Identifier id = Identifier.tryParse(module.getSpoofBiomeId());
        if (id == null) return;

        Holder<Biome> biome = orbiter$resolveBiome((ClientLevel) (Object) this, id);
        if (biome != null) cir.setReturnValue(biome);
    }

    @Unique
    private static Holder<Biome> orbiter$resolveBiome(ClientLevel world, Identifier id) {
        RegistryAccess access = world.registryAccess();
        if (access != orbiter$cachedRegistry) {
            orbiter$biomeCache.clear();
            orbiter$cachedRegistry = access;
        }

        Holder<Biome> biome = orbiter$biomeCache.get(id.toString());
        if (biome == null) {
            biome = access.lookupOrThrow(Registries.BIOME).get(id).orElse(null);
            if (biome != null) orbiter$biomeCache.put(id.toString(), biome);
        }
        return biome;
    }
}