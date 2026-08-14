package orbiter.commands;

import orbiter.modules.WorldEditModule;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class WorldEditCommand extends Command {
    private static final SuggestionProvider<CommandSource> DIRECTION_SUGGESTIONS = (context, builder) -> {
        String[] dirs = { "north", "south", "east", "west", "up", "down" };
        String remaining = builder.getRemainingLowerCase();
        for (String d : dirs) {
            if (d.startsWith(remaining)) builder.suggest(d);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSource> BLOCK_SUGGESTIONS = (context, builder) -> {
        String remaining = builder.getRemainingLowerCase();
        Set<String> suggested = new HashSet<>();

        for (Identifier id : Registries.BLOCK.getIds()) {
            String full = id.toString();
            if (full.startsWith(remaining) && suggested.add(full)) builder.suggest(full);

            if ("minecraft".equals(id.getNamespace())) {
                String shortId = id.getPath();
                if (shortId.startsWith(remaining) && suggested.add(shortId)) builder.suggest(shortId);
            }
        }

        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSource> ITEM_SUGGESTIONS = (context, builder) -> {
        String remaining = builder.getRemainingLowerCase();
        Set<String> suggested = new HashSet<>();

        for (Identifier id : Registries.ITEM.getIds()) {
            String full = id.toString();
            if (full.startsWith(remaining) && suggested.add(full)) builder.suggest(full);

            if ("minecraft".equals(id.getNamespace())) {
                String shortId = id.getPath();
                if (shortId.startsWith(remaining) && suggested.add(shortId)) builder.suggest(shortId);
            }
        }

        return builder.buildFuture();
    };

    public WorldEditCommand() {
        super("we", "Expanded WorldEdit commands with dynamic autocomplete.", "worldedit");
    }

    private WorldEditModule getModule() {
        Modules modules = Modules.get();
        if (modules == null) return null;
        return modules.get(WorldEditModule.class);
    }

    private void run(String args) {
        WorldEditModule mod = getModule();
        if (mod == null || !mod.isActive()) {
            error("WorldEdit module is not active! Enable it first.");
            return;
        }

        mod.processCommand(args);
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {

        builder.then(literal("pos1")
                .executes(ctx -> {
                    run("pos1");
                    return SINGLE_SUCCESS;
                })
                .then(argument("x", IntegerArgumentType.integer())
                        .then(argument("y", IntegerArgumentType.integer())
                                .then(argument("z", IntegerArgumentType.integer())
                                        .executes(ctx -> {
                                            int x = IntegerArgumentType.getInteger(ctx, "x");
                                            int y = IntegerArgumentType.getInteger(ctx, "y");
                                            int z = IntegerArgumentType.getInteger(ctx, "z");
                                            WorldEditModule mod = getModule();
                                            if (mod != null && mod.isActive()) mod.setPos1(new BlockPos(x, y, z));
                                            else error("WorldEdit module is not active!");
                                            return SINGLE_SUCCESS;
                                        })))));

        builder.then(literal("pos2")
                .executes(ctx -> {
                    run("pos2");
                    return SINGLE_SUCCESS;
                })
                .then(argument("x", IntegerArgumentType.integer())
                        .then(argument("y", IntegerArgumentType.integer())
                                .then(argument("z", IntegerArgumentType.integer())
                                        .executes(ctx -> {
                                            int x = IntegerArgumentType.getInteger(ctx, "x");
                                            int y = IntegerArgumentType.getInteger(ctx, "y");
                                            int z = IntegerArgumentType.getInteger(ctx, "z");
                                            WorldEditModule mod = getModule();
                                            if (mod != null && mod.isActive()) mod.setPos2(new BlockPos(x, y, z));
                                            else error("WorldEdit module is not active!");
                                            return SINGLE_SUCCESS;
                                        })))));

        builder.then(literal("p1").executes(ctx -> {
            run("pos1");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("p2").executes(ctx -> {
            run("pos2");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("hpos1").executes(ctx -> {
            run("hpos1");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("hpos2").executes(ctx -> {
            run("hpos2");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("chunk").executes(ctx -> {
            run("chunk");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("set")
                .then(argument("block", StringArgumentType.string()).suggests(BLOCK_SUGGESTIONS)
                        .executes(ctx -> {
                            run("set " + StringArgumentType.getString(ctx, "block"));
                            return SINGLE_SUCCESS;
                        })));

        builder.then(literal("replace")
                .then(argument("block", StringArgumentType.string()).suggests(BLOCK_SUGGESTIONS)
                        .executes(ctx -> {
                            run("replace " + StringArgumentType.getString(ctx, "block"));
                            return SINGLE_SUCCESS;
                        })
                        .then(argument("to", StringArgumentType.string()).suggests(BLOCK_SUGGESTIONS)
                                .executes(ctx -> {
                                    run("replace " + StringArgumentType.getString(ctx, "block") + " "
                                            + StringArgumentType.getString(ctx, "to"));
                                    return SINGLE_SUCCESS;
                                }))));

        builder.then(literal("walls")
                .then(argument("block", StringArgumentType.string()).suggests(BLOCK_SUGGESTIONS)
                        .executes(ctx -> {
                            run("walls " + StringArgumentType.getString(ctx, "block"));
                            return SINGLE_SUCCESS;
                        })));

        builder.then(literal("outline")
                .then(argument("block", StringArgumentType.string()).suggests(BLOCK_SUGGESTIONS)
                        .executes(ctx -> {
                            run("outline " + StringArgumentType.getString(ctx, "block"));
                            return SINGLE_SUCCESS;
                        })));

        builder.then(literal("floor")
                .then(argument("block", StringArgumentType.string()).suggests(BLOCK_SUGGESTIONS)
                        .executes(ctx -> {
                            run("floor " + StringArgumentType.getString(ctx, "block"));
                            return SINGLE_SUCCESS;
                        })));

        builder.then(literal("roof")
                .then(argument("block", StringArgumentType.string()).suggests(BLOCK_SUGGESTIONS)
                        .executes(ctx -> {
                            run("roof " + StringArgumentType.getString(ctx, "block"));
                            return SINGLE_SUCCESS;
                        })));

        builder.then(literal("hollow")
                .then(argument("block", StringArgumentType.string()).suggests(BLOCK_SUGGESTIONS)
                        .executes(ctx -> {
                            run("hollow " + StringArgumentType.getString(ctx, "block"));
                            return SINGLE_SUCCESS;
                        })
                        .then(argument("thickness", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    run("hollow " + StringArgumentType.getString(ctx, "block") + " "
                                            + IntegerArgumentType.getInteger(ctx, "thickness"));
                                    return SINGLE_SUCCESS;
                                }))));

        builder.then(literal("sphere")
                .then(argument("block", StringArgumentType.string()).suggests(BLOCK_SUGGESTIONS)
                        .then(argument("radius", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    run("sphere " + StringArgumentType.getString(ctx, "block") + " "
                                            + IntegerArgumentType.getInteger(ctx, "radius"));
                                    return SINGLE_SUCCESS;
                                }))));

        builder.then(literal("hsphere")
                .then(argument("block", StringArgumentType.string()).suggests(BLOCK_SUGGESTIONS)
                        .then(argument("radius", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    run("hsphere " + StringArgumentType.getString(ctx, "block") + " "
                                            + IntegerArgumentType.getInteger(ctx, "radius"));
                                    return SINGLE_SUCCESS;
                                }))));

        builder.then(literal("cyl")
                .then(argument("block", StringArgumentType.string()).suggests(BLOCK_SUGGESTIONS)
                        .then(argument("radius", IntegerArgumentType.integer(1))
                                .then(argument("height", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            run("cyl " + StringArgumentType.getString(ctx, "block") + " "
                                                    + IntegerArgumentType.getInteger(ctx, "radius") + " "
                                                    + IntegerArgumentType.getInteger(ctx, "height"));
                                            return SINGLE_SUCCESS;
                                        })))));

        builder.then(literal("hcyl")
                .then(argument("block", StringArgumentType.string()).suggests(BLOCK_SUGGESTIONS)
                        .then(argument("radius", IntegerArgumentType.integer(1))
                                .then(argument("height", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            run("hcyl " + StringArgumentType.getString(ctx, "block") + " "
                                                    + IntegerArgumentType.getInteger(ctx, "radius") + " "
                                                    + IntegerArgumentType.getInteger(ctx, "height"));
                                            return SINGLE_SUCCESS;
                                        })))));

        builder.then(literal("pyramid")
                .then(argument("block", StringArgumentType.string()).suggests(BLOCK_SUGGESTIONS)
                        .then(argument("size", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    run("pyramid " + StringArgumentType.getString(ctx, "block") + " "
                                            + IntegerArgumentType.getInteger(ctx, "size"));
                                    return SINGLE_SUCCESS;
                                }))));

        builder.then(literal("hpyramid")
                .then(argument("block", StringArgumentType.string()).suggests(BLOCK_SUGGESTIONS)
                        .then(argument("size", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    run("hpyramid " + StringArgumentType.getString(ctx, "block") + " "
                                            + IntegerArgumentType.getInteger(ctx, "size"));
                                    return SINGLE_SUCCESS;
                                }))));

        builder.then(literal("copy").executes(ctx -> {
            run("copy");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("cut").executes(ctx -> {
            run("cut");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("paste").executes(ctx -> {
            run("paste");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("flip")
                .executes(ctx -> {
                    run("flip");
                    return SINGLE_SUCCESS;
                })
                .then(argument("direction", StringArgumentType.string()).suggests(DIRECTION_SUGGESTIONS)
                        .executes(ctx -> {
                            run("flip " + StringArgumentType.getString(ctx, "direction"));
                            return SINGLE_SUCCESS;
                        })));

        builder.then(literal("stack")
                .then(argument("count", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            run("stack " + IntegerArgumentType.getInteger(ctx, "count"));
                            return SINGLE_SUCCESS;
                        })
                        .then(argument("direction", StringArgumentType.string()).suggests(DIRECTION_SUGGESTIONS)
                                .executes(ctx -> {
                                    run("stack " + IntegerArgumentType.getInteger(ctx, "count") + " "
                                            + StringArgumentType.getString(ctx, "direction"));
                                    return SINGLE_SUCCESS;
                                }))));

        builder.then(literal("move")
                .then(argument("distance", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            run("move " + IntegerArgumentType.getInteger(ctx, "distance"));
                            return SINGLE_SUCCESS;
                        })
                        .then(argument("direction", StringArgumentType.string()).suggests(DIRECTION_SUGGESTIONS)
                                .executes(ctx -> {
                                    run("move " + IntegerArgumentType.getInteger(ctx, "distance") + " "
                                            + StringArgumentType.getString(ctx, "direction"));
                                    return SINGLE_SUCCESS;
                                }))));

        builder.then(literal("drain")
                .executes(ctx -> {
                    run("drain");
                    return SINGLE_SUCCESS;
                })
                .then(argument("radius", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            run("drain " + IntegerArgumentType.getInteger(ctx, "radius"));
                            return SINGLE_SUCCESS;
                        })));

        builder.then(literal("replacenear")
                .then(argument("radius", IntegerArgumentType.integer(1))
                        .then(argument("from", StringArgumentType.string()).suggests(BLOCK_SUGGESTIONS)
                                .then(argument("to", StringArgumentType.string()).suggests(BLOCK_SUGGESTIONS)
                                        .executes(ctx -> {
                                            run("replacenear " + IntegerArgumentType.getInteger(ctx, "radius") + " "
                                                    + StringArgumentType.getString(ctx, "from") + " "
                                                    + StringArgumentType.getString(ctx, "to"));
                                            return SINGLE_SUCCESS;
                                        })))));

        builder.then(literal("line")
                .then(argument("block", StringArgumentType.string()).suggests(BLOCK_SUGGESTIONS)
                        .executes(ctx -> {
                            run("line " + StringArgumentType.getString(ctx, "block"));
                            return SINGLE_SUCCESS;
                        })));

        builder.then(literal("center")
                .then(argument("block", StringArgumentType.string()).suggests(BLOCK_SUGGESTIONS)
                        .executes(ctx -> {
                            run("center " + StringArgumentType.getString(ctx, "block"));
                            return SINGLE_SUCCESS;
                        })));

        builder.then(literal("count")
                .then(argument("block", StringArgumentType.string()).suggests(BLOCK_SUGGESTIONS)
                        .executes(ctx -> {
                            run("count " + StringArgumentType.getString(ctx, "block"));
                            return SINGLE_SUCCESS;
                        }))
                .executes(ctx -> {
                    run("count");
                    return SINGLE_SUCCESS;
                }));

        builder.then(literal("expand")
                .then(literal("vert")
                        .executes(ctx -> {
                            run("expand vert");
                            return SINGLE_SUCCESS;
                        }))
                .then(argument("amount", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            run("expand " + IntegerArgumentType.getInteger(ctx, "amount"));
                            return SINGLE_SUCCESS;
                        })
                        .then(argument("direction", StringArgumentType.string()).suggests(DIRECTION_SUGGESTIONS)
                                .executes(ctx -> {
                                    run("expand " + IntegerArgumentType.getInteger(ctx, "amount") + " "
                                            + StringArgumentType.getString(ctx, "direction"));
                                    return SINGLE_SUCCESS;
                                }))));

        builder.then(literal("contract")
                .then(argument("amount", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            run("contract " + IntegerArgumentType.getInteger(ctx, "amount"));
                            return SINGLE_SUCCESS;
                        })
                        .then(argument("direction", StringArgumentType.string()).suggests(DIRECTION_SUGGESTIONS)
                                .executes(ctx -> {
                                    run("contract " + IntegerArgumentType.getInteger(ctx, "amount") + " "
                                            + StringArgumentType.getString(ctx, "direction"));
                                    return SINGLE_SUCCESS;
                                }))));

        builder.then(literal("shift")
                .then(argument("amount", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            run("shift " + IntegerArgumentType.getInteger(ctx, "amount"));
                            return SINGLE_SUCCESS;
                        })
                        .then(argument("direction", StringArgumentType.string()).suggests(DIRECTION_SUGGESTIONS)
                                .executes(ctx -> {
                                    run("shift " + IntegerArgumentType.getInteger(ctx, "amount") + " "
                                            + StringArgumentType.getString(ctx, "direction"));
                                    return SINGLE_SUCCESS;
                                }))));

        builder.then(literal("inset")
                .then(argument("amount", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            run("inset " + IntegerArgumentType.getInteger(ctx, "amount"));
                            return SINGLE_SUCCESS;
                        })));

        builder.then(literal("outset")
                .then(argument("amount", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            run("outset " + IntegerArgumentType.getInteger(ctx, "amount"));
                            return SINGLE_SUCCESS;
                        })));

        builder.then(literal("ascend")
                .executes(ctx -> {
                    run("ascend");
                    return SINGLE_SUCCESS;
                })
                .then(argument("levels", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            run("ascend " + IntegerArgumentType.getInteger(ctx, "levels"));
                            return SINGLE_SUCCESS;
                        })));

        builder.then(literal("descend")
                .executes(ctx -> {
                    run("descend");
                    return SINGLE_SUCCESS;
                })
                .then(argument("levels", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            run("descend " + IntegerArgumentType.getInteger(ctx, "levels"));
                            return SINGLE_SUCCESS;
                        })));

        builder.then(literal("ceiling")
                .executes(ctx -> {
                    run("ceiling");
                    return SINGLE_SUCCESS;
                })
                .then(argument("clearance", IntegerArgumentType.integer(0))
                        .executes(ctx -> {
                            run("ceiling " + IntegerArgumentType.getInteger(ctx, "clearance"));
                            return SINGLE_SUCCESS;
                        })));

        builder.then(literal("thru")
                .executes(ctx -> {
                    run("thru");
                    return SINGLE_SUCCESS;
                }));

        builder.then(literal("undo")
                .executes(ctx -> {
                    run("undo");
                    return SINGLE_SUCCESS;
                })
                .then(argument("count", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            run("undo " + IntegerArgumentType.getInteger(ctx, "count"));
                            return SINGLE_SUCCESS;
                        })));

        builder.then(literal("redo")
                .executes(ctx -> {
                    run("redo");
                    return SINGLE_SUCCESS;
                })
                .then(argument("count", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            run("redo " + IntegerArgumentType.getInteger(ctx, "count"));
                            return SINGLE_SUCCESS;
                        })));

        builder.then(literal("saveclipboard")
                .executes(ctx -> {
                    run("saveclipboard");
                    return SINGLE_SUCCESS;
                }));

        builder.then(literal("loadclipboard")
                .executes(ctx -> {
                    run("loadclipboard");
                    return SINGLE_SUCCESS;
                }));

        builder.then(literal("tool")
                .executes(ctx -> {
                    run("tool");
                    return SINGLE_SUCCESS;
                })
                .then(argument("item", StringArgumentType.string()).suggests(ITEM_SUGGESTIONS)
                        .executes(ctx -> {
                            run("tool " + StringArgumentType.getString(ctx, "item"));
                            return SINGLE_SUCCESS;
                        })));

        builder.then(literal("size").executes(ctx -> {
            run("size");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("clear").executes(ctx -> {
            run("clear");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("confirm").executes(ctx -> {
            run("confirm");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("cancel").executes(ctx -> {
            run("cancel");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("help").executes(ctx -> {
            run("help");
            return SINGLE_SUCCESS;
        }));

        builder.executes(ctx -> {
            run("help");
            return SINGLE_SUCCESS;
        });
    }
}
