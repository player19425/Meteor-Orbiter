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
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class SwdRegionStorage implements AutoCloseable {

    private final Path directory;
    private final RegionStorageInfo storageKey;
    private final Map<Long, RegionFile> regionCache = new HashMap<>();
    private boolean closed;

    public SwdRegionStorage(Path directory, ResourceKey<Level> dimension) {
        this.directory = directory;
        this.storageKey = new RegionStorageInfo("orbiter", dimension, "chunk");
        try {
            java.nio.file.Files.createDirectories(directory);
        } catch (IOException ignored) {}
    }

    public void write(ChunkPos pos, CompoundTag nbt) throws IOException {
        synchronized (this) {
            RegionFile rf = getOrOpenRegion(pos);
            try (DataOutputStream out = rf.getChunkDataOutputStream(pos)) {
                NbtIo.write(nbt, (DataOutput) out);
            }
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
        RegionFile rf = regionCache.get(regionKey);
        if (rf == null) {
            Path path = directory.resolve("r." + pos.getRegionX() + "." + pos.getRegionZ() + ".mca");
            rf = new RegionFile(storageKey, path, directory, false);
            regionCache.put(regionKey, rf);
        }
        return rf;
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
