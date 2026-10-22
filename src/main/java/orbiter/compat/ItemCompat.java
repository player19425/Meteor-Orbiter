package orbiter.compat;

import java.util.List;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ChargedProjectiles;

public final class ItemCompat {
    private ItemCompat() {}

    public static Item copperBlock() {
        return Items.COPPER_BLOCK;
    }

    public static Item stainedGlass() {
        return Items.WHITE_STAINED_GLASS;
    }

    public static List<ItemStack> bundleCopies(BundleContents bundle) {
        return bundle.itemCopyStream().toList();
    }

    public static List<ItemStack> chargedProjectiles(ChargedProjectiles charged) {
        return charged.itemCopies();
    }
}
