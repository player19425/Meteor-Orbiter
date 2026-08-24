package orbiter.modules.render;

import orbiter.Orbiter;
import orbiter.modules.CreativeSafetyModule;
import orbiter.util.CommandUtils;
import orbiter.util.FastSend;
import orbiter.util.GlobalSendLimiter;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PlaySoundSpam extends CreativeSafetyModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<SoundMode> soundMode = sgGeneral.add(new EnumSetting.Builder<SoundMode>()
            .name("sound-mode")
            .description("Which sounds to spam.")
            .defaultValue(SoundMode.All)
            .build());

    private final Setting<String> specificSound = sgGeneral.add(new StringSetting.Builder()
            .name("specific-sound")
            .description("Sound ID when mode is Specific.")
            .defaultValue("minecraft:entity.wither.spawn")
            .visible(() -> soundMode.get() == SoundMode.Specific)
            .build());

    private final Setting<Integer> commandsPerTick = sgGeneral.add(new IntSetting.Builder()
            .name("commands-per-tick")
            .description("Number of /playsound commands per tick.")
            .defaultValue(10)
            .min(1)
            .sliderRange(1, 50)
            .build());

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
            .name("delay")
            .description("Ticks between sound bursts.")
            .defaultValue(1)
            .min(0)
            .sliderRange(0, 20)
            .build());

    private final Setting<Double> volume = sgGeneral.add(new DoubleSetting.Builder()
            .name("volume")
            .description("Sound volume.")
            .defaultValue(1.0)
            .min(0.0)
            .sliderRange(0.0, 100.0)
            .build());

    private final Setting<Double> pitch = sgGeneral.add(new DoubleSetting.Builder()
            .name("pitch")
            .description("Sound pitch.")
            .defaultValue(1.0)
            .min(0.0)
            .sliderRange(0.0, 2.0)
            .build());

    private final Setting<Boolean> randomPitch = sgGeneral.add(new BoolSetting.Builder()
            .name("random-pitch")
            .description("Randomize pitch for each sound.")
            .defaultValue(false)
            .build());

    private final Setting<String> target = sgGeneral.add(new StringSetting.Builder()
            .name("target")
            .description("Target selector for /playsound.")
            .defaultValue("@a")
            .build());

    private final Setting<SoundSource> soundSource = sgGeneral.add(new EnumSetting.Builder<SoundSource>()
            .name("sound-source")
            .description("Sound source/channel.")
            .defaultValue(SoundSource.Master)
            .build());

    private List<String> allSounds;
    private int soundIndex = 0;
    private int tickCounter = 0;
    private final Random random = new Random();

    public PlaySoundSpam() {
        super("playsound-spam",
                "Spams every sound. OP required.");
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
        soundIndex = 0;
        safetyActivate();

        allSounds = new ArrayList<>();
        for (SoundEvent sound : BuiltInRegistries.SOUND_EVENT) {
            Identifier id = BuiltInRegistries.SOUND_EVENT.getKey(sound);
            if (id != null) {
                allSounds.add(id.toString());
            }
        }

        info("Loaded " + allSounds.size() + " sounds. Spamming...");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.player.connection == null || allSounds == null || allSounds.isEmpty())
            return;

        tickCounter++;
        if (tickCounter < delay.get())
            return;
        tickCounter = 0;

        for (int i = 0; i < commandsPerTick.get(); i++) {
            if (!GlobalSendLimiter.tryAcquireOne()) break;
            String sound;
            if (soundMode.get() == SoundMode.Specific) {
                sound = specificSound.get();
            } else {
                sound = allSounds.get(soundIndex % allSounds.size());
                soundIndex++;
            }

            double p = randomPitch.get() ? 0.5 + random.nextDouble() * 1.5 : pitch.get();
            String sourceStr = soundSource.get().name().toLowerCase();

            String cmd = CommandUtils.formatCommand("playsound %s %s %s ~ ~ ~ %.2f %.2f",
                    sound, sourceStr, target.get(), volume.get(), p);
            FastSend.command(CommandUtils.vanilla(cmd));
        }
    }

    @Override
    public void onDeactivate() {
        safetyDeactivate();
        allSounds = null;
        soundIndex = 0;
    }

    public enum SoundMode {
        All,
        Specific
    }

    public enum SoundSource {
        Master,
        Music,
        Record,
        Weather,
        Block,
        Hostile,
        Neutral,
        Player,
        Ambient,
        Voice
    }
}
