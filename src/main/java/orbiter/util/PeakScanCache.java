package orbiter.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PeakScanCache {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_ENTRIES = 128;
    private static final long MAX_BYTES = 8L * 1024L * 1024L;

    public static final class PluginData {
        public String name = "";
        public String category = "";
        public String evidence = "";
        public String confidence = "";
        public List<String> commands = new ArrayList<>();
    }

    public static final class Entry {
        public String address = "";
        public String name = "";
        public String brand = "";
        public String version = "";
        public String status = "COMPLETE";
        public long scannedAtMs = 0L;
        public int totalProbes = 0;
        public int sentProbes = 0;
        public List<PluginData> plugins = new ArrayList<>();
    }

    private static final class CacheFile {
        int version = 1;
        Map<String, Entry> entries = new LinkedHashMap<>();
    }

    private PeakScanCache() {
    }

    private static File file() {
        return new File("peakscan_cache.json");
    }

    private static File backupFile() {
        return new File("peakscan_cache.json.bak");
    }

    public static synchronized Entry get(String address) {
        if (address == null || address.isBlank()) return null;
        CacheFile cache = read(file());
        if (cache == null || cache.entries == null) return null;

        Entry newest = null;
        for (Map.Entry<String, Entry> item : cache.entries.entrySet()) {
            String key = item.getKey();
            if (!key.equals(address) && !key.startsWith(address + "|")) continue;
            if (newest == null || item.getValue().scannedAtMs > newest.scannedAtMs) {
                newest = item.getValue();
            }
        }
        return newest;
    }

    public static synchronized void put(String address, Entry entry) {
        if (address == null || address.isBlank() || entry == null) return;
        CacheFile cache = read(file());
        if (cache == null) cache = new CacheFile();
        entry.address = address;
        cache.entries.put(address, entry);
        trim(cache);
        write(cache);
    }

    private static void trim(CacheFile cache) {
        if (cache.entries.isEmpty()) return;

        List<Map.Entry<String, Entry>> sorted = new ArrayList<>(cache.entries.entrySet());
        sorted.sort(Comparator.comparingLong(item -> item.getValue().scannedAtMs));
        java.util.Collections.reverse(sorted);

        LinkedHashMap<String, Entry> retained = new LinkedHashMap<>();
        long bytes = 32L;
        for (Map.Entry<String, Entry> item : sorted) {
            if (retained.size() >= MAX_ENTRIES) break;
            long size = GSON.toJson(item.getValue()).getBytes(StandardCharsets.UTF_8).length;
            if (!retained.isEmpty() && bytes + size > MAX_BYTES) break;
            retained.put(item.getKey(), item.getValue());
            bytes += size;
        }

        cache.entries.clear();
        cache.entries.putAll(retained);
    }

    private static CacheFile read(File source) {
        if (source == null || !source.exists()) return null;
        try (FileReader reader = new FileReader(source)) {
            return GSON.fromJson(reader, CacheFile.class);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void write(CacheFile cache) {
        try {
            File target = file();
            File tmp = new File(target.getAbsoluteFile().getParentFile(), "peakscan_cache.json.tmp");
            try (FileWriter writer = new FileWriter(tmp)) {
                writer.write(GSON.toJson(cache));
            }
            if (target.exists()) {
                Files.copy(target.toPath(), backupFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable ignored) {
        }
    }
}
