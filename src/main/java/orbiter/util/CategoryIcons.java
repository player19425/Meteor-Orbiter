package orbiter.util;

import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.utils.render.DisplayItemUtils;
import net.minecraft.world.item.Item;

public final class CategoryIcons {
    private CategoryIcons() {}

    public static Category of(String name, Item icon) {
        return new Category(name, () -> DisplayItemUtils.toStack(icon));
    }
}