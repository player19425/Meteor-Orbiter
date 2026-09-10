package orbiter.util;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

public final class NbtCopyHelper {
    private NbtCopyHelper() {
    }

    public static String encodeItemNbt(ItemStack stack, Consumer<String> onError) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;

        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess());
        return ItemStack.CODEC.encodeStart(ops, stack)
            .resultOrPartial(onError)
            .map(tag -> ((CompoundTag) tag).toString())
            .orElse(null);
    }
}
