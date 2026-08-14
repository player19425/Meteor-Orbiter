package orbiter.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import orbiter.modules.misc.ServerProtect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(SoundSystem.class)
public class CrashFixerSoundSystemMixin {

    @Unique
    private static final Deque<Long> recentGlobalPlays = new ArrayDeque<>();
    @Unique
    private static final Map<Identifier, Deque<Long>> recentById = new ConcurrentHashMap<>();
    @Unique
    private int soundsThisTick = 0;
    @Unique
    private int blockSoundsThisTick = 0;

    @Inject(method = "tick", at = @At("HEAD"))
    private void orbiter$resetSoundCounters(CallbackInfo ci) {
        this.soundsThisTick = 0;
        this.blockSoundsThisTick = 0;
    }

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)Lnet/minecraft/client/sound/SoundSystem$PlayResult;",
            at = @At("HEAD"), cancellable = true)
    private void orbiter$throttleSoundPlays(SoundInstance sound, CallbackInfoReturnable<SoundSystem.PlayResult> cir) {
        ServerProtect mod = Modules.get() == null ? null : Modules.get().get(ServerProtect.class);
        if (mod == null || !mod.isActive() || !mod.shouldThrottleSounds()) return;
        if (sound == null) return;

        if (sound.getCategory() == SoundCategory.BLOCKS) {
            if (this.blockSoundsThisTick >= mod.getMaxBlockSoundsPerTick()) {
                cir.cancel();
                return;
            }
            this.blockSoundsThisTick++;
        }

        if (this.soundsThisTick >= mod.getMaxSoundsPerTick()) {
            cir.cancel();
            return;
        }
        this.soundsThisTick++;

        long now = System.nanoTime();
        long windowNanos = Math.max(1L, mod.getSoundWindowMs()) * 1000000L;
        synchronized (recentGlobalPlays) {
            while (!recentGlobalPlays.isEmpty() && now - recentGlobalPlays.peekFirst() > windowNanos) {
                recentGlobalPlays.removeFirst();
            }
            if (recentGlobalPlays.size() >= mod.getMaxPlaysPerWindow()) {
                cir.cancel();
                return;
            }
            recentGlobalPlays.addLast(now);
        }

        Identifier id = sound.getId();
        if (id == null) return;
        Deque<Long> queue = recentById.computeIfAbsent(id, k -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && now - queue.peekFirst() > windowNanos) {
                queue.removeFirst();
            }
            if (recentById.size() > mod.getSoundCleanupThreshold() && queue.isEmpty()) {
                recentById.remove(id, queue);
            }
            while (queue.size() > mod.getMaxSameSoundPerWindow()) {
                queue.pollFirst();
            }
            if (queue.size() >= mod.getMaxSameSoundPerWindow()) {
                cir.cancel();
                return;
            }
            queue.addLast(now);
        }
    }
}
