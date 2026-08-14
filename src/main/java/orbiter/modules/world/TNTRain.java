package orbiter.modules;

import orbiter.Orbiter;
import orbiter.util.CommandUtils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Random;

public class TNTRain extends CreativeSafetyModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgContinuous = settings.createGroup("Continuous Mode");
    private final SettingGroup sgTarget = settings.createGroup("Target Mode");

    private final Setting<Integer> totalTNT = sgGeneral.add(new IntSetting.Builder()
            .name("total-tnt")
            .description("Total TNT entities to spawn (used in burst mode).")
            .defaultValue(50)
            .min(1)
            .sliderRange(1, 1000)
            .build());

    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
            .name("radius")
            .description("Horizontal radius for TNT spread.")
            .defaultValue(10)
            .min(1)
            .sliderRange(1, 50)
            .build());

    private final Setting<Integer> height = sgGeneral.add(new IntSetting.Builder()
            .name("height")
            .description("Height above player to spawn TNT.")
            .defaultValue(30)
            .min(5)
            .sliderRange(5, 100)
            .build());

    private final Setting<Integer> fuseTicks = sgGeneral.add(new IntSetting.Builder()
            .name("fuse-ticks")
            .description("TNT fuse time in ticks (80 = default, 0 = instant).")
            .defaultValue(80)
            .min(0)
            .sliderRange(0, 200)
            .build());

    private final Setting<Integer> commandsPerTick = sgGeneral.add(new IntSetting.Builder()
            .name("commands-per-tick")
            .description("Number of /summon commands per tick.")
            .defaultValue(5)
            .min(1)
            .sliderRange(1, 50)
            .build());

    private final Setting<Boolean> continuous = sgContinuous.add(new BoolSetting.Builder()
            .name("continuous")
            .description("Keep spawning TNT continuously instead of a one-time burst.")
            .defaultValue(false)
            .build());

    private final Setting<Integer> tntPerSecond = sgContinuous.add(new IntSetting.Builder()
            .name("tnt-per-second")
            .description("TNT to spawn per second in continuous mode.")
            .defaultValue(10)
            .min(1)
            .sliderRange(1, 100)
            .visible(continuous::get)
            .build());

    private final Setting<Integer> duration = sgContinuous.add(new IntSetting.Builder()
            .name("duration")
            .description("Duration in seconds (0 = infinite).")
            .defaultValue(0)
            .min(0)
            .sliderRange(0, 300)
            .visible(continuous::get)
            .build());

    private final Setting<Boolean> randomFuse = sgContinuous.add(new BoolSetting.Builder()
            .name("random-fuse")
            .description("Randomize fuse time for each TNT.")
            .defaultValue(false)
            .build());

    private final Setting<Integer> minFuse = sgContinuous.add(new IntSetting.Builder()
            .name("min-fuse")
            .description("Minimum fuse ticks when randomized.")
            .defaultValue(20)
            .min(0)
            .sliderRange(0, 200)
            .visible(randomFuse::get)
            .build());

    private final Setting<Integer> maxFuse = sgContinuous.add(new IntSetting.Builder()
            .name("max-fuse")
            .description("Maximum fuse ticks when randomized.")
            .defaultValue(80)
            .min(0)
            .sliderRange(0, 200)
            .visible(randomFuse::get)
            .build());

    private final Setting<TargetMode> targetMode = sgTarget.add(new EnumSetting.Builder<TargetMode>()
            .name("target-mode")
            .description("Target a player to rain TNT on their head.")
            .defaultValue(TargetMode.None)
            .build());

    private final Setting<String> targetPlayerName = sgTarget.add(new StringSetting.Builder()
            .name("target-player")
            .description("Name of the player to target.")
            .defaultValue("")
            .visible(() -> targetMode.get() == TargetMode.NamedPlayer)
            .build());

    private final Setting<Boolean> ignoreFriends = sgTarget.add(new BoolSetting.Builder()
            .name("ignore-friends")
            .description("Don't target friends.")
            .defaultValue(true)
            .visible(() -> targetMode.get() != TargetMode.None)
            .build());

    private final Setting<Integer> followRadius = sgTarget.add(new IntSetting.Builder()
            .name("follow-radius")
            .description("Spread radius around the target player.")
            .defaultValue(5)
            .min(0)
            .sliderRange(0, 30)
            .visible(() -> targetMode.get() != TargetMode.None)
            .build());

    private final Random random = new Random();
    private int spawnedCount = 0;
    private int tickCounter = 0;
    private int elapsedTicks = 0;

    public TNTRain() {
        super("tnt-rain",
                "Spawns TNT falling from the sky in a radius. OP required.");
    }

    @Override
    public void onActivate() {
        spawnedCount = 0;
        tickCounter = 0;
        elapsedTicks = 0;

        if (mc.player == null) {
            toggle();
            return;
        }

        info("TNT Rain started! " + (continuous.get()
                ? "Continuous mode at " + tntPerSecond.get() + "/sec"
                : "Burst mode: " + totalTNT.get() + " TNT"));
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.player.networkHandler == null)
            return;

        elapsedTicks++;

        if (continuous.get() && duration.get() > 0 && elapsedTicks > duration.get() * 20) {
            info("TNT Rain duration expired. Spawned " + spawnedCount + " TNT total.");
            toggle();
            return;
        }

        if (!continuous.get() && spawnedCount >= totalTNT.get()) {
            info("TNT Rain burst complete! Spawned " + spawnedCount + " TNT.");
            toggle();
            return;
        }

        int toSpawn;
        if (continuous.get()) {

            double perTick = tntPerSecond.get() / 20.0;
            toSpawn = (int) perTick;

            if (random.nextDouble() < (perTick - toSpawn))
                toSpawn++;
        } else {
            toSpawn = commandsPerTick.get();
        }

        for (int i = 0; i < toSpawn; i++) {
            if (!continuous.get() && spawnedCount >= totalTNT.get())
                break;

            double cx, cy, cz;
            int spreadR = radius.get();

            PlayerEntity targetEntity = findTarget();
            if (targetEntity != null) {
                cx = targetEntity.getX();
                cy = targetEntity.getY();
                cz = targetEntity.getZ();
                spreadR = followRadius.get();
            } else {
                cx = mc.player.getX();
                cy = mc.player.getY();
                cz = mc.player.getZ();
            }

            double x = cx + (random.nextDouble() * 2 - 1) * spreadR;
            double y = cy + height.get();
            double z = cz + (random.nextDouble() * 2 - 1) * spreadR;

            int fuse;
            if (randomFuse.get()) {
                fuse = minFuse.get() + random.nextInt(Math.max(1, maxFuse.get() - minFuse.get() + 1));
            } else {
                fuse = fuseTicks.get();
            }

            String cmd = CommandUtils.formatCommand("summon minecraft:tnt %.2f %.2f %.2f {fuse:%d}", x, y, z, fuse);
            mc.player.networkHandler.sendChatCommand(cmd);
            spawnedCount++;
        }
    }

    private PlayerEntity findTarget() {
        if (mc.world == null || mc.player == null || targetMode.get() == TargetMode.None)
            return null;

        if (targetMode.get() == TargetMode.NamedPlayer) {
            String name = targetPlayerName.get();
            if (name.isEmpty())
                return null;
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player != mc.player && player.getName().getString().equalsIgnoreCase(name)) {
                    if (ignoreFriends.get() && Friends.get().isFriend(player)) return null;
                    return player;
                }
            }
            return null;
        }

        PlayerEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player)
                continue;
            if (ignoreFriends.get() && Friends.get().isFriend(player))
                continue;
            double dist = mc.player.squaredDistanceTo(player);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = player;
            }
        }
        return nearest;
    }

    @Override
    public void onDeactivate() {
        if (spawnedCount > 0) {
            info("TNT Rain stopped. Total spawned: " + spawnedCount);
        }
    }

    public void startWithParams(int tnt, int r, int h) {
        totalTNT.set(tnt);
        radius.set(r);
        height.set(h);
        continuous.set(false);

        if (!isActive()) {
            toggle();
        }
    }

    public enum TargetMode {
        None,
        NearestPlayer,
        NamedPlayer
    }
}
