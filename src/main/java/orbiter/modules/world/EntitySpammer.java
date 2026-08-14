package orbiter.modules;

import orbiter.Orbiter;
import orbiter.util.CommandUtils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class EntitySpammer extends CreativeSafetyModule {

    public enum SpammerMode {
        Spawn,
        FillArea,
        Animate,
        Dominate,
        All
    }

    public enum PositionMode {
        AtPlayer,
        Random,
        AtLookPos
    }

    public enum Formation {
        None,
        Circle,
        Line,
        Spiral,
        Grid
    }

    public enum SpawnShape {
        Cube,
        Sphere,
        Cylinder
    }

    public enum AnimationPattern {
        Orbit,
        Sphere,
        Spiral,
        Line
    }

    public enum Profile {
        Balanced,
        ZombieSwarm,
        MobMix,
        TargetedOrbit,
        Custom
    }

    public enum TargetMode {
        Self,
        NearestPlayer,
        PlayerName,
        Selector
    }

    private final SettingGroup sgGeneral    = settings.getDefaultGroup();
    private final SettingGroup sgSpawn      = settings.createGroup("Spawn / Spam");
    private final SettingGroup sgFillArea   = settings.createGroup("Fill Area");
    private final SettingGroup sgAnimate    = settings.createGroup("Animator");
    private final SettingGroup sgDominate   = settings.createGroup("Dominator");
    private final SettingGroup sgNBT        = settings.createGroup("NBT Options");
    private final SettingGroup sgFormation  = settings.createGroup("Formation");
    private final SettingGroup sgTarget     = settings.createGroup("Spawn Target");

    private final Setting<Profile> profile = sgGeneral.add(new EnumSetting.Builder<Profile>()
        .name("profile").description("Built-in bounded behavior profile.").defaultValue(Profile.Balanced).build());

    private final Setting<SpammerMode> mode = sgGeneral.add(new EnumSetting.Builder<SpammerMode>()
        .name("mode")
        .description("Which features to use. All = everything at once.")
        .defaultValue(SpammerMode.Spawn)
        .build());

    private final Setting<Set<EntityType<?>>> entityTypes = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entity-type")
        .description("Entity types to spawn, animate, or dominate. Selected types are cycled.")
        .defaultValue(EntityType.ZOMBIE)
        .build());

    private final Setting<TargetMode> targetMode = sgTarget.add(new EnumSetting.Builder<TargetMode>()
        .name("target-mode").description("Center spawn/fill operations on one target or a multi-player selector.")
        .defaultValue(TargetMode.Self).build());
    private final Setting<String> targetPlayerName = sgTarget.add(new StringSetting.Builder()
        .name("target-player").description("Exact player name used as the spawn center.").defaultValue("")
        .visible(() -> targetMode.get() == TargetMode.PlayerName).build());
    private final Setting<String> targetSelector = sgTarget.add(new StringSetting.Builder()
        .name("target-selector").description("Safe player selector, for example @a[tag=mob-target,limit=3].")
        .defaultValue("@a[tag=mob-target,limit=3]")
        .visible(() -> targetMode.get() == TargetMode.Selector).build());
    private final Setting<Double> nearestRange = sgTarget.add(new DoubleSetting.Builder()
        .name("nearest-range").description("Maximum range for nearest-player targeting.").defaultValue(64)
        .min(1).sliderRange(1, 256).visible(() -> targetMode.get() == TargetMode.NearestPlayer).build());
    private final Setting<Integer> maxTargets = sgTarget.add(new IntSetting.Builder()
        .name("max-targets").description("Maximum players matched by a multi-player @a selector.").defaultValue(3)
        .min(1).sliderRange(1, 16).visible(() -> targetMode.get() == TargetMode.Selector).build());

    private final Setting<Integer> commandsPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("commands-per-tick")
        .description("Commands per tick.")
        .defaultValue(5)
        .min(1).sliderRange(1, 50)
        .build());

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between action bursts.")
        .defaultValue(1)
        .min(0).sliderRange(0, 20)
        .build());

    private final Setting<Integer> spawnAmount = sgSpawn.add(new IntSetting.Builder()
        .name("spawn-amount")
        .description("Total entities to spawn (0 = infinite).")
        .defaultValue(100)
        .min(0).sliderRange(0, 5000)
        .visible(() -> mode.get() == SpammerMode.Spawn || mode.get() == SpammerMode.All)
        .build());

    private final Setting<PositionMode> positionMode = sgSpawn.add(new EnumSetting.Builder<PositionMode>()
        .name("position-mode")
        .description("Where to spawn entities.")
        .defaultValue(PositionMode.AtPlayer)
        .visible(() -> mode.get() == SpammerMode.Spawn || mode.get() == SpammerMode.All)
        .build());

    private final Setting<Integer> spreadRadius = sgSpawn.add(new IntSetting.Builder()
        .name("spread-radius")
        .description("Radius for random spread.")
        .defaultValue(5)
        .min(1).sliderRange(1, 30)
        .visible(() -> positionMode.get() == PositionMode.Random &&
            (mode.get() == SpammerMode.Spawn || mode.get() == SpammerMode.All))
        .build());

    private final Setting<SpawnShape> fillShape = sgFillArea.add(new EnumSetting.Builder<SpawnShape>()
        .name("fill-shape")
        .description("Shape of the fill area.")
        .defaultValue(SpawnShape.Cube)
        .visible(() -> mode.get() == SpammerMode.FillArea || mode.get() == SpammerMode.All)
        .build());

    private final Setting<Integer> fillRadius = sgFillArea.add(new IntSetting.Builder()
        .name("fill-radius")
        .description("Radius to fill with entities.")
        .defaultValue(5)
        .min(1).sliderRange(1, 30)
        .visible(() -> mode.get() == SpammerMode.FillArea || mode.get() == SpammerMode.All)
        .build());

    private final Setting<Boolean> simpleSelector = sgAnimate.add(new BoolSetting.Builder()
        .name("simple-selector")
        .description("Use entity type for selector instead of raw text.")
        .defaultValue(true)
        .visible(() -> mode.get() == SpammerMode.Animate || mode.get() == SpammerMode.All)
        .build());

    private final Setting<String> entitySelector = sgAnimate.add(new StringSetting.Builder()
        .name("animate-selector")
        .description("Custom entity selector for animation target.")
        .defaultValue("@e[type=zombie,limit=1,sort=nearest]")
        .visible(() -> !simpleSelector.get() &&
            (mode.get() == SpammerMode.Animate || mode.get() == SpammerMode.All))
        .build());

    private final Setting<AnimationPattern> animation = sgAnimate.add(new EnumSetting.Builder<AnimationPattern>()
        .name("animation")
        .description("Movement pattern.")
        .defaultValue(AnimationPattern.Orbit)
        .visible(() -> mode.get() == SpammerMode.Animate || mode.get() == SpammerMode.All)
        .build());

    private final Setting<Double> animRadius = sgAnimate.add(new DoubleSetting.Builder()
        .name("anim-radius")
        .description("Radius of the animation pattern.")
        .defaultValue(5.0)
        .min(1.0).sliderRange(1.0, 30.0)
        .visible(() -> mode.get() == SpammerMode.Animate || mode.get() == SpammerMode.All)
        .build());

    private final Setting<Double> animSpeed = sgAnimate.add(new DoubleSetting.Builder()
        .name("anim-speed")
        .description("Speed of animation (angle per tick).")
        .defaultValue(5.0)
        .min(0.5).sliderRange(0.5, 30.0)
        .visible(() -> mode.get() == SpammerMode.Animate || mode.get() == SpammerMode.All)
        .build());

    private final Setting<Boolean> facePlayer = sgAnimate.add(new BoolSetting.Builder()
        .name("face-player")
        .description("Entity faces the player during animation.")
        .defaultValue(true)
        .visible(() -> mode.get() == SpammerMode.Animate || mode.get() == SpammerMode.All)
        .build());

    private final Setting<Boolean> relativeToPlayer = sgAnimate.add(new BoolSetting.Builder()
        .name("relative-to-player")
        .description("Animation follows the player's position.")
        .defaultValue(true)
        .visible(() -> mode.get() == SpammerMode.Animate || mode.get() == SpammerMode.All)
        .build());

    private final Setting<Double> verticalOffset = sgAnimate.add(new DoubleSetting.Builder()
        .name("vertical-offset")
        .description("Y offset for animation center.")
        .defaultValue(1.0)
        .min(-10.0).sliderRange(-10.0, 20.0)
        .visible(() -> mode.get() == SpammerMode.Animate || mode.get() == SpammerMode.All)
        .build());

    private final Setting<Double> spiralSpeed = sgAnimate.add(new DoubleSetting.Builder()
        .name("spiral-vertical-speed")
        .description("Vertical speed for Spiral pattern.")
        .defaultValue(0.1)
        .min(0.01).sliderRange(0.01, 2.0)
        .visible(() -> animation.get() == AnimationPattern.Spiral &&
            (mode.get() == SpammerMode.Animate || mode.get() == SpammerMode.All))
        .build());

    private final Setting<Double> lineLength = sgAnimate.add(new DoubleSetting.Builder()
        .name("line-length")
        .description("Length of Line animation.")
        .defaultValue(10.0)
        .min(1.0).sliderRange(1.0, 50.0)
        .visible(() -> animation.get() == AnimationPattern.Line &&
            (mode.get() == SpammerMode.Animate || mode.get() == SpammerMode.All))
        .build());

    private final Setting<Integer> domAmount = sgDominate.add(new IntSetting.Builder()
        .name("dom-amount")
        .description("Number of entities to mass-spawn.")
        .defaultValue(10)
        .min(1).sliderRange(1, 500)
        .visible(() -> mode.get() == SpammerMode.Dominate || mode.get() == SpammerMode.All)
        .build());

    private final Setting<SpawnShape> domShape = sgDominate.add(new EnumSetting.Builder<SpawnShape>()
        .name("dom-shape")
        .description("Shape for mass-spawning.")
        .defaultValue(SpawnShape.Cube)
        .visible(() -> mode.get() == SpammerMode.Dominate || mode.get() == SpammerMode.All)
        .build());

    private final Setting<Integer> domRadius = sgDominate.add(new IntSetting.Builder()
        .name("dom-radius")
        .description("Radius for mass-spawning.")
        .defaultValue(5)
        .min(1).sliderRange(1, 30)
        .visible(() -> mode.get() == SpammerMode.Dominate || mode.get() == SpammerMode.All)
        .build());

    private final Setting<Boolean> domOnFire = sgDominate.add(new BoolSetting.Builder()
        .name("dom-on-fire")
        .description("Entities have visual fire.")
        .defaultValue(false)
        .visible(() -> mode.get() == SpammerMode.Dominate || mode.get() == SpammerMode.All)
        .build());

    private final Setting<String> domCustomName = sgDominate.add(new StringSetting.Builder()
        .name("dom-custom-name")
        .description("Custom name for spawned entities (empty = none).")
        .defaultValue("")
        .visible(() -> mode.get() == SpammerMode.Dominate || mode.get() == SpammerMode.All)
        .build());

    private final Setting<Boolean> domNameVisible = sgDominate.add(new BoolSetting.Builder()
        .name("dom-name-visible")
        .description("Show custom name above entity.")
        .defaultValue(true)
        .visible(() -> !domCustomName.get().isEmpty() &&
            (mode.get() == SpammerMode.Dominate || mode.get() == SpammerMode.All))
        .build());

    private final Setting<Integer> domHealth = sgDominate.add(new IntSetting.Builder()
        .name("dom-health")
        .description("Custom health (0 = default).")
        .defaultValue(0)
        .min(0).sliderRange(0, 1000)
        .visible(() -> mode.get() == SpammerMode.Dominate || mode.get() == SpammerMode.All)
        .build());

    private final Setting<Boolean> domCharged = sgDominate.add(new BoolSetting.Builder()
        .name("dom-creeper-charged")
        .description("Charged creeper.")
        .defaultValue(false)
        .visible(() -> mode.get() == SpammerMode.Dominate || mode.get() == SpammerMode.All)
        .build());

    private final Setting<Integer> domExplosionPower = sgDominate.add(new IntSetting.Builder()
        .name("dom-explosion-power")
        .description("Explosion power for fireballs/creepers (0 = default).")
        .defaultValue(0)
        .min(0).sliderRange(0, 127)
        .visible(() -> mode.get() == SpammerMode.Dominate || mode.get() == SpammerMode.All)
        .build());

    private final Setting<Integer> domSlimeSize = sgDominate.add(new IntSetting.Builder()
        .name("dom-slime-size")
        .description("Slime/Magma Cube size (0 = default).")
        .defaultValue(0)
        .min(0).sliderRange(0, 127)
        .visible(() -> mode.get() == SpammerMode.Dominate || mode.get() == SpammerMode.All)
        .build());

    private final Setting<Boolean> noAI = sgNBT.add(new BoolSetting.Builder()
        .name("no-ai").defaultValue(false).build());

    private final Setting<Boolean> noGravity = sgNBT.add(new BoolSetting.Builder()
        .name("no-gravity").defaultValue(false).build());

    private final Setting<Boolean> silent = sgNBT.add(new BoolSetting.Builder()
        .name("silent").defaultValue(true).build());

    private final Setting<Boolean> invulnerable = sgNBT.add(new BoolSetting.Builder()
        .name("invulnerable").defaultValue(false).build());

    private final Setting<Boolean> glowing = sgNBT.add(new BoolSetting.Builder()
        .name("glowing").defaultValue(false).build());

    private final Setting<Boolean> persistent = sgNBT.add(new BoolSetting.Builder()
        .name("persistent").defaultValue(false).build());

    private final Setting<String> customNBT = sgNBT.add(new StringSetting.Builder()
        .name("custom-nbt")
        .description("Additional raw NBT to append (e.g. Fuse:0,powered:1).")
        .defaultValue("")
        .build());

    private final Setting<Formation> formation = sgFormation.add(new EnumSetting.Builder<Formation>()
        .name("formation")
        .description("Spawn entities in a formation shape.")
        .defaultValue(Formation.None)
        .visible(() -> mode.get() == SpammerMode.Spawn || mode.get() == SpammerMode.All)
        .build());

    private final Setting<Double> formationRadius = sgFormation.add(new DoubleSetting.Builder()
        .name("formation-radius")
        .description("Radius of formation.")
        .defaultValue(5.0)
        .min(1.0).sliderRange(1.0, 30.0)
        .visible(() -> formation.get() != Formation.None)
        .build());

    private final Setting<Double> formationSpacing = sgFormation.add(new DoubleSetting.Builder()
        .name("formation-spacing")
        .description("Spacing between entities.")
        .defaultValue(1.5)
        .min(0.5).sliderRange(0.5, 5.0)
        .visible(() -> formation.get() != Formation.None)
        .build());

    private final Random random = new Random();
    private int tickCounter = 0;
    private int spawnedCount = 0;
    private int formationIndex = 0;
    private int entityTypeIndex = 0;

    private double angle = 0;
    private double verticalAngle = 0;
    private double lineProgress = 0;
    private boolean lineDirection = true;

    private List<BlockPos> fillPositions;
    private int fillIndex = 0;

    public EntitySpammer() {
        super("entity-spammer",
            "Mega entity manipulation module: spawn, fill, animate, and dominate entities. OP required.");
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
        spawnedCount = 0;
        formationIndex = 0;
        entityTypeIndex = 0;
        applyProfile();
        angle = 0;
        verticalAngle = 0;
        lineProgress = 0;
        lineDirection = true;
        fillPositions = null;
        fillIndex = 0;

        SpammerMode m = mode.get();

        if (m == SpammerMode.FillArea || m == SpammerMode.Dominate || m == SpammerMode.All) {
            fillPositions = buildFillPositions();
        }

        info("Entity Spammer activated in " + m.name() + " mode.");
    }

    @Override
    public void onDeactivate() {
        info("Entity Spammer stopped. Total spawned: " + spawnedCount);
    }

    @Override
    public String getInfoString() {
        return mode.get().name() + " | " + spawnedCount;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.player.networkHandler == null) return;

        tickCounter++;
        if (tickCounter < delay.get()) return;
        tickCounter = 0;

        SpammerMode m = mode.get();

        for (int i = 0; i < commandsPerTick.get(); i++) {
            boolean didSomething = false;

            if (m == SpammerMode.Spawn || m == SpammerMode.All) {
                if (spawnAmount.get() > 0 && spawnedCount >= spawnAmount.get()) {
                    if (m == SpammerMode.Spawn) { toggle(); return; }
                } else {
                    doSpawn();
                    didSomething = true;
                }
            }

            if (m == SpammerMode.FillArea || m == SpammerMode.All) {
                if (fillPositions != null && fillIndex < fillPositions.size()) {
                    doFill();
                    didSomething = true;
                } else if (m == SpammerMode.FillArea) {
                    info("Fill complete!");
                    toggle();
                    return;
                }
            }

            if (m == SpammerMode.Animate || m == SpammerMode.All) {
                doAnimate();
                didSomething = true;
            }

            if (m == SpammerMode.Dominate || m == SpammerMode.All) {
                if (fillPositions != null && fillIndex < fillPositions.size()) {
                    doDominate();
                    didSomething = true;
                } else if (m == SpammerMode.Dominate) {
                    info("Domination complete! Spawned " + spawnedCount);
                    toggle();
                    return;
                }
            }

            if (!didSomething) break;
        }
    }

    private void doSpawn() {
        double x, y, z;

        if (formation.get() != Formation.None) {
            double[] pos = getFormationPosition(formationIndex++);
            x = pos[0];
            y = pos[1];
            z = pos[2];
        } else {
            switch (positionMode.get()) {
                case Random -> {
                    x = (random.nextDouble() * 2 - 1) * spreadRadius.get();
                    y = random.nextDouble() * 3;
                    z = (random.nextDouble() * 2 - 1) * spreadRadius.get();
                }
                case AtLookPos -> {
                    double yaw = Math.toRadians(mc.player.getYaw());
                    double pitch = Math.toRadians(mc.player.getPitch());
                    x = -Math.sin(yaw) * Math.cos(pitch) * 10;
                    y = -Math.sin(pitch) * 10;
                    z = Math.cos(yaw) * Math.cos(pitch) * 10;
                }
                default -> { x = 0; y = 0; z = 0; }
            }
        }

        String target = resolveSpawnTarget();
        if (target == null) return;
        String nbt = buildNBT();
        String entity = getNextEntityId("minecraft:zombie");
        String cmd = nbt.isEmpty()
            ? CommandUtils.formatCommand("execute at %s run summon %s ~%.2f ~%.2f ~%.2f", target, entity, x, y, z)
            : CommandUtils.formatCommand("execute at %s run summon %s ~%.2f ~%.2f ~%.2f {%s}", target, entity, x, y, z, nbt);

        mc.player.networkHandler.sendChatCommand(cmd);
        spawnedCount++;
    }

    private void doFill() {
        if (fillPositions == null || fillIndex >= fillPositions.size()) return;

        BlockPos pos = fillPositions.get(fillIndex);
        String target = resolveSpawnTarget();
        if (target == null) return;
        String nbt = buildNBT();
        String entity = getNextEntityId("minecraft:zombie");
        String cmd = nbt.isEmpty()
            ? CommandUtils.formatCommand("execute at %s run summon %s ~%d ~%d ~%d", target, entity, pos.getX(), pos.getY(), pos.getZ())
            : CommandUtils.formatCommand("execute at %s run summon %s ~%d ~%d ~%d {%s}", target, entity, pos.getX(), pos.getY(), pos.getZ(), nbt);

        mc.player.networkHandler.sendChatCommand(cmd);
        fillIndex++;
        spawnedCount++;
    }

    private void doAnimate() {
        double cx, cy, cz;
        if (relativeToPlayer.get()) {
            cx = mc.player.getX();
            cy = mc.player.getY() + verticalOffset.get();
            cz = mc.player.getZ();
        } else {
            cx = 0;
            cy = verticalOffset.get();
            cz = 0;
        }

        double tx, ty, tz;
        double r = animRadius.get();

        switch (animation.get()) {
            case Orbit -> {
                tx = cx + Math.cos(Math.toRadians(angle)) * r;
                ty = cy;
                tz = cz + Math.sin(Math.toRadians(angle)) * r;
                angle += animSpeed.get();
            }
            case Sphere -> {
                tx = cx + Math.cos(Math.toRadians(angle)) * Math.cos(Math.toRadians(verticalAngle)) * r;
                ty = cy + Math.sin(Math.toRadians(verticalAngle)) * r;
                tz = cz + Math.sin(Math.toRadians(angle)) * Math.cos(Math.toRadians(verticalAngle)) * r;
                angle += animSpeed.get();
                verticalAngle += animSpeed.get() * 0.7;
            }
            case Spiral -> {
                tx = cx + Math.cos(Math.toRadians(angle)) * r;
                ty = cy + (angle * spiralSpeed.get() / 360.0);
                tz = cz + Math.sin(Math.toRadians(angle)) * r;
                angle += animSpeed.get();
                if (ty > cy + r * 2) angle = 0;
            }
            case Line -> {
                double progress = lineProgress / lineLength.get();
                double facing = Math.toRadians(mc.player.getYaw());
                tx = cx + Math.sin(-facing) * lineLength.get() * (progress - 0.5);
                ty = cy;
                tz = cz + Math.cos(facing) * lineLength.get() * (progress - 0.5);

                if (lineDirection) {
                    lineProgress += animSpeed.get() * 0.1;
                    if (lineProgress >= lineLength.get()) lineDirection = false;
                } else {
                    lineProgress -= animSpeed.get() * 0.1;
                    if (lineProgress <= 0) lineDirection = true;
                }
            }
            default -> { tx = cx; ty = cy; tz = cz; }
        }

        String facing = "";
        if (facePlayer.get()) {
            facing = " facing entity @s feet";
        }

        String cmd = CommandUtils.formatCommand("tp %s %.2f %.2f %.2f%s",
            getResolvedSelector(), tx, ty, tz, facing);
        mc.player.networkHandler.sendChatCommand(cmd);
    }

    private void doDominate() {
        if (fillPositions == null || fillIndex >= fillPositions.size()) return;

        BlockPos pos = fillPositions.get(fillIndex);
        String cmd = buildDominateCommand(pos);
        if (cmd == null) return;
        mc.player.networkHandler.sendChatCommand(cmd);
        fillIndex++;
        spawnedCount++;
    }

    private String buildDominateCommand(BlockPos pos) {
        List<String> tags = new ArrayList<>();

        if (noAI.get()) tags.add("NoAI:1b");
        if (noGravity.get()) tags.add("NoGravity:1b");
        if (invulnerable.get()) tags.add("Invulnerable:1b");
        if (silent.get()) tags.add("Silent:1b");
        if (glowing.get()) tags.add("Glowing:1b");
        if (persistent.get()) tags.add("PersistenceRequired:1b");
        if (domOnFire.get()) tags.add("HasVisualFire:1b");

        if (!domCustomName.get().isEmpty()) {
            String escaped = domCustomName.get().replace("\"", "\\\"");
            tags.add("CustomName:'\"" + escaped + "\"'");
            tags.add("CustomNameVisible:" + (domNameVisible.get() ? "1b" : "0b"));
        }

        if (domHealth.get() > 0) {
            tags.add("Health:" + domHealth.get() + "f");
            tags.add("Attributes:[{id:\"minecraft:max_health\",base:" + domHealth.get() + "d}]");
        }
        if (domCharged.get()) tags.add("powered:1b");
        if (domExplosionPower.get() > 0) tags.add("ExplosionRadius:" + domExplosionPower.get() + "b");
        if (domSlimeSize.get() > 0) tags.add("Size:" + domSlimeSize.get());

        String extra = customNBT.get().trim();
        if (!extra.isEmpty()) tags.add(extra);

        String target = resolveSpawnTarget();
        if (target == null) return null;
        String entity = getNextEntityId("minecraft:zombie");

        if (tags.isEmpty()) {
            return CommandUtils.formatCommand("execute at %s run summon %s ~%d ~%d ~%d",
                target, entity, pos.getX(), pos.getY(), pos.getZ());
        }
        return CommandUtils.formatCommand("execute at %s run summon %s ~%d ~%d ~%d {%s}",
            target, entity, pos.getX(), pos.getY(), pos.getZ(), String.join(",", tags));
    }

    private String buildNBT() {
        StringBuilder nbt = new StringBuilder();
        if (noAI.get()) appendNBT(nbt, "NoAI:1b");
        if (noGravity.get()) appendNBT(nbt, "NoGravity:1b");
        if (silent.get()) appendNBT(nbt, "Silent:1b");
        if (invulnerable.get()) appendNBT(nbt, "Invulnerable:1b");
        if (glowing.get()) appendNBT(nbt, "Glowing:1b");
        if (persistent.get()) appendNBT(nbt, "PersistenceRequired:1b");

        String custom = customNBT.get();
        if (custom != null && !custom.isEmpty()) appendNBT(nbt, custom);

        return nbt.toString();
    }

    private void appendNBT(StringBuilder sb, String tag) {
        if (sb.length() > 0) sb.append(",");
        sb.append(tag);
    }

    private String getResolvedSelector() {
        if (!simpleSelector.get()) return validateEntitySelector(entitySelector.get());
        String type = getPrimaryEntityId("minecraft:zombie");
        return CommandUtils.formatCommand("@e[type=%s,limit=1,sort=nearest]", type);
    }

    private String getPrimaryEntityId(String fallback) {
        if (entityTypes.get().isEmpty()) return fallback;

        return entityTypes.get().stream()
            .map(Registries.ENTITY_TYPE::getId)
            .filter(id -> id != null)
            .map(Identifier::toString)
            .sorted(Comparator.naturalOrder())
            .findFirst()
            .orElse(fallback);
    }

    private String getNextEntityId(String fallback) {
        List<String> ids;
        if (profile.get() == Profile.ZombieSwarm) {
            ids = List.of("minecraft:zombie");
        } else if (profile.get() == Profile.MobMix) {
            ids = List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:spider", "minecraft:creeper");
        } else {
            ids = entityTypes.get().stream()
                .map(Registries.ENTITY_TYPE::getId)
                .filter(id -> id != null)
                .map(Identifier::toString)
                .sorted()
                .toList();
        }
        if (ids.isEmpty()) return fallback;
        String id = ids.get(entityTypeIndex % ids.size());
        entityTypeIndex++;
        return id;
    }

    private String resolveSpawnTarget() {
        return switch (targetMode.get()) {
            case Self -> "@s";
            case NearestPlayer -> CommandUtils.formatCommand(
                "@p[name=!%s,distance=..%.2f]", mc.player.getGameProfile().name(), nearestRange.get());
            case PlayerName -> validatePlayerName(targetPlayerName.get());
            case Selector -> validatePlayerSelector(targetSelector.get());
        };
    }

    private String validatePlayerName(String value) {
        if (value == null || value.isBlank() || value.length() > 16) return rejectTarget("Target player name rejected.");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_')) return rejectTarget("Target player name rejected.");
        }
        return value;
    }

    private String validatePlayerSelector(String value) {
        if (value == null) return rejectTarget("Target selector rejected.");
        String trimmed = value.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (trimmed.length() > 180 || trimmed.indexOf(' ') >= 0 || trimmed.indexOf('\n') >= 0
            || trimmed.indexOf('\r') >= 0 || !(lower.startsWith("@a") || lower.startsWith("@p") || lower.startsWith("@r"))) {
            return rejectTarget("Target selector rejected. Use a whitespace-free @a, @p, or @r selector.");
        }
        for (int i = 2; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (!(Character.isLetterOrDigit(c) || "_[],:.=!+-".indexOf(c) >= 0)) {
                return rejectTarget("Target selector contains unsupported characters.");
            }
        }
        if (lower.startsWith("@a") && !lower.contains("limit=")) {
            trimmed = trimmed.equalsIgnoreCase("@a")
                ? "@a[limit=" + maxTargets.get() + "]"
                : trimmed.substring(0, trimmed.length() - 1) + ",limit=" + maxTargets.get() + "]";
        }
        return trimmed;
    }

    private String validateEntitySelector(String value) {
        if (value == null || value.length() > 180 || value.indexOf(' ') >= 0
            || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) return rejectTarget("Entity selector rejected.");
        return value;
    }

    private String rejectTarget(String message) {
        warning(message);
        toggle();
        return null;
    }

    private void applyProfile() {
        if (profile.get() == Profile.TargetedOrbit) {
            if (mode.get() == SpammerMode.Spawn) mode.set(SpammerMode.Animate);
        }
    }

    private List<BlockPos> buildFillPositions() {
        List<BlockPos> positions = new ArrayList<>();
        if (mc.player == null) return positions;

        int r;
        SpawnShape shape;

        if (mode.get() == SpammerMode.Dominate || mode.get() == SpammerMode.All) {
            r = domRadius.get();
            shape = domShape.get();
        } else {
            r = fillRadius.get();
            shape = fillShape.get();
        }

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (shouldInclude(x, y, z, r, shape)) {
                        positions.add(new BlockPos(x, y, z));
                    }
                }
            }
        }

        if (mode.get() == SpammerMode.Dominate || mode.get() == SpammerMode.All) {
            int total = domAmount.get();
            if (total > 0 && positions.size() > total) {

                List<BlockPos> limited = new ArrayList<>();
                for (int i = 0; i < total; i++) {
                    limited.add(positions.get(i % positions.size()));
                }
                return limited;
            }
        }

        return positions;
    }

    private boolean shouldInclude(int x, int y, int z, int r, SpawnShape shape) {
        return switch (shape) {
            case Cube -> true;
            case Sphere -> (x * x + y * y + z * z) <= (r * r);
            case Cylinder -> (x * x + z * z) <= (r * r);
        };
    }

    private double[] getFormationPosition(int index) {
        double r = formationRadius.get();
        double spacing = formationSpacing.get();
        double yaw = Math.toRadians(mc.player.getYaw());

        return switch (formation.get()) {
            case Circle -> {
                int totalInCircle = Math.max(1, (int) (2 * Math.PI * r / spacing));
                double a = (2 * Math.PI / totalInCircle) * (index % totalInCircle);
                int ring = index / totalInCircle;
                yield new double[]{Math.cos(a) * (r + ring * spacing), ring * 0.5,
                    Math.sin(a) * (r + ring * spacing)};
            }
            case Line -> {
                double offset = index * spacing;
                yield new double[]{-Math.sin(yaw) * offset, 0, Math.cos(yaw) * offset};
            }
            case Spiral -> {
                double a = index * 0.5;
                double sr = spacing * index * 0.15;
                yield new double[]{Math.cos(a) * sr, index * 0.1, Math.sin(a) * sr};
            }
            case Grid -> {
                int gridSize = (int) Math.ceil(Math.sqrt(spawnAmount.get() > 0 ? spawnAmount.get() : 100));
                int gx = index % gridSize;
                int gz = index / gridSize;
                double startX = -(gridSize * spacing) / 2.0;
                double startZ = -(gridSize * spacing) / 2.0;
                yield new double[]{startX + gx * spacing, 0, startZ + gz * spacing};
            }
            default -> new double[]{0, 0, 0};
        };
    }
}
