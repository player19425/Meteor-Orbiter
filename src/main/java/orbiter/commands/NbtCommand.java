package orbiter.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;

public class NbtCommand extends Command {

    public NbtCommand() {
        super("nbt", "Copies the held item's full NBT to the clipboard.");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.executes(context -> {
            if (mc.player == null) return SINGLE_SUCCESS;

            ItemStack stack = mc.player.getMainHandItem();
            if (stack.isEmpty()) {
                info("Hold an item first.");
                return SINGLE_SUCCESS;
            }

            RegistryOps<net.minecraft.nbt.Tag> ops = RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess());
            ItemStack.CODEC.encodeStart(ops, stack)
                .resultOrPartial(err -> info("Failed to encode item: %s", err))
                .ifPresent(tag -> {
                    CompoundTag compound = (CompoundTag) tag;
                    mc.keyboardHandler.setClipboard(compound.toString());
                    info("Copied %s NBT to clipboard.", stack.getHoverName().getString());
                });

            return SINGLE_SUCCESS;
        });
    }
}
