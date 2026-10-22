package orbiter.compat;

import java.util.List;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ChargedProjectiles;

public final class ItemCompat {
    private ItemCompat() {}

    public static Item copperBlock() {
        return Items.COPPER_BLOCK.weathering().unaffected();
    }

    public static Item stainedGlass() {
        return Items.STAINED_GLASS.pick(DyeColor.WHITE);
    }

    public static List<ItemStack> bundleCopies(BundleContents bundle) {
        return bundle.itemCopyStream().toList();
    }

    public static List<ItemStack> chargedProjectiles(ChargedProjectiles charged) {
        return charged.itemCopies();
    }
}
