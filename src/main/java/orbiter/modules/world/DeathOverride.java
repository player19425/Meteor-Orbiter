package orbiter.modules.world;

import orbiter.Orbiter;
import orbiter.modules.CreativeSafetyModule;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.client.gui.screens.DeathScreen;
import orbiter.util.CommandUtils;
import orbiter.util.FastSend;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

public class DeathOverride extends CreativeSafetyModule {
    public enum RespawnMode { Hold, Spam }

    private static DeathOverride instance;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> requireTrigger = sgGeneral.add(new BoolSetting.Builder()
            .name("require-death-streak")
            .description("Only override deaths after enough deaths inside the time window. When off, every death is overridden.")
            .defaultValue(true)
            .build());

    private final Setting<Integer> triggerDeaths = sgGeneral.add(new IntSetting.Builder()
            .name("trigger-deaths")
            .description("Deaths needed inside the window to engage the override.")
            .defaultValue(5)
            .min(1)
            .sliderRange(1, 20)
            .visible(requireTrigger::get)
            .build());

    private final Setting<Integer> windowSeconds = sgGeneral.add(new IntSetting.Builder()
            .name("window-seconds")
            .description("Sliding time window used to count deaths.")
            .defaultValue(30)
            .min(1)
            .sliderRange(5, 300)
            .visible(requireTrigger::get)
            .build());

    private final Setting<RespawnMode> respawnMode = sgGeneral.add(new EnumSetting.Builder<RespawnMode>()
            .name("respawn-mode")
            .description("Hold blocks the server's respawn packet so your world never reloads and you stay a free camera ghost until you disable the module. Spam lets respawns through and sends your own respawn requests at an interval.")
            .defaultValue(RespawnMode.Hold)
            .build());

    private final Setting<Integer> spamInterval = sgGeneral.add(new IntSetting.Builder()
            .name("spam-interval-ticks")
            .description("Ticks between respawn requests while dead in Spam mode.")
            .defaultValue(10)
            .min(1)
            .sliderRange(1, 40)
            .visible(() -> respawnMode.get() == RespawnMode.Spam)
            .build());

    private final Setting<Boolean> useFreecam = sgGeneral.add(new BoolSetting.Builder()
            .name("freecam")
            .description("Move the camera around freely while the override is engaged. Chat and commands stay usable.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> notifications = sgGeneral.add(new BoolSetting.Builder()
            .name("notifications")
            .description("Show a chat message when the override engages or releases.")
            .defaultValue(true)
            .build());

    private final Deque<Long> deathTimes = new ArrayDeque<>();
    private boolean engaged = false;
    private boolean freecamStartedByUs = false;
    private Boolean savedShowDeathScreen = null;
    private boolean wasDead = false;
    private int spamCounter = 0;

    public DeathOverride() {
        super("death-override",
                "Removes DieScreen incase of kill commandblock.");
        instance = this;
    }

    public static boolean shouldBlockWorldSwap() {
        return instance != null && instance.engaged && instance.respawnMode.get() == RespawnMode.Hold;
    }

    @Override
    public void onActivate() {
        deathTimes.clear();
        engaged = false;
        freecamStartedByUs = false;
        savedShowDeathScreen = null;
        wasDead = mc.player != null && mc.player.isDeadOrDying();
        spamCounter = 0;
    }

    @Override
    public void onDeactivate() {
        boolean wasHolding = engaged && respawnMode.get() == RespawnMode.Hold;
        release(true);

        if (mc.player == null || mc.player.connection == null) return;

        if (wasHolding) {
            if (mc.player.isDeadOrDying()) {
                mc.player.setHealth(mc.player.getMaxHealth());
                mc.player.deathTime = 0;
                mc.player.connection.send(new ServerboundClientCommandPacket(
                        ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            }
            FastSend.command(CommandUtils.vanilla("tp @s @s"));
        } else if (mc.player.isDeadOrDying()) {
            mc.player.connection.send(new ServerboundClientCommandPacket(
                    ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        }
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (engaged && event.screen instanceof DeathScreen) event.cancel();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        boolean dead = mc.player.isDeadOrDying();

        if (dead && !wasDead) {
            long now = System.currentTimeMillis();
            deathTimes.addLast(now);
            prune(now);
            if (!engaged && shouldEngage()) engage(now);
        }
        wasDead = dead;

        if (!engaged) return;

        if (mc.player.shouldShowDeathScreen()) mc.player.setShowDeathScreen(false);
        if (mc.gui.screen() instanceof DeathScreen) mc.gui.setScreen(null);

        if (!dead) {
            if (respawnMode.get() == RespawnMode.Spam) release(true);
            return;
        }

        if (respawnMode.get() == RespawnMode.Spam
                && ++spamCounter >= spamInterval.get()
                && mc.player.connection != null) {
            spamCounter = 0;
            mc.player.connection.send(new ServerboundClientCommandPacket(
                    ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        }
    }

    private boolean shouldEngage() {
        if (!requireTrigger.get()) return true;
        prune(System.currentTimeMillis());
        return deathTimes.size() >= triggerDeaths.get();
    }

    private void engage(long now) {
        engaged = true;
        spamCounter = 0;

        savedShowDeathScreen = mc.player.shouldShowDeathScreen();
        mc.player.setShowDeathScreen(false);

        if (useFreecam.get()) {
            Freecam freecam = Modules.get().get(Freecam.class);
            if (freecam != null && !freecam.isActive()) {
                freecam.toggle();
                freecamStartedByUs = true;
            }
        }

        if (notifications.get()) {
            warning("Death override engaged (" + deathTimes.size() + " deaths in "
                    + windowSeconds.get() + "s). Disable the module to release.");
        }
    }

    private void release(boolean announce) {
        if (!engaged) return;
        engaged = false;

        if (savedShowDeathScreen != null && mc.player != null) {
            mc.player.setShowDeathScreen(savedShowDeathScreen);
            savedShowDeathScreen = null;
        }

        if (freecamStartedByUs) {
            Freecam freecam = Modules.get().get(Freecam.class);
            if (freecam != null && freecam.isActive()) freecam.toggle();
            freecamStartedByUs = false;
        }

        if (announce && notifications.get() && mc.player != null) {
            info("Death override released.");
        }
    }

    private void prune(long now) {
        long windowMs = windowSeconds.get() * 1000L;
        Iterator<Long> it = deathTimes.iterator();
        while (it.hasNext()) {
            if (now - it.next() > windowMs) it.remove();
            else break;
        }
    }

    @Override
    public String getInfoString() {
        return engaged ? "engaged" : deathTimes.size() + " deaths";
    }
}
