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

    private static boolean orbiter$isHidden(Category category) {
        ConfigModifier config = ConfigModifier.get();
        if (category == Orbiter.CATEGORY_STUPID && !config.stupidModulesEnabled()) return true;
        if (category == Orbiter.CATEGORY_WIP && !config.wipModulesEnabled()) return true;
        return false;
    }

    @ModifyReturnValue(method = "loopCategories", at = @At("RETURN"))
    private static Iterable<Category> orbiter$filterHiddenCategories(Iterable<Category> original) {
        List<Category> filtered = new ArrayList<>();
        for (Category cat : original) {
            if (!orbiter$isHidden(cat)) filtered.add(cat);
        }
        return filtered;
    }

    @SuppressWarnings("unchecked")
    @ModifyReturnValue(method = "searchTitles", at = @At("RETURN"))
    private static List<?> orbiter$filterSearchTitles(List<?> original) {
        return original.stream()
            .filter(entry -> !orbiter$isHidden(((Module) ((Pair<?, ?>) entry).getFirst()).category))
            .collect(Collectors.toList());
    }

    @ModifyReturnValue(method = "searchSettingTitles", at = @At("RETURN"))
    private static Set<Module> orbiter$filterSearchSettingTitles(Set<Module> original) {
        return original.stream()
            .filter(m -> !orbiter$isHidden(m.category))
            .collect(Collectors.toSet());
    }
}
