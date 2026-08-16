package orbiter.modules.world;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.PlayerInfo;
import orbiter.modules.CreativeSafetyModule;
import orbiter.util.CommandUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class ControlPlayer extends CreativeSafetyModule {
    public enum TargetMode { Nearest, NameList, Selector }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgMotion = settings.createGroup("Orbit");
    private final SettingGroup sgSafetyControls = settings.createGroup("Command Safety");

    private final Setting<TargetMode> targetMode = sgGeneral.add(new EnumSetting.Builder<TargetMode>()
        .name("target-mode").description("How controlled players are selected.").defaultValue(TargetMode.Nearest).build());
    private final Setting<Integer> maxPlayers = sgGeneral.add(new IntSetting.Builder()
        .name("max-players").description("Maximum players repositioned per update.").defaultValue(3).min(1).sliderRange(1, 20).build());
    private final Setting<Double> searchRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("search-range").description("Maximum distance for nearest-player selection.").defaultValue(32).min(1).sliderRange(1, 256)
        .visible(() -> targetMode.get() == TargetMode.Nearest).build());
    private final Setting<String> names = sgGeneral.add(new StringSetting.Builder()
        .name("player-names").description("Comma-separated exact player names.").defaultValue("")
        .visible(() -> targetMode.get() == TargetMode.NameList).build());
    private final Setting<String> selector = sgGeneral.add(new StringSetting.Builder()
        .name("selector").description("OP selector used directly, for example @a[tag=orbit,limit=3].").defaultValue("@a[tag=orbit,limit=3]")
        .visible(() -> targetMode.get() == TargetMode.Selector).build());

    private final Setting<Double> radius = sgMotion.add(new DoubleSetting.Builder()
        .name("radius").description("Orbit radius around your current position.").defaultValue(4).min(0.5).sliderRange(0.5, 32).build());
    private final Setting<Double> height = sgMotion.add(new DoubleSetting.Builder()
        .name("height").description("Vertical offset from your current position.").defaultValue(1).sliderRange(-16, 16).build());
    private final Setting<Double> speed = sgMotion.add(new DoubleSetting.Builder()
        .name("speed").description("Orbit degrees advanced per client tick.").defaultValue(6).sliderRange(-45, 45).build());
    private final Setting<Boolean> faceCenter = sgMotion.add(new BoolSetting.Builder()
        .name("face-center").description("Rotate controlled players toward the orbit center.").defaultValue(true).build());
    private final Setting<Boolean> spreadEvenly = sgMotion.add(new BoolSetting.Builder()
        .name("spread-evenly").description("Place selected players at equal angular offsets.").defaultValue(true).build());

    private final Setting<Integer> updateDelay = sgSafetyControls.add(new IntSetting.Builder()
        .name("update-delay-ticks").description("Ticks between command batches.").defaultValue(2).min(1).sliderRange(1, 20).build());
    private final Setting<Integer> maxCommandsPerTick = sgSafetyControls.add(new IntSetting.Builder()
        .name("max-commands-per-tick").description("Hard outbound command cap.").defaultValue(5).min(1).sliderRange(1, 20).build());
    private final Setting<Boolean> excludeSelf = sgSafetyControls.add(new BoolSetting.Builder()
        .name("exclude-self").description("Never target your own player account.").defaultValue(true).build());

    private int tickCounter;
    private double angle;

    public ControlPlayer() {
        super("control-player", "Rotates players around you. OP.");
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
        angle = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null || mc.player.connection == null) return;
        angle = wrapDegrees(angle + speed.get());
        if (++tickCounter < updateDelay.get()) return;
        tickCounter = 0;

        if (targetMode.get() == TargetMode.Selector) {
            String safe = safeSelector(selector.get());
            if (safe == null) {
                warning("Selector rejected. It must start with @a, @p, or @r and must not contain spaces/newlines.");
                toggle();
                return;
            }
            sendOrbitCommand(safe, angle, 0);
            return;
        }

        List<String> targets = resolveNamedTargets();
        int count = Math.min(Math.min(targets.size(), maxPlayers.get()), maxCommandsPerTick.get());
        for (int i = 0; i < count; i++) {
            double offset = spreadEvenly.get() && count > 1 ? i * 360.0 / count : 0;
            sendOrbitCommand(targets.get(i), angle + offset, i);
        }
    }

    private List<String> resolveNamedTargets() {
        List<PlayerInfo> entries = new ArrayList<>(mc.player.connection.getOnlinePlayers());
        String self = mc.player.getGameProfile().name();
        if (targetMode.get() == TargetMode.NameList) {
            List<String> requested = new ArrayList<>();
            for (String raw : names.get().split(",")) {
                String name = safePlayerName(raw.trim());
                if (name != null && (!excludeSelf.get() || !name.equalsIgnoreCase(self))) requested.add(name);
            }
            return requested.stream().distinct().limit(maxPlayers.get()).toList();
        }

        entries.removeIf(entry -> excludeSelf.get() && entry.getProfile().name().equalsIgnoreCase(self));
        entries.removeIf(entry -> {
            if (orbiter$findPlayer(entry.getProfile().id()) == null) return true;
            return orbiter$findPlayer(entry.getProfile().id()).distanceToSqr(mc.player) > searchRange.get() * searchRange.get();
        });
        entries.sort(Comparator.comparingDouble(entry -> orbiter$findPlayer(entry.getProfile().id()).distanceToSqr(mc.player)));
        return entries.stream().limit(maxPlayers.get()).map(entry -> entry.getProfile().name()).toList();
    }

    private void sendOrbitCommand(String target, double targetAngle, int index) {
        double radians = Math.toRadians(targetAngle);
        double dx = Math.cos(radians) * radius.get();
        double dz = Math.sin(radians) * radius.get();
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        String command;
        if (faceCenter.get()) {
            command = CommandUtils.formatCommand(
                "execute positioned %.3f %.3f %.3f run tp %s %.3f %.3f %.3f %.2f 0",
                mc.player.getX(), mc.player.getY(), mc.player.getZ(), target,
                mc.player.getX() + dx, mc.player.getY() + height.get(), mc.player.getZ() + dz, yaw
            );
        } else {
            command = CommandUtils.formatCommand(
                "execute positioned %.3f %.3f %.3f run tp %s %.3f %.3f %.3f",
                mc.player.getX(), mc.player.getY(), mc.player.getZ(), target,
                mc.player.getX() + dx, mc.player.getY() + height.get(), mc.player.getZ() + dz
            );
        }
        mc.player.connection.sendCommand(CommandUtils.vanilla(command));
    }

    private String safePlayerName(String value) {
        if (value == null || value.isEmpty() || value.length() > 16) return null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_')) return null;
        }
        return value;
    }

    private String safeSelector(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.length() > 160 || trimmed.indexOf(' ') >= 0 || trimmed.indexOf('\n') >= 0 || trimmed.indexOf('\r') >= 0) return null;
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("@a") || lower.startsWith("@p") || lower.startsWith("@r"))) return null;
        if (excludeSelf.get() && lower.startsWith("@a")) {
            if (trimmed.equalsIgnoreCase("@a")) return "@a[name=!" + mc.player.getGameProfile().name() + ",limit=" + maxPlayers.get() + "]";
            if (!lower.contains("name=!")) return null;
        }
        return trimmed;
    }

    private double wrapDegrees(double value) {
        value %= 360.0;
        return value < 0 ? value + 360.0 : value;
    }

    private net.minecraft.world.entity.player.Player orbiter$findPlayer(java.util.UUID uuid) {
        if (mc.level == null) return null;
        net.minecraft.world.entity.Entity e = ((meteordevelopment.meteorclient.mixin.LevelAccessor) mc.level).meteor$getEntityLookup().get(uuid);
        return e instanceof net.minecraft.world.entity.player.Player p ? p : null;
    }
}
