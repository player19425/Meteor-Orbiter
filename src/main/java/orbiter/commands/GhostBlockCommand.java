package orbiter.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import orbiter.util.GhostBlockManager;

import java.util.HashSet;
import java.util.Set;

public class GhostBlockCommand extends Command {
    private static final SuggestionProvider<ClientSuggestionProvider> BLOCK_SUGGESTIONS = (context, builder) -> {
        String remaining = builder.getRemainingLowerCase();
        Set<String> suggested = new HashSet<>();

        for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
            String full = id.toString();
            if (full.startsWith(remaining) && suggested.add(full)) builder.suggest(full);

            if ("minecraft".equals(id.getNamespace())) {
                String shortId = id.getPath();
                if (shortId.startsWith(remaining) && suggested.add(shortId)) builder.suggest(shortId);
            }
        }

        return builder.buildFuture();
    };

    public GhostBlockCommand() {
        super("ghostblock", "Client-side ghost blocks only you can see.", "gb");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(ctx -> {
            info("Usage: ghostblock create <x> <y> <z> <block>, create here <block>, delete <x> <y> <z>, delete here, delete all, list.");
            return SINGLE_SUCCESS;
        });

        builder.then(literal("create")
            .then(literal("here")
                .then(argument("block", StringArgumentType.greedyString()).suggests(BLOCK_SUGGESTIONS)
                    .executes(ctx -> {
                        if (mc.hitResult instanceof BlockHitResult bhr && bhr.getType() != HitResult.Type.MISS) {
                            place(bhr.getBlockPos(), StringArgumentType.getString(ctx, "block"));
                        } else {
                            error("Not looking at a block.");
                        }
                        return SINGLE_SUCCESS;
                    })))
            .then(argument("x", IntegerArgumentType.integer())
                .then(argument("y", IntegerArgumentType.integer())
                    .then(argument("z", IntegerArgumentType.integer())
                        .then(argument("block", StringArgumentType.greedyString()).suggests(BLOCK_SUGGESTIONS)
                            .executes(ctx -> {
                                BlockPos pos = new BlockPos(
                                    IntegerArgumentType.getInteger(ctx, "x"),
                                    IntegerArgumentType.getInteger(ctx, "y"),
                                    IntegerArgumentType.getInteger(ctx, "z"));
                                place(pos, StringArgumentType.getString(ctx, "block"));
                                return SINGLE_SUCCESS;
                            }))))));

        builder.then(literal("delete")
            .then(literal("here")
                .executes(ctx -> {
                    if (mc.hitResult instanceof BlockHitResult bhr && bhr.getType() != HitResult.Type.MISS) {
                        remove(bhr.getBlockPos());
                    } else {
                        error("Not looking at a block.");
                    }
                    return SINGLE_SUCCESS;
                }))
            .then(literal("all")
                .executes(ctx -> {
                    int removed = GhostBlockManager.deleteAll();
                    if (removed > 0) info("Removed " + removed + " ghost blocks.");
                    else info("No ghost blocks.");
                    return SINGLE_SUCCESS;
                }))
            .then(argument("x", IntegerArgumentType.integer())
                .then(argument("y", IntegerArgumentType.integer())
                    .then(argument("z", IntegerArgumentType.integer())
                        .executes(ctx -> {
                            remove(new BlockPos(
                                IntegerArgumentType.getInteger(ctx, "x"),
                                IntegerArgumentType.getInteger(ctx, "y"),
                                IntegerArgumentType.getInteger(ctx, "z")));
                            return SINGLE_SUCCESS;
                        })))));

        builder.then(literal("list")
            .executes(ctx -> {
                int total = GhostBlockManager.count();
                if (total == 0) {
                    info("No ghost blocks.");
                    return SINGLE_SUCCESS;
                }

                info("Ghost blocks (" + total + "):");
                for (String line : GhostBlockManager.summary(20)) info(line);
                return SINGLE_SUCCESS;
            }));
    }

    private void place(BlockPos pos, String block) {
        if (mc.level == null) {
            error("Not in a world.");
            return;
        }

        if (!GhostBlockManager.create(pos, block)) {
            GhostBlockManager.BlockSpec spec = GhostBlockManager.parse(block);
            error(spec.ok() ? "Failed to place the ghost block." : spec.error());
            return;
        }

        info("Placed ghost block at " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
            + " (vanishes when the server updates the chunk).");
    }

    private void remove(BlockPos pos) {
        if (GhostBlockManager.delete(pos)) {
            info("Removed ghost block at " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + ".");
        } else {
            error("No ghost block there.");
        }
    }
}
