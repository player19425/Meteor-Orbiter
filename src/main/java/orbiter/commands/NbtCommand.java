package orbiter.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;

public class NbtCommand extends Command {

    public NbtCommand() {
        super("nbt", "Copies the held item's full NBT to the clipboard.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            if (mc.player == null) return SINGLE_SUCCESS;

            ItemStack stack = mc.player.getMainHandStack();
            if (stack.isEmpty()) {
                info("Hold an item first.");
                return SINGLE_SUCCESS;
            }

            ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, stack)
                .resultOrPartial(err -> info("Failed to encode item: " + err))
                .ifPresent(tag -> {
                    NbtCompound compound = (NbtCompound) tag;
                    mc.keyboard.setClipboard(compound.toString());
                    info("Copied " + stack.getName().getString() + " NBT to clipboard.");
                });

            return SINGLE_SUCCESS;
        });
    }
}
