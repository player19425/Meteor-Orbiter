package orbiter.modules.render;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.phys.Vec3;
import orbiter.modules.CreativeSafetyModule;
import orbiter.util.CommandUtils;

import java.util.Locale;

public final class ParticleControl extends CreativeSafetyModule {
    public enum Shape {
        Cube, Sphere, Ring, Torus, Helix, DoubleHelix, Pyramid, Diamond,
        Cone, Cylinder, Spiral, Galaxy, Heart, Star, Wave, Atom
    }

    public enum ParticleStyle {
        Flame("minecraft:flame"),
        SoulFlame("minecraft:soul_fire_flame"),
        EndRod("minecraft:end_rod"),
        Enchant("minecraft:enchant"),
        Crit("minecraft:crit"),
        Heart("minecraft:heart"),
        Portal("minecraft:portal"),
        ElectricSpark("minecraft:electric_spark"),
        Totem("minecraft:totem_of_undying");

        private final String id;

        ParticleStyle(String id) {
            this.id = id;
        }
    }

    public enum CenterTargetMode { Self, NearestPlayer, PlayerName, Selector, FixedPosition }
    public enum EmissionMode { Batched, Precise }
    public enum TrailMode { Crisp, Balanced, Native }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTarget = settings.createGroup("Animation Target");
    private final SettingGroup sgRotation = settings.createGroup("Rotation");
    private final SettingGroup sgTransform = settings.createGroup("Transform");
    private final SettingGroup sgRate = settings.createGroup("Command Optimization");

