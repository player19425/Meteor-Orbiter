package orbiter.util;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GhostBlockManager {
    public record GhostEntry(BlockPos pos, BlockState state, BlockState previousState) {
    }

    public record BlockSpec(BlockState state, String error) {
        public boolean ok() {
            return error == null;
        }
    }

    private static final Map<BlockPos, GhostEntry> ghosts = new LinkedHashMap<>();

    private static boolean listening;

    private GhostBlockManager() {
    }

    private static void touch() {
        if (listening) return;
        listening = true;
        MeteorClient.EVENT_BUS.subscribe(new Object() {
            @EventHandler
            private void onGameLeft(GameLeftEvent event) {
                ghosts.clear();
            }
        });
    }

    public static BlockSpec parse(String blockSpec) {
        String[] tokens = blockSpec.trim().split("\\s+");
        if (tokens.length == 0 || tokens[0].isEmpty()) return new BlockSpec(null, "No block given.");

        String raw = tokens[0];
        Identifier id = Identifier.tryParse(raw.contains(":") ? raw : "minecraft:" + raw);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return new BlockSpec(null, "Unknown block: " + raw);

        Block block = BuiltInRegistries.BLOCK.get(id).map(Holder::value).orElse(null);
        if (block == null) return new BlockSpec(null, "Unknown block: " + raw);

        BlockState state = block.defaultBlockState();
        for (int i = 1; i < tokens.length; i++) {
            for (String pair : tokens[i].split(",")) {
                int eq = pair.indexOf('=');
                if (eq <= 0) return new BlockSpec(null, "Invalid property: " + pair);

                String key = pair.substring(0, eq);
                String value = pair.substring(eq + 1);
                Property<?> prop = block.getStateDefinition().getProperty(key);
                if (prop == null) return new BlockSpec(null, "Unknown property: " + key);
                if (prop.getValue(value).isEmpty()) return new BlockSpec(null, "Invalid value for " + key + ": " + value);

                state = apply(state, prop, value);
            }
        }

        return new BlockSpec(state, null);
    }

    private static <T extends Comparable<T>> BlockState apply(BlockState state, Property<T> prop, String value) {
        return state.setValue(prop, prop.getValue(value).orElse(state.getValue(prop)));
    }

    public static boolean create(BlockPos pos, String blockSpec) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;

        BlockSpec spec = parse(blockSpec);
        if (!spec.ok()) return false;

        BlockState previous = mc.level.getBlockState(pos);
        GhostEntry existing = ghosts.get(pos);
        if (existing != null) previous = existing.previousState();

        boolean placed = mc.level.setBlock(pos, spec.state(), 2);
        if (placed) {
            ghosts.put(pos, new GhostEntry(pos, spec.state(), previous));
            touch();
        }
        return placed;
    }

    public static boolean delete(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;

        GhostEntry entry = ghosts.get(pos);
        if (entry == null) return false;

        if (!mc.level.getBlockState(pos).equals(entry.state())) {
            ghosts.remove(pos);
            return false;
        }

        ghosts.remove(pos);
        return mc.level.setBlock(pos, entry.previousState(), 2);
    }

    public static int deleteAll() {
        int removed = 0;
        for (BlockPos pos : List.copyOf(ghosts.keySet())) {
            if (delete(pos)) removed++;
        }
        return removed;
    }

    public static boolean isGhost(BlockPos pos) {
        return ghosts.containsKey(pos);
    }

    public static int count() {
        return ghosts.size();
    }

    public static List<String> summary(int limit) {
        List<String> lines = new ArrayList<>();
        for (GhostEntry entry : ghosts.values()) {
            if (lines.size() >= limit) break;

            BlockPos pos = entry.pos();
            StringBuilder sb = new StringBuilder()
                .append(pos.getX()).append(' ')
                .append(pos.getY()).append(' ')
                .append(pos.getZ()).append(' ')
                .append(BuiltInRegistries.BLOCK.getKey(entry.state().getBlock()));

            List<String> props = new ArrayList<>();
            entry.state().getValues().forEach(v -> props.add(v.property().getName() + "=" + v.valueName()));
            if (!props.isEmpty()) sb.append('[').append(String.join(",", props)).append(']');

            lines.add(sb.toString());
        }
        return lines;
    }
}
