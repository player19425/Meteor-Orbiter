package orbiter.modules.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class SwdRegionStorage implements AutoCloseable {

    private static final int MAX_OPEN_REGIONS = 64;

    private final Path directory;
    private final RegionStorageInfo storageKey;
    private final Map<Long, RegionFile> regionCache = new LinkedHashMap<>(16, 0.75f, true);
    private boolean closed;

    public SwdRegionStorage(Path directory, ResourceKey<Level> dimension) {
        this.directory = directory;
        this.storageKey = new RegionStorageInfo("orbiter", dimension, "chunk");
        try {
            java.nio.file.Files.createDirectories(directory);
        } catch (IOException ignored) {}
    }

    public long write(ChunkPos pos, CompoundTag nbt) throws IOException {
        synchronized (this) {
            RegionFile rf = getOrOpenRegion(pos);
            DataOutputStream out = rf.getChunkDataOutputStream(pos);
            CountingDataOutput counting = new CountingDataOutput(out);
            try {
                NbtIo.write(nbt, counting);
            } finally {
                out.close();
            }
            return counting.count();
        }
    }

    private static final class CountingDataOutput extends DataOutputStream {
        private long count;

        CountingDataOutput(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int b) throws IOException {
            count++;
            super.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            count += len;
            super.write(b, off, len);
        }

        long count() {
            return count;
        }
    }

    public CompoundTag read(ChunkPos pos) throws IOException {
        synchronized (this) {
            RegionFile rf = getOrOpenRegion(pos);
            try (DataInputStream in = rf.getChunkDataInputStream(pos)) {
                if (in == null) return null;
                return NbtIo.read((DataInput) in);
            }
        }
    }

    private RegionFile getOrOpenRegion(ChunkPos pos) throws IOException {
        if (closed) throw new IOException("Region storage is closed");
        long regionKey = packRegion(pos.getRegionX(), pos.getRegionZ());
        RegionFile cached = regionCache.get(regionKey);
        if (cached != null) return cached;
        evictOldestRegions();
        Path path = directory.resolve("r." + pos.getRegionX() + "." + pos.getRegionZ() + ".mca");
        RegionFile opened = new RegionFile(storageKey, path, directory, false);
        regionCache.put(regionKey, opened);
        return opened;
    }

    private void evictOldestRegions() {
        while (regionCache.size() >= MAX_OPEN_REGIONS) {
            Iterator<Map.Entry<Long, RegionFile>> iterator = regionCache.entrySet().iterator();
            Map.Entry<Long, RegionFile> eldest = iterator.next();
            iterator.remove();
            try {
                eldest.getValue().close();
            } catch (IOException ignored) {}
        }
    }

    @Override
    public void close() throws IOException {
        IOException first = null;
        synchronized (this) {
            if (closed) return;
            closed = true;
            for (RegionFile rf : regionCache.values()) {
                try {
                    rf.close();
                } catch (IOException e) {
                    if (first == null) first = e;
                }
            }
            regionCache.clear();
        }
        if (first != null) throw first;
    }

    private static long packRegion(int rx, int rz) {
        return ((long) rx << 32) | (rz & 0xFFFFFFFFL);
    }
}