    private final Setting<Shape> shape = sgGeneral.add(new EnumSetting.Builder<Shape>()
        .name("shape").description("Geometric particle shape.").defaultValue(Shape.Torus).build());
    private final Setting<ParticleStyle> particleStyle = sgGeneral.add(new EnumSetting.Builder<ParticleStyle>()
        .name("particle-type").description("Particle used by the server command.").defaultValue(ParticleStyle.EndRod).build());
    private final Setting<Double> radius = sgGeneral.add(new DoubleSetting.Builder()
        .name("radius").description("Shape radius.").defaultValue(2.0).min(0.25).sliderRange(0.25, 10.0).build());
    private final Setting<Double> quality = sgGeneral.add(new DoubleSetting.Builder()
        .name("quality").description("Virtual geometry resolution. Higher values improve temporal sampling without directly increasing commands.")
        .defaultValue(1.0).min(0.1).sliderRange(0.1, 4.0).build());
    private final Setting<Double> animationSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("animation-speed").description("Overall animation speed multiplier.").defaultValue(1.0).min(0.0).sliderRange(0.0, 5.0).build());
    private final Setting<Boolean> forceParticles = sgGeneral.add(new BoolSetting.Builder()
        .name("force-particles").description("Use the force particle visibility mode.").defaultValue(true).build());
    private final Setting<String> viewers = sgGeneral.add(new StringSetting.Builder()
        .name("viewers").description("Who can see the particles. Accepts a player name or a safe player selector such as @a or @a[tag=show].")
        .defaultValue("@a").build());

    private final Setting<CenterTargetMode> centerTargetMode = sgTarget.add(new EnumSetting.Builder<CenterTargetMode>()
        .name("center-target-mode").description("Who or what the animation follows. This is independent from viewers.")
        .defaultValue(CenterTargetMode.Self).build());
    private final Setting<String> centerPlayerName = sgTarget.add(new StringSetting.Builder()
        .name("center-player-name").description("Exact player name used as the animation center.").defaultValue("")
        .visible(() -> centerTargetMode.get() == CenterTargetMode.PlayerName).build());
    private final Setting<String> centerSelector = sgTarget.add(new StringSetting.Builder()
        .name("center-selector").description("Player selector used as the animation center. Multiple matches create one shape per player.")
        .defaultValue("@a[tag=particle-target]")
        .visible(() -> centerTargetMode.get() == CenterTargetMode.Selector).build());
    private final Setting<Double> nearestRange = sgTarget.add(new DoubleSetting.Builder()
        .name("nearest-range").description("Maximum range when centering on the nearest other player.").defaultValue(64).min(1).sliderRange(1, 256)
        .visible(() -> centerTargetMode.get() == CenterTargetMode.NearestPlayer).build());

    private final Setting<Double> rotateXSpeed = sgRotation.add(new DoubleSetting.Builder()
        .name("x-speed").description("X-axis degrees per animation tick.").defaultValue(0.8).sliderRange(-10, 10).build());
    private final Setting<Double> rotateYSpeed = sgRotation.add(new DoubleSetting.Builder()
        .name("y-speed").description("Y-axis degrees per animation tick.").defaultValue(2.0).sliderRange(-10, 10).build());
    private final Setting<Double> rotateZSpeed = sgRotation.add(new DoubleSetting.Builder()
        .name("z-speed").description("Z-axis degrees per animation tick.").defaultValue(0.4).sliderRange(-10, 10).build());
    private final Setting<Double> rotateX = sgRotation.add(new DoubleSetting.Builder()
        .name("x-offset").description("Fixed X-axis rotation.").defaultValue(0.0).sliderRange(-180, 180).build());
    private final Setting<Double> rotateY = sgRotation.add(new DoubleSetting.Builder()
        .name("y-offset").description("Fixed Y-axis rotation.").defaultValue(0.0).sliderRange(-180, 180).build());
    private final Setting<Double> rotateZ = sgRotation.add(new DoubleSetting.Builder()
        .name("z-offset").description("Fixed Z-axis rotation.").defaultValue(0.0).sliderRange(-180, 180).build());

    private final Setting<Double> translateX = sgTransform.add(new DoubleSetting.Builder()
        .name("translate-x").description("X offset from the selected animation center.").defaultValue(0.0).sliderRange(-10, 10).build());
    private final Setting<Double> translateY = sgTransform.add(new DoubleSetting.Builder()
        .name("translate-y").description("Y offset from the selected animation center.").defaultValue(1.0).sliderRange(-10, 10).build());
    private final Setting<Double> translateZ = sgTransform.add(new DoubleSetting.Builder()
        .name("translate-z").description("Z offset from the selected animation center.").defaultValue(0.0).sliderRange(-10, 10).build());

    private final Setting<EmissionMode> emissionMode = sgRate.add(new EnumSetting.Builder<EmissionMode>()
        .name("emission-mode").description("Batched emits several particles per anchor; Precise emits one exact particle per command.")
        .defaultValue(EmissionMode.Batched).build());
    private final Setting<TrailMode> trailMode = sgRate.add(new EnumSetting.Builder<TrailMode>()
        .name("trail-mode").description("Controls visual buildup. Crisp uses fewer particles and shorter client-visible trails.")
        .defaultValue(TrailMode.Crisp).build());
    private final Setting<Integer> particlesPerCommand = sgRate.add(new IntSetting.Builder()
        .name("particles-per-command").description("Particles emitted by each command in Batched mode.").defaultValue(4).min(2).sliderRange(2, 32)
        .visible(() -> emissionMode.get() == EmissionMode.Batched).build());
    private final Setting<Double> anchorSpread = sgRate.add(new DoubleSetting.Builder()
        .name("anchor-spread").description("Small random spread around each sampled anchor in Batched mode.").defaultValue(0.035).min(0.0).sliderRange(0.0, 0.5)
        .visible(() -> emissionMode.get() == EmissionMode.Batched).build());
    private final Setting<Integer> maxCommandsPerBatch = sgRate.add(new IntSetting.Builder()
        .name("max-commands-per-batch").description("Hard limit for /particle commands sent in one update.").defaultValue(8).min(1).sliderRange(1, 32).build());
    private final Setting<Integer> delayTicks = sgRate.add(new IntSetting.Builder()
        .name("delay-ticks").description("Ticks between command batches. Two ticks substantially lowers command traffic while retaining smooth trails.")
        .defaultValue(2).min(1).sliderRange(1, 20).build());

    private static final double GOLDEN_FRACTION = 0.6180339887498949;
    private double phase;
    private double samplePhase;
    private Vec3 fixedCenter;
    private int tickCounter;

    public ParticleControl() {
        super("particle-control", "Particle shapes around a target.");
    }

    @Override
    public void onActivate() {
        phase = 0;
        samplePhase = 0;
        tickCounter = 0;
        fixedCenter = mc.player == null ? null : playerPosition();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null || mc.player.connection == null) return;
        if (++tickCounter < delayTicks.get()) return;
        tickCounter = 0;

        String target = resolveCenterTarget();
        if (target == null && centerTargetMode.get() != CenterTargetMode.FixedPosition) return;
        if (centerTargetMode.get() == CenterTargetMode.FixedPosition && fixedCenter == null) fixedCenter = playerPosition();

        phase += animationSpeed.get() * delayTicks.get();
        samplePhase = fractional(samplePhase + GOLDEN_FRACTION);

        int pointCount = Math.max(12, (int) Math.round(basePointCount(shape.get()) * quality.get()));
        int commands = Math.min(pointCount, maxCommandsPerBatch.get());
        double ax = Math.toRadians(rotateX.get() + phase * rotateXSpeed.get());
        double ay = Math.toRadians(rotateY.get() + phase * rotateYSpeed.get());
        double az = Math.toRadians(rotateZ.get() + phase * rotateZSpeed.get());
        String mode = forceParticles.get() ? "force" : "normal";
        String viewerSelector = safePlayerTarget(viewers.get(), true);
        if (viewerSelector == null) {
            warning("Viewer target rejected. Use an exact player name or a whitespace-free @a/@p/@r/@s selector.");
            toggle();
            return;
        }

        int particleCount = emissionMode.get() == EmissionMode.Batched ? particlesPerCommand.get() : 1;
        double spread = emissionMode.get() == EmissionMode.Batched ? anchorSpread.get() : 0.0;
        if (trailMode.get() == TrailMode.Crisp) {
            particleCount = Math.min(particleCount, 2);
            spread = Math.min(spread, 0.012);
        } else if (trailMode.get() == TrailMode.Balanced) {
            particleCount = Math.min(particleCount, 4);
            spread = Math.min(spread, 0.035);
        }

        for (int n = 0; n < commands; n++) {
            double distributed = fractional((n + samplePhase) / commands);
            int index = Math.min(pointCount - 1, (int) Math.floor(distributed * pointCount));
            Vec3 point = rotate(pointFor(shape.get(), index, pointCount, radius.get(), phase), ax, ay, az)
                .add(translateX.get(), translateY.get(), translateZ.get());

            String command;
            if (centerTargetMode.get() == CenterTargetMode.FixedPosition) {
                command = absoluteParticleCommand(fixedCenter.add(point), spread, particleCount, mode, viewerSelector);
            } else {
                command = relativeParticleCommand(target, point, spread, particleCount, mode, viewerSelector);
            }
            mc.player.connection.sendCommand(CommandUtils.vanilla(command));
        }
    }

    private String resolveCenterTarget() {
        return switch (centerTargetMode.get()) {
            case Self -> mc.player.getGameProfile().name();
            case NearestPlayer -> CommandUtils.formatCommand(
                "@p[name=!%s,distance=..%.2f]", mc.player.getGameProfile().name(), nearestRange.get());
            case PlayerName -> rejectInvalidCenter(safePlayerTarget(centerPlayerName.get(), false), "player name");
            case Selector -> rejectInvalidCenter(safeCenterSelector(centerSelector.get()), "center selector");
            case FixedPosition -> null;
        };
    }

    private String rejectInvalidCenter(String value, String label) {
        if (value != null) return value;
        warning("Invalid " + label + ". The module has been disabled before sending any command.");
        toggle();
        return null;
    }

    private String relativeParticleCommand(String target, Vec3 point, double spread, int count, String mode, String viewers) {
        return CommandUtils.formatCommand(
            "execute at %s run particle %s ~%.3f ~%.3f ~%.3f %.3f %.3f %.3f 0 %d %s %s",
            target, particleStyle.get().id, point.x, point.y, point.z,
            spread, spread, spread, count, mode, viewers
        );
    }

    private String absoluteParticleCommand(Vec3 point, double spread, int count, String mode, String viewers) {
        return CommandUtils.formatCommand(
            "particle %s %.3f %.3f %.3f %.3f %.3f %.3f 0 %d %s %s",
            particleStyle.get().id, point.x, point.y, point.z,
            spread, spread, spread, count, mode, viewers
        );
    }

    private Vec3 playerPosition() {
        return new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());
    }

    private String safePlayerTarget(String value, boolean allowSelfSelector) {
        if (value == null) return null;
        String trimmed = value.trim();
        String name = safePlayerName(trimmed);
        if (name != null) return name;
        if (!trimmed.startsWith("@")) return null;
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (isSelectorType(lower, '@', 's') && allowSelfSelector && selectorCharactersAreSafe(trimmed)) return trimmed;
        if ((isSelectorType(lower, '@', 'a') || isSelectorType(lower, '@', 'p') || isSelectorType(lower, '@', 'r'))
            && selectorCharactersAreSafe(trimmed)) return trimmed;
        return null;
    }

    private boolean isSelectorType(String value, char prefix, char type) {
        return value.length() == 2 && value.charAt(0) == prefix && value.charAt(1) == type
            || value.length() > 2 && value.charAt(0) == prefix && value.charAt(1) == type && value.charAt(2) == '[';
    }

    private String safeCenterSelector(String value) {
        String safe = safePlayerTarget(value, false);
        return safe != null && safe.startsWith("@") ? safe : null;
    }

    private boolean selectorCharactersAreSafe(String value) {
        if (value.isEmpty() || value.length() > 180) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c)) continue;
            if ("@_[],:.=!+-.".indexOf(c) < 0) return false;
        }
        return true;
    }

    private String safePlayerName(String value) {
        if (value == null || value.isEmpty() || value.length() > 16) return null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_')) return null;
        }
        return value;
    }

    private int basePointCount(Shape value) {
        return switch (value) {
            case Cube -> 120;
            case Sphere -> 144;
            case Ring -> 96;
            case Torus -> 192;
            case Helix, DoubleHelix -> 144;
            case Pyramid, Diamond -> 120;
            case Cone, Cylinder -> 144;
            case Spiral, Galaxy, Heart, Star, Wave -> 128;
            case Atom -> 168;
        };
    }

    private Vec3 pointFor(Shape value, int index, int count, double r, double time) {
        double t = count <= 1 ? 0 : index / (double) count;
        return switch (value) {
            case Sphere -> {
                double golden = Math.PI * (3.0 - Math.sqrt(5.0));
                double y = 1.0 - 2.0 * ((index + 0.5) / count);
                double radial = Math.sqrt(Math.max(0, 1.0 - y * y));
                double a = golden * index;
                yield new Vec3(Math.cos(a) * radial * r, y * r, Math.sin(a) * radial * r);
            }
            case Ring -> new Vec3(Math.cos(t * Math.PI * 2) * r, 0, Math.sin(t * Math.PI * 2) * r);
            case Torus -> {
                double major = t * Math.PI * 2;
                double minor = fractional(index * GOLDEN_FRACTION + time * 0.008) * Math.PI * 2;
                double tube = r * 0.30;
                yield new Vec3((r + tube * Math.cos(minor)) * Math.cos(major), tube * Math.sin(minor),
                    (r + tube * Math.cos(minor)) * Math.sin(major));
            }
            case Helix -> {
                double a = t * Math.PI * 6 + time * 0.05;
                yield new Vec3(Math.cos(a) * r, (t - 0.5) * r * 2, Math.sin(a) * r);
            }
            case DoubleHelix -> {
                int strand = index & 1;
                int strandCount = Math.max(1, count / 2);
                double u = (index / 2.0) / strandCount;
                double a = u * Math.PI * 6 + strand * Math.PI + time * 0.05;
                yield new Vec3(Math.cos(a) * r, (u - 0.5) * r * 2, Math.sin(a) * r);
            }
            case Cone -> conePoint(index, count, r);
            case Cylinder -> cylinderPoint(index, count, r);
            case Spiral -> {
                double a = t * Math.PI * 8 + time * 0.035;
                double radial = r * (0.08 + 0.92 * t);
                yield new Vec3(Math.cos(a) * radial, (t - 0.5) * r * 0.35, Math.sin(a) * radial);
            }
            case Galaxy -> galaxyPoint(index, count, r, time);
            case Heart -> heartPoint(t, r);
            case Star -> starPoint(index, count, r);
            case Wave -> {
                double x = (t * 2 - 1) * r;
                double a = t * Math.PI * 4 + time * 0.06;
                yield new Vec3(x, Math.sin(a) * r * 0.45, Math.cos(a * 0.5) * r * 0.25);
            }
            case Atom -> atomPoint(index, count, r, time);
            case Cube -> cubePoint(index, count, r);
            case Pyramid -> pyramidPoint(index, count, r);
            case Diamond -> diamondPoint(index, count, r);
        };
    }

    private Vec3 conePoint(int index, int count, double r) {
        if (index % 4 == 0) {
            double a = (index / 4.0) / Math.max(1.0, count / 4.0) * Math.PI * 2;
            return new Vec3(Math.cos(a) * r, -r, Math.sin(a) * r);
        }
        double t = index / (double) count;
        double a = t * Math.PI * 10;
        double radial = r * (1.0 - t);
        return new Vec3(Math.cos(a) * radial, -r + 2 * r * t, Math.sin(a) * radial);
    }

    private Vec3 cylinderPoint(int index, int count, double r) {
        int lane = index % 3;
        double t = (index / 3.0) / Math.max(1.0, count / 3.0);
        double a = t * Math.PI * 6;
        if (lane == 0) return new Vec3(Math.cos(a) * r, -r, Math.sin(a) * r);
        if (lane == 1) return new Vec3(Math.cos(a) * r, r, Math.sin(a) * r);
        return new Vec3(Math.cos(a) * r, (fractional(t * 3) * 2 - 1) * r, Math.sin(a) * r);
    }

    private Vec3 galaxyPoint(int index, int count, double r, double time) {
        int arms = 4;
        int arm = index % arms;
        double t = (index / (double) arms) / Math.max(1.0, count / (double) arms);
        double distance = r * Math.sqrt(Math.min(1.0, t));
        double a = arm * Math.PI * 2 / arms + t * Math.PI * 3 + time * 0.025;
        return new Vec3(Math.cos(a) * distance, Math.sin(t * Math.PI * 8) * r * 0.06, Math.sin(a) * distance);
    }

    private Vec3 heartPoint(double t, double r) {
        double a = t * Math.PI * 2;
        double x = 16 * Math.pow(Math.sin(a), 3);
        double y = 13 * Math.cos(a) - 5 * Math.cos(2 * a) - 2 * Math.cos(3 * a) - Math.cos(4 * a);
        return new Vec3(x * r / 17.0, y * r / 17.0, 0);
    }

    private Vec3 starPoint(int index, int count, double r) {
        int vertices = 10;
        double progress = index / (double) count * vertices;
        int vertex = (int) Math.floor(progress) % vertices;
        double u = fractional(progress);
        Vec3 a = starVertex(vertex, r);
        Vec3 b = starVertex((vertex + 1) % vertices, r);
        return a.lerp(b, u);
    }

    private Vec3 starVertex(int index, double r) {
        double radial = (index & 1) == 0 ? r : r * 0.42;
        double a = -Math.PI / 2 + index * Math.PI / 5;
        return new Vec3(Math.cos(a) * radial, Math.sin(a) * radial, 0);
    }

    private Vec3 atomPoint(int index, int count, double r, double time) {
        int orbit = index % 3;
        double t = (index / 3.0) / Math.max(1.0, count / 3.0);
        double a = t * Math.PI * 2 + time * 0.025;
        Vec3 ring = new Vec3(Math.cos(a) * r, 0, Math.sin(a) * r);
        return switch (orbit) {
            case 0 -> rotate(ring, Math.toRadians(60), 0, 0);
            case 1 -> rotate(ring, Math.toRadians(-60), 0, Math.toRadians(60));
            default -> rotate(ring, 0, 0, Math.toRadians(60));
        };
    }

    private Vec3 cubePoint(int index, int count, double r) {
        int edge = index % 12;
        double u = (index / 12.0) / Math.max(1.0, count / 12.0 - 1.0);
        u = fractional(u);
        double v = -r + 2 * r * u;
        return switch (edge) {
            case 0 -> new Vec3(v, -r, -r); case 1 -> new Vec3(v, -r, r);
            case 2 -> new Vec3(v, r, -r); case 3 -> new Vec3(v, r, r);
            case 4 -> new Vec3(-r, v, -r); case 5 -> new Vec3(-r, v, r);
            case 6 -> new Vec3(r, v, -r); case 7 -> new Vec3(r, v, r);
            case 8 -> new Vec3(-r, -r, v); case 9 -> new Vec3(-r, r, v);
            case 10 -> new Vec3(r, -r, v); default -> new Vec3(r, r, v);
        };
    }

    private Vec3 pyramidPoint(int index, int count, double r) {
        Vec3 top = new Vec3(0, r, 0);
        Vec3[] base = {new Vec3(r, -r, r), new Vec3(-r, -r, r), new Vec3(-r, -r, -r), new Vec3(r, -r, -r)};
        int segment = index % 8;
        Vec3 a = segment < 4 ? top : base[segment - 4];
        Vec3 b = segment < 4 ? base[segment] : base[(segment - 3) % 4];
        double u = (index / 8.0) / Math.max(1.0, count / 8.0);
        return a.lerp(b, fractional(u));
    }

    private Vec3 diamondPoint(int index, int count, double r) {
        Vec3 top = new Vec3(0, r, 0);
        Vec3 bottom = new Vec3(0, -r, 0);
        Vec3[] ring = {new Vec3(r, 0, 0), new Vec3(0, 0, r), new Vec3(-r, 0, 0), new Vec3(0, 0, -r)};
        int segment = index % 12;
        Vec3 a;
        Vec3 b;
        if (segment < 4) { a = top; b = ring[segment]; }
        else if (segment < 8) { a = bottom; b = ring[segment - 4]; }
        else { a = ring[segment - 8]; b = ring[(segment - 7) % 4]; }
        double u = (index / 12.0) / Math.max(1.0, count / 12.0);
        return a.lerp(b, fractional(u));
    }

    private Vec3 rotate(Vec3 p, double ax, double ay, double az) {
        double x1 = p.x;
        double y1 = p.y * Math.cos(ax) - p.z * Math.sin(ax);
        double z1 = p.y * Math.sin(ax) + p.z * Math.cos(ax);
        double x2 = x1 * Math.cos(ay) + z1 * Math.sin(ay);
        double z2 = -x1 * Math.sin(ay) + z1 * Math.cos(ay);
        double x3 = x2 * Math.cos(az) - y1 * Math.sin(az);
        double y3 = x2 * Math.sin(az) + y1 * Math.cos(az);
        return new Vec3(x3, y3, z2);
    }

    private double fractional(double value) {
        return value - Math.floor(value);
    }
}
