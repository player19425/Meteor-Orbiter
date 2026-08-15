package orbiter.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import orbiter.Orbiter;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OrbiterModuleOrder {

    private static final String FILE_NAME = "orbiter-module-order.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Integer>>() {}.getType();

    private static final Map<String, Integer> orderMap = new LinkedHashMap<>();

    private static Path configPath;

    private OrbiterModuleOrder() {

    }

    private static Path resolveConfigPath() {
        if (configPath != null) return configPath;
        try {
            Minecraft mc = Minecraft.getInstance();
            Path dir = mc.gameDirectory.toPath();
            configPath = dir.resolve(FILE_NAME);
        } catch (Exception e) {
            Orbiter.LOG.warn("Failed to resolve Orbiter config directory, using fallback", e);
            configPath = Path.of(FILE_NAME);
        }
        return configPath;
    }

    public static void load() {
        orderMap.clear();
        Path file = resolveConfigPath();
        if (!Files.exists(file)) {
            Orbiter.LOG.info("No module order file found at {}, starting fresh", file);
            return;
        }

        try {
            String json = Files.readString(file);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject orders = root.has("orders") ? root.getAsJsonObject("orders") : root;

            for (Map.Entry<String, JsonElement> entry : orders.entrySet()) {
                if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber()) {
                    orderMap.put(entry.getKey(), entry.getValue().getAsInt());
                }
            }

            Orbiter.LOG.info("Loaded {} module order entries from {}", orderMap.size(), file);
        } catch (Exception e) {
            Orbiter.LOG.warn("Failed to load module order from {}, starting fresh", file, e);
            orderMap.clear();
        }
    }

    public static void save() {
        Path file = resolveConfigPath();
        try {
            Files.createDirectories(file.getParent());

            JsonObject root = new JsonObject();
            JsonObject orders = new JsonObject();
            for (Map.Entry<String, Integer> entry : orderMap.entrySet()) {
                orders.addProperty(entry.getKey(), entry.getValue());
            }
            root.add("orders", orders);

            Files.writeString(file, GSON.toJson(root));
            Orbiter.LOG.info("Saved {} module order entries to {}", orderMap.size(), file);
        } catch (IOException e) {
            Orbiter.LOG.warn("Failed to save module order to {}", file, e);
        }
    }

    public static int getOrder(String moduleName) {
        return orderMap.getOrDefault(moduleName, 0);
    }

    public static boolean hasOrder(String moduleName) {
        return orderMap.containsKey(moduleName);
    }

    public static Map<String, Integer> getAll() {
        return Collections.unmodifiableMap(orderMap);
    }

    public static List<String> getOrdered() {
        return orderMap.entrySet().stream()
            .sorted(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    public static List<String> getOrderedForCategory(List<String> moduleNames) {
        List<ModuleOrderEntry> entries = new ArrayList<>();
        for (String name : moduleNames) {
            int order = orderMap.getOrDefault(name, Integer.MAX_VALUE);
            entries.add(new ModuleOrderEntry(name, order));
        }
        entries.sort((a, b) -> Integer.compare(a.order, b.order));
        return entries.stream().map(e -> e.name).collect(Collectors.toList());
    }

    public static void setOrder(String moduleName, int position) {
        orderMap.put(moduleName, position);
        save();
    }

    public static boolean moveUp(String moduleName) {
        Integer current = orderMap.get(moduleName);
        if (current == null) return false;
        if (current <= Integer.MIN_VALUE + 1) return false;
        orderMap.put(moduleName, current - 1);
        save();
        return true;
    }

    public static boolean moveDown(String moduleName) {
        Integer current = orderMap.get(moduleName);
        if (current == null) return false;
        if (current >= Integer.MAX_VALUE - 1) return false;
        orderMap.put(moduleName, current + 1);
        save();
        return true;
    }

    public static boolean moveTop(String moduleName) {
        if (!orderMap.containsKey(moduleName)) return false;
        int minOrder = orderMap.values().stream().mapToInt(Integer::intValue).min().orElse(0);
        orderMap.put(moduleName, minOrder - 1);
        save();
        return true;
    }

    public static boolean moveBottom(String moduleName) {
        if (!orderMap.containsKey(moduleName)) return false;
        int maxOrder = orderMap.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        orderMap.put(moduleName, maxOrder + 1);
        save();
        return true;
    }

    public static boolean removeOrder(String moduleName) {
        if (!orderMap.containsKey(moduleName)) return false;
        orderMap.remove(moduleName);
        save();
        return true;
    }

    public static void clearAll() {
        orderMap.clear();
        save();
    }

    private static class ModuleOrderEntry {
        final String name;
        final int order;

        ModuleOrderEntry(String name, int order) {
            this.name = name;
            this.order = order;
        }
    }
}
