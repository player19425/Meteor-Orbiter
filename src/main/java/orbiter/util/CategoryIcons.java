package orbiter.util;

import meteordevelopment.meteorclient.systems.modules.Category;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class CategoryIcons {
    private CategoryIcons() {}

    public static Category of(String name, Item icon) {
        return new Category(name, new ItemStack(icon));
    }
}
