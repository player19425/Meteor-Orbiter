package orbiter.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import com.mojang.datafixers.util.Pair;
import orbiter.Orbiter;
import orbiter.util.ConfigModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mixin(value = Modules.class, priority = 500)
public abstract class ModulesMixin {

    private static boolean orbiter$isStupidEnabled() {
        return ConfigModifier.get().stupidModules.get();
    }

    @ModifyReturnValue(method = "loopCategories", at = @At("RETURN"))
    private static Iterable<Category> orbiter$filterStupidCategory(Iterable<Category> original) {
        if (orbiter$isStupidEnabled()) return original;
        List<Category> filtered = new ArrayList<>();
        for (Category cat : original) {
            if (cat != Orbiter.CATEGORY_STUPID) filtered.add(cat);
        }
        return filtered;
    }

    @SuppressWarnings("unchecked")
    @ModifyReturnValue(method = "searchTitles", at = @At("RETURN"))
    private static List<?> orbiter$filterSearchTitles(List<?> original) {
        if (orbiter$isStupidEnabled()) return original;
        return original.stream()
            .filter(entry -> {
                Module m = (Module) ((Pair<?, ?>) entry).getFirst();
                return m.category != Orbiter.CATEGORY_STUPID;
            })
            .collect(Collectors.toList());
    }

    @ModifyReturnValue(method = "searchSettingTitles", at = @At("RETURN"))
    private static Set<Module> orbiter$filterSearchSettingTitles(Set<Module> original) {
        if (orbiter$isStupidEnabled()) return original;
        return original.stream()
            .filter(m -> m.category != Orbiter.CATEGORY_STUPID)
            .collect(Collectors.toSet());
    }
}
