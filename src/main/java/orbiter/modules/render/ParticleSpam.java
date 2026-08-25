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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;

import java.util.Random;

public class ParticleSpam extends CreativeSafetyModule {
        private final SettingGroup sgGeneral = settings.getDefaultGroup();
        private final SettingGroup sgArea = settings.createGroup("Area");
        private final SettingGroup sgTrails = settings.createGroup("Trails");

        private final Setting<ParticleMode> particleMode = sgGeneral.add(new EnumSetting.Builder<ParticleMode>()
                        .name("particle-mode")
                        .description("Which particles to spam.")
                        .defaultValue(ParticleMode.All)
                        .build());

        private final Setting<String> specificParticle = sgGeneral.add(new StringSetting.Builder()
                        .name("specific-particle")
                        .description("Particle ID when mode is Specific (e.g. flame, heart, explosion).")
                        .defaultValue("flame")
                        .visible(() -> particleMode.get() == ParticleMode.Specific)
                        .build());

        private final Setting<Integer> commandsPerTick = sgGeneral.add(new IntSetting.Builder()
                        .name("commands-per-tick")
                        .description("Number of /particle commands per tick.")
                        .defaultValue(5)
                        .min(1)
                        .sliderRange(1, 50)
                        .build());

        private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
                        .name("delay")
                        .description("Ticks between particle bursts.")
                        .defaultValue(1)
                        .min(0)
                        .sliderRange(0, 20)
                        .build());

        private final Setting<Integer> particleCount = sgGeneral.add(new IntSetting.Builder()
                        .name("count")
                        .description("Number of particles per command.")
                        .defaultValue(24)
                        .min(1)
                        .sliderRange(1, 1000)
                        .build());

        private final Setting<Integer> maxParticlesPerBurst = sgGeneral.add(new IntSetting.Builder()
                        .name("max-particles-per-burst")
                        .description("Hard cap on particles emitted by one burst to prevent visual buildup.")
                        .defaultValue(96)
                        .min(1)
                        .sliderRange(1, 500)
                        .build());

        private final Setting<Boolean> crispTrails = sgTrails.add(new BoolSetting.Builder()
                        .name("crisp-trails")
                        .description("Use one short, low-spread particle per projectile update.")
                        .defaultValue(true)
                        .build());

        private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
                        .name("speed")
                        .description("Particle speed/spread.")
                        .defaultValue(1.0)
                        .min(0.0)
                        .sliderRange(0.0, 10.0)
                        .build());

        private final Setting<Boolean> force = sgGeneral.add(new BoolSetting.Builder()
                        .name("force")
                        .description("Force particles to render for all players regardless of distance.")
                        .defaultValue(true)
                        .build());

        private final Setting<Integer> radius = sgArea.add(new IntSetting.Builder()
                        .name("radius")
                        .description("Radius around you to spawn particles.")
                        .defaultValue(10)
                        .min(1)
                        .sliderRange(1, 50)
                        .build());

        private final Setting<Boolean> randomPosition = sgArea.add(new BoolSetting.Builder()
                        .name("random-position")
                        .description("Spawn particles at random positions within radius.")
                        .defaultValue(true)
                        .build());

        private final Setting<Double> deltaX = sgArea.add(new DoubleSetting.Builder()
                        .name("delta-x").description("X spread.").defaultValue(2.0).min(0).sliderRange(0, 20).build());
        private final Setting<Double> deltaY = sgArea.add(new DoubleSetting.Builder()
                        .name("delta-y").description("Y spread.").defaultValue(2.0).min(0).sliderRange(0, 20).build());
        private final Setting<Double> deltaZ = sgArea.add(new DoubleSetting.Builder()
                        .name("delta-z").description("Z spread.").defaultValue(2.0).min(0).sliderRange(0, 20).build());

        private final Setting<Boolean> trailMode = sgTrails.add(new BoolSetting.Builder()
                        .name("trail-mode")
                        .description("Attach particle trails to flying projectiles (arrows, snowballs, etc).")
                        .defaultValue(false)
                        .build());

        private final Setting<String> trailParticle = sgTrails.add(new StringSetting.Builder()
                        .name("trail-particle")
                        .description("Particle to use for trails.")
                        .defaultValue("flame")
                        .visible(trailMode::get)
                        .build());

        private final Setting<Integer> trailDensity = sgTrails.add(new IntSetting.Builder()
                        .name("trail-density")
                        .description("Number of trail particles per projectile per tick.")
                        .defaultValue(3)
                        .min(1)
                        .sliderRange(1, 20)
                        .visible(trailMode::get)
                        .build());

        private static final String[] ALL_PARTICLES = {
                        "flame", "soul_fire_flame", "smoke", "large_smoke", "cloud", "explosion",
                        "explosion_emitter", "heart", "angry_villager", "happy_villager",
                        "crit", "enchanted_hit", "portal", "enchant", "end_rod", "witch",
                        "dripping_water", "dripping_lava", "splash", "fishing",
                        "dust", "dust_color_transition", "item_slime", "snowflake",
                        "cherry_leaves", "gust", "trial_spawner_detection", "vault_connection",
                        "infested", "small_gust", "firework", "note", "bubble",
                        "rain", "ash", "crimson_spore", "warped_spore", "mycelium",
                        "lava", "campfire_cosy_smoke", "campfire_signal_smoke", "totem_of_undying",
                        "dragon_breath", "sonic_boom", "sculk_soul", "sculk_charge",
                        "shriek", "electric_spark", "wax_on", "wax_off",
                        "scrape", "falling_honey", "landing_honey", "falling_nectar"
        };

        private final Random random = new Random();
        private int tickCounter = 0;
        private int particleIndex = 0;

        public ParticleSpam() {
                super("particle-spam",
                                "Spams particles in a radius. OP required.");
        }

        @Override
        public void onActivate() {
                tickCounter = 0;
                particleIndex = 0;
                safetyActivate();
        }

        @Override
        public void onDeactivate() {
                safetyDeactivate();
        }

        @EventHandler
        private void onTick(TickEvent.Post event) {
                if (mc.player == null || mc.player.connection == null)
                        return;

                tickCounter++;
                if (tickCounter < delay.get())
                        return;
                tickCounter = 0;

                int burstBudget = maxParticlesPerBurst.get();
                long deadline = sliceDeadline();
                for (int i = 0; i < commandsPerTick.get() && burstBudget > 0; i++) {
                        if ((i & 3) == 3 && System.nanoTime() >= deadline) break;
                        if (!GlobalSendLimiter.tryAcquireOne()) break;
                        String particle = particleWithOptions(getNextParticle());
                        int emitted = Math.min(particleCount.get(), burstBudget);
                        double x, y, z;

                        if (randomPosition.get()) {
                                x = mc.player.getX() + (random.nextDouble() * 2 - 1) * radius.get();
                                y = mc.player.getY() + (random.nextDouble() * 2 - 1) * radius.get();
                                z = mc.player.getZ() + (random.nextDouble() * 2 - 1) * radius.get();
                        } else {
                                x = mc.player.getX();
                                y = mc.player.getY();
                                z = mc.player.getZ();
                        }

                        String forceStr = force.get() ? "force" : "normal";
                        String cmd = CommandUtils.formatCommand("particle %s %.2f %.2f %.2f %.2f %.2f %.2f %.2f %d %s @a",
                                        particle, x, y, z, deltaX.get(), deltaY.get(), deltaZ.get(),
                                        speed.get(), emitted, forceStr);
                        FastSend.command(CommandUtils.vanilla(cmd));
                        burstBudget -= emitted;
                }

                if (trailMode.get() && mc.level != null && mc.player != null && mc.player.connection != null) {
                        String trailP = "minecraft:" + trailParticle.get().replace("minecraft:", "");
                        int trailBudget = Math.min(maxParticlesPerBurst.get(), 64);
                        for (Entity entity : ((meteordevelopment.meteorclient.mixin.LevelAccessor) mc.level).meteor$getEntityLookup().getAll()) {
                                if (trailBudget <= 0 || System.nanoTime() >= deadline) break;
                                if (entity instanceof Arrow || entity instanceof Snowball
                                                || entity instanceof ThrownEgg || entity instanceof ThrownTrident
                                                || entity instanceof Fireball) {
                                        int trailLimit = Math.min(crispTrails.get() ? 1 : trailDensity.get(), trailBudget);
                                        for (int t = 0; t < trailLimit; t++) {
                                                if (!GlobalSendLimiter.tryAcquireOne()) break;
                                                String trailCmd = CommandUtils.formatCommand(
                                                                "particle %s %.2f %.2f %.2f %s %s %s 0.01 1 force @a",
                                                                trailP, entity.getX(), entity.getY(), entity.getZ(),
                                                                crispTrails.get() ? "0.02" : "0.1",
                                                                crispTrails.get() ? "0.02" : "0.1",
                                                                crispTrails.get() ? "0.02" : "0.1");
                                                mc.player.connection.sendCommand(CommandUtils.vanilla(trailCmd));
                                                trailBudget--;
                                        }
                                }
                        }
                }
        }

        private String getNextParticle() {
                if (particleMode.get() == ParticleMode.Specific) {
                        return "minecraft:" + specificParticle.get().replace("minecraft:", "");
                }

                String p = "minecraft:" + ALL_PARTICLES[particleIndex % ALL_PARTICLES.length];
                particleIndex++;
                return p;
        }

        private static String particleWithOptions(String particleId) {
                return switch (particleId) {
                        case "minecraft:dust" -> particleId + "{scale:1.0,color:[1.0,0.0,0.0]}";
                        case "minecraft:dust_color_transition" ->
                                particleId + "{scale:1.0,from_color:[1.0,0.0,0.0],to_color:[0.0,1.0,1.0]}";
                        case "minecraft:sculk_charge" -> particleId + "{roll:0.3f}";
                        case "minecraft:shriek" -> particleId + "{delay:0}";
                        default -> particleId;
                };
        }

        public enum ParticleMode {
                All,
                Specific
        }
}
