package orbiter.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import orbiter.util.CameraPosition;
import orbiter.util.NbtCopyHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class CopyCommand extends Command {

    public CopyCommand() {
        super("copy", "Copies all kinds of client data to the clipboard.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(ctx -> {
            info("Subcommands: coordinates, camerapos, nbt, helditem, looking, blockid, serverip, uuid.");
            return SINGLE_SUCCESS;
        });

        builder.then(literal("coordinates")
            .executes(ctx -> {
                if (mc.player == null) {
                    error("Not in a world.");
                    return SINGLE_SUCCESS;
                }
                copy(coords(mc.player.getX(), mc.player.getY(), mc.player.getZ()));
                return SINGLE_SUCCESS;
            })
            .then(literal("exact").executes(ctx -> {
                if (mc.player == null) {
                    error("Not in a world.");
                    return SINGLE_SUCCESS;
                }
                copy(mc.player.getX() + " " + mc.player.getY() + " " + mc.player.getZ());
                return SINGLE_SUCCESS;
            })));

        builder.then(literal("camerapos").executes(ctx -> copy(CameraPosition.coords())));

        builder.then(literal("nbt").executes(ctx -> {
            if (mc.player == null) {
                error("Not in a world.");
                return SINGLE_SUCCESS;
            }
            ItemStack stack = mc.player.getMainHandItem();
            if (stack.isEmpty()) {
                error("Hold an item first.");
                return SINGLE_SUCCESS;
            }

            String nbt = NbtCopyHelper.encodeItemNbt(stack, err -> error("Failed to encode item: " + err));
            if (nbt == null) return SINGLE_SUCCESS;

            mc.keyboardHandler.setClipboard(nbt);
            info("Copied " + stack.getHoverName().getString() + " NBT to clipboard.");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("helditem")
            .executes(ctx -> {
                ItemStack stack = heldStack();
                if (stack == null) return SINGLE_SUCCESS;
                copy(heldId(stack) + " x" + stack.getCount());
                return SINGLE_SUCCESS;
            })
            .then(literal("verbose").executes(ctx -> {
                ItemStack stack = heldStack();
                if (stack == null) return SINGLE_SUCCESS;

                String nbt = NbtCopyHelper.encodeItemNbt(stack, err -> error("Failed to encode item: " + err));
                if (nbt == null) return SINGLE_SUCCESS;

                copy(heldId(stack) + " x" + stack.getCount() + " " + nbt);
                return SINGLE_SUCCESS;
            })));

        builder.then(literal("looking").executes(ctx -> {
            if (mc.hitResult instanceof BlockHitResult bhr && bhr.getType() != HitResult.Type.MISS) {
                BlockPos pos = bhr.getBlockPos();
                copy(pos.getX() + " " + pos.getY() + " " + pos.getZ());
            } else if (mc.hitResult instanceof EntityHitResult ehr) {
                Entity e = ehr.getEntity();
                copy(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()) + " "
                    + coords(e.getX(), e.getY(), e.getZ()));
            } else {
                info("Looking at nothing.");
            }
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("blockid")
            .executes(ctx -> {
                BlockState state = lookedState();
                if (state != null) copy(stateId(state));
                return SINGLE_SUCCESS;
            })
            .then(literal("verbose").executes(ctx -> {
                BlockState state = lookedState();
                if (state == null) return SINGLE_SUCCESS;

                BlockState def = state.getBlock().defaultBlockState();
                List<String> extra = new ArrayList<>();
                state.getValues().forEach(v -> {
                    if (!v.value().equals(def.getValue(v.property()))) {
                        extra.add(v.property().getName() + "=" + v.valueName());
                    }
                });
                Collections.sort(extra);

                StringBuilder sb = new StringBuilder(stateId(state));
                if (!extra.isEmpty()) sb.append('[').append(String.join(",", extra)).append(']');
                copy(sb.toString());
                return SINGLE_SUCCESS;
            })));

        builder.then(literal("serverip").executes(ctx -> {
            if (mc.getConnection() != null && mc.getConnection().getServerData() != null) {
                copy(mc.getConnection().getServerData().ip);
            } else if (mc.player != null) {
                copy("singleplayer");
            } else {
                error("Not in a world.");
            }
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("uuid").executes(ctx -> {
            if (mc.player == null) {
                error("Not in a world.");
                return SINGLE_SUCCESS;
            }
            copy(mc.player.getUUID().toString());
            return SINGLE_SUCCESS;
        }));
    }

    private int copy(String value) {
        mc.keyboardHandler.setClipboard(value);
        info("Copied " + truncate(value));
        return SINGLE_SUCCESS;
    }

    private String truncate(String value) {
        int max = 200;
        if (value.length() <= max) return value;
        return value.substring(0, max) + "... (" + value.length() + " chars)";
    }

    private ItemStack heldStack() {
        if (mc.player == null) return null;
        ItemStack stack = mc.player.getMainHandItem();
        if (stack.isEmpty()) {
            error("Hold an item first.");
            return null;
        }
        return stack;
    }

    private String heldId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private BlockState lookedState() {
        if (mc.level == null) {
            error("Not in a world.");
            return null;
        }
        if (!(mc.hitResult instanceof BlockHitResult bhr) || bhr.getType() == HitResult.Type.MISS) {
            error("Not looking at a block.");
            return null;
        }
        return mc.level.getBlockState(bhr.getBlockPos());
    }

    private String stateId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    private String coords(double x, double y, double z) {
        return String.format(Locale.ROOT, "%.2f %.2f %.2f", x, y, z);
    }
}
