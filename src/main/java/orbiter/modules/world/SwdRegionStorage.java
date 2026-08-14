package orbiter.modules.world;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.RegionFile;
import net.minecraft.world.storage.StorageKey;

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
    private final StorageKey storageKey;
    private final Map<Long, RegionFile> regionCache = new HashMap<>();

    public SwdRegionStorage(Path directory, RegistryKey<World> dimension) {
        this.directory = directory;
        this.storageKey = new StorageKey("orbiter", dimension, "chunk");
        try {
            java.nio.file.Files.createDirectories(directory);
        } catch (IOException ignored) {}
    }

    public void write(ChunkPos pos, NbtCompound nbt) throws IOException {
        RegionFile rf = getOrOpenRegion(pos);
        try (DataOutputStream out = rf.getChunkOutputStream(pos)) {
            NbtIo.write(nbt, (DataOutput) out);
        }
    }

    public NbtCompound read(ChunkPos pos) throws IOException {
        RegionFile rf = getOrOpenRegion(pos);
        try (DataInputStream in = rf.getChunkInputStream(pos)) {
            if (in == null) return null;
            return NbtIo.readCompound((DataInput) in);
        }
    }

    private RegionFile getOrOpenRegion(ChunkPos pos) throws IOException {
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
        for (RegionFile rf : regionCache.values()) {
            try {
                rf.close();
            } catch (IOException e) {
                if (first == null) first = e;
            }
        }
        regionCache.clear();
        if (first != null) throw first;
    }

    private static long packRegion(int rx, int rz) {
        return ((long) rx << 32) | (rz & 0xFFFFFFFFL);
    }
}
