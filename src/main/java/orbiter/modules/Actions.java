package orbiter.modules;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.entity.player.BreakBlockEvent;
import meteordevelopment.meteorclient.events.entity.player.PickItemsEvent;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffects;
import orbiter.util.ComboTracker;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

public class Actions extends Module {

    private final SettingGroup sgGeneral      = settings.getDefaultGroup();
    private final SettingGroup sgHealth       = settings.createGroup("Health");
    private final SettingGroup sgDurability   = settings.createGroup("Durability");
    private final SettingGroup sgPlayerRange  = settings.createGroup("Player Range");
    private final SettingGroup sgEntityNear   = settings.createGroup("Entity Near");
    private final SettingGroup sgTotem        = settings.createGroup("Totem");
    private final SettingGroup sgHeldItem     = settings.createGroup("Held Item");
    private final SettingGroup sgUsedItem     = settings.createGroup("Used Item");
    private final SettingGroup sgNearbyItem   = settings.createGroup("Nearby Player Item");
    private final SettingGroup sgLowMaterial  = settings.createGroup("Low Material");
    private final SettingGroup sgLag          = settings.createGroup("Lag");
    private final SettingGroup sgPickup       = settings.createGroup("Pickup");
    private final SettingGroup sgVelocity     = settings.createGroup("Velocity");
    private final SettingGroup sgPearl        = settings.createGroup("Pearl");
    private final SettingGroup sgDeath        = settings.createGroup("On Death");
    private final SettingGroup sgRespawn      = settings.createGroup("On Respawn");
    private final SettingGroup sgBlockBreak   = settings.createGroup("On Block Break");
    private final SettingGroup sgBlockPlace   = settings.createGroup("On Block Place");
    private final SettingGroup sgXPGain       = settings.createGroup("On XP Gain");
    private final SettingGroup sgGamemode     = settings.createGroup("On Gamemode Change");
    private final SettingGroup sgWeather      = settings.createGroup("On Weather Change");
    private final SettingGroup sgDimension    = settings.createGroup("On Dimension Change");
    private final SettingGroup sgChatMatch    = settings.createGroup("On Chat Match");
    private final SettingGroup sgTitle       = settings.createGroup("On Title/Actionbar");
    private final SettingGroup sgScreenOpen   = settings.createGroup("On Screen Open");
    private final SettingGroup sgMoveDistance  = settings.createGroup("On Move Distance");
    private final SettingGroup sgTimeInWorld  = settings.createGroup("On Time In Level");
    private final SettingGroup sgPotion       = settings.createGroup("On Potion Effect");
    private final SettingGroup sgArmorBreak   = settings.createGroup("On Armor Break");
    private final SettingGroup sgTargetSwitch  = settings.createGroup("On Target Switch");

    public enum LogicType {
        And, Or
    }

    public enum LookDirection {
        North(0, -1), South(0, 1), East(1, 0), West(-1, 0),
        NorthEast(1, -1), NorthWest(-1, -1), SouthEast(1, 1), SouthWest(-1, 1);

        public final float yaw;
        LookDirection(int x, int z) {
            this.yaw = (float) (Math.toDegrees(Math.atan2(-x, z)) + 180) % 360;
        }
    }

    public static class Trigger {
        public final String id;
        public final String type;
        public final Map<String, String> config;
        public final List<String> actions;
        public boolean enabled = true;
        public long lastFired = 0;
        public long cooldownMs = 0;

        public Trigger(String id, String type, Map<String, String> config, List<String> actions) {
            this.id = id;
            this.type = type;
            this.config = config != null ? config : new HashMap<>();
            this.actions = actions != null ? actions : new ArrayList<>();
        }
    }

    public static class Macro {
        public final String name;
        public final List<String> actions;

        public Macro(String name, List<String> actions) {
            this.name = name;
            this.actions = actions;
        }
    }

    private final Setting<Boolean> healthEnabled = sgHealth.add(new BoolSetting.Builder()
        .name("health-enabled").description("Enable health-based triggers.").defaultValue(false).build());
    private final Setting<Double> healthDropThreshold = sgHealth.add(new DoubleSetting.Builder()
        .name("health-drop-threshold").description("Health drop amount to trigger.").defaultValue(6.0).min(0.5).sliderRange(0.5, 20.0).build());
    private final Setting<Boolean> healthHighDamage = sgHealth.add(new BoolSetting.Builder()
        .name("high-damage").description("Trigger on high damage spikes.").defaultValue(false).build());
    private final Setting<String> healthAction = sgHealth.add(new StringSetting.Builder()
        .name("health-action").description("Action to fire on health trigger.").defaultValue("").build());

    private final Setting<Boolean> durabilityEnabled = sgDurability.add(new BoolSetting.Builder()
        .name("durability-enabled").description("Enable durability triggers.").defaultValue(false).build());
    private final Setting<Integer> durabilityThreshold = sgDurability.add(new IntSetting.Builder()
        .name("durability-threshold").description("Durability percentage to trigger.").defaultValue(10).min(1).max(99).sliderRange(1, 99).build());
    private final Setting<String> durabilityAction = sgDurability.add(new StringSetting.Builder()
        .name("durability-action").description("Action to fire on durability trigger.").defaultValue("").build());

    private final Setting<Boolean> playerRangeEnabled = sgPlayerRange.add(new BoolSetting.Builder()
        .name("player-range-enabled").description("Enable player range triggers.").defaultValue(false).build());
    private final Setting<Double> playerRangeDistance = sgPlayerRange.add(new DoubleSetting.Builder()
        .name("player-range-distance").description("Distance to trigger.").defaultValue(30.0).min(1.0).sliderRange(1.0, 256.0).build());
    private final Setting<Boolean> playerRangeInside = sgPlayerRange.add(new BoolSetting.Builder()
        .name("player-range-inside").description("Trigger when player enters range (vs leaves).").defaultValue(true).build());
    private final Setting<String> playerRangeAction = sgPlayerRange.add(new StringSetting.Builder()
        .name("player-range-action").description("Action to fire on player range trigger.").defaultValue("").build());

    private final Setting<Boolean> entityNearEnabled = sgEntityNear.add(new BoolSetting.Builder()
        .name("entity-near-enabled").description("Enable entity near triggers.").defaultValue(false).build());
    private final Setting<Double> entityNearDistance = sgEntityNear.add(new DoubleSetting.Builder()
        .name("entity-near-distance").description("Distance threshold.").defaultValue(5.0).min(1.0).sliderRange(1.0, 64.0).build());
    private final Setting<String> entityNearFilter = sgEntityNear.add(new StringSetting.Builder()
        .name("entity-near-filter").description("Entity type filter (empty=all).").defaultValue("").build());
    private final Setting<String> entityNearAction = sgEntityNear.add(new StringSetting.Builder()
        .name("entity-near-action").description("Action to fire.").defaultValue("").build());

    private final Setting<Boolean> totemSelfEnabled = sgTotem.add(new BoolSetting.Builder()
        .name("totem-self-enabled").description("Trigger when you pop a totem.").defaultValue(false).build());
    private final Setting<Boolean> totemEnemyEnabled = sgTotem.add(new BoolSetting.Builder()
        .name("totem-enemy-enabled").description("Trigger when an enemy pops a totem.").defaultValue(false).build());
    private final Setting<String> totemSelfAction = sgTotem.add(new StringSetting.Builder()
        .name("totem-self-action").description("Action when you pop totem.").defaultValue("").build());
    private final Setting<String> totemEnemyAction = sgTotem.add(new StringSetting.Builder()
        .name("totem-enemy-action").description("Action when enemy pops totem.").defaultValue("").build());

    private final Setting<Boolean> heldItemEnabled = sgHeldItem.add(new BoolSetting.Builder()
        .name("held-item-enabled").description("Enable held item triggers.").defaultValue(false).build());
    private final Setting<String> heldItemFilter = sgHeldItem.add(new StringSetting.Builder()
        .name("held-item-filter").description("Item name filter (empty=any).").defaultValue("").build());
    private final Setting<String> heldItemAction = sgHeldItem.add(new StringSetting.Builder()
        .name("held-item-action").description("Action to fire.").defaultValue("").build());

    private final Setting<Boolean> usedItemEnabled = sgUsedItem.add(new BoolSetting.Builder()
        .name("used-item-enabled").description("Enable used item triggers.").defaultValue(false).build());
    private final Setting<String> usedItemFilter = sgUsedItem.add(new StringSetting.Builder()
        .name("used-item-filter").description("Item name filter.").defaultValue("").build());
    private final Setting<String> usedItemAction = sgUsedItem.add(new StringSetting.Builder()
        .name("used-item-action").description("Action to fire.").defaultValue("").build());

    private final Setting<Boolean> nearbyPlayerItemEnabled = sgNearbyItem.add(new BoolSetting.Builder()
        .name("nearby-player-item-enabled").description("Enable nearby player item triggers.").defaultValue(false).build());
    private final Setting<String> nearbyPlayerItemFilter = sgNearbyItem.add(new StringSetting.Builder()
        .name("nearby-player-item-filter").description("Item name filter.").defaultValue("").build());
    private final Setting<Double> nearbyPlayerItemRange = sgNearbyItem.add(new DoubleSetting.Builder()
        .name("nearby-player-item-range").description("Range to check.").defaultValue(20.0).min(1.0).sliderRange(1.0, 64.0).build());
    private final Setting<String> nearbyPlayerItemAction = sgNearbyItem.add(new StringSetting.Builder()
        .name("nearby-player-item-action").description("Action to fire.").defaultValue("").build());

    private final Setting<Boolean> lowMaterialEnabled = sgLowMaterial.add(new BoolSetting.Builder()
        .name("low-material-enabled").description("Enable low material triggers.").defaultValue(false).build());
    private final Setting<String> lowMaterialItem = sgLowMaterial.add(new StringSetting.Builder()
        .name("low-material-item").description("Item to check.").defaultValue("cobblestone").build());
    private final Setting<Integer> lowMaterialThreshold = sgLowMaterial.add(new IntSetting.Builder()
        .name("low-material-threshold").description("Count threshold.").defaultValue(16).min(0).max(4096).sliderRange(0, 512).build());
    private final Setting<String> lowMaterialAction = sgLowMaterial.add(new StringSetting.Builder()
        .name("low-material-action").description("Action to fire.").defaultValue("").build());

    private final Setting<Boolean> lagEnabled = sgLag.add(new BoolSetting.Builder()
        .name("lag-enabled").description("Enable lag/TPS triggers.").defaultValue(false).build());
    private final Setting<Double> lagTpsThreshold = sgLag.add(new DoubleSetting.Builder()
        .name("lag-tps-threshold").description("TPS threshold to trigger.").defaultValue(10.0).min(1.0).sliderRange(1.0, 20.0).build());
    private final Setting<Boolean> lagBelow = sgLag.add(new BoolSetting.Builder()
        .name("lag-below").description("Trigger when TPS drops below threshold.").defaultValue(true).build());
    private final Setting<String> lagAction = sgLag.add(new StringSetting.Builder()
        .name("lag-action").description("Action to fire.").defaultValue("").build());

    private final Setting<Boolean> pickupEnabled = sgPickup.add(new BoolSetting.Builder()
        .name("pickup-enabled").description("Enable pickup triggers.").defaultValue(false).build());
    private final Setting<String> pickupFilter = sgPickup.add(new StringSetting.Builder()
        .name("pickup-filter").description("Item filter (empty=any).").defaultValue("").build());
    private final Setting<String> pickupAction = sgPickup.add(new StringSetting.Builder()
        .name("pickup-action").description("Action to fire.").defaultValue("").build());

    private final Setting<Boolean> velocityEnabled = sgVelocity.add(new BoolSetting.Builder()
        .name("velocity-enabled").description("Enable velocity/knockback triggers.").defaultValue(false).build());
    private final Setting<Double> velocityThreshold = sgVelocity.add(new DoubleSetting.Builder()
        .name("velocity-threshold").description("Velocity magnitude threshold.").defaultValue(2.0).min(0.1).sliderRange(0.1, 20.0).build());
    private final Setting<String> velocityAction = sgVelocity.add(new StringSetting.Builder()
        .name("velocity-action").description("Action to fire.").defaultValue("").build());

    private final Setting<Boolean> pearlEnabled = sgPearl.add(new BoolSetting.Builder()
        .name("pearl-enabled").description("Enable ender pearl triggers.").defaultValue(false).build());
    private final Setting<Boolean> pearlLand = sgPearl.add(new BoolSetting.Builder()
        .name("pearl-land").description("Trigger when pearl lands (vs thrown).").defaultValue(true).build());
    private final Setting<String> pearlAction = sgPearl.add(new StringSetting.Builder()
        .name("pearl-action").description("Action to fire.").defaultValue("").build());

    private final Setting<LogicType> logicType = sgGeneral.add(new EnumSetting.Builder<LogicType>()
        .name("logic-type").description("How multiple triggers combine.").defaultValue(LogicType.And).build());

    private final Setting<Boolean> onDeathEnabled = sgDeath.add(new BoolSetting.Builder()
        .name("on-death-enabled").description("Trigger when player dies (health=0).").defaultValue(false).build());
    private final Setting<String> onDeathAction = sgDeath.add(new StringSetting.Builder()
        .name("on-death-action").description("Action to fire on death.").defaultValue("").build());

    private final Setting<Boolean> onRespawnEnabled = sgRespawn.add(new BoolSetting.Builder()
        .name("on-respawn-enabled").description("Trigger when player respawns.").defaultValue(false).build());
    private final Setting<String> onRespawnAction = sgRespawn.add(new StringSetting.Builder()
        .name("on-respawn-action").description("Action to fire on respawn.").defaultValue("").build());

    private final Setting<Boolean> onBlockBreakEnabled = sgBlockBreak.add(new BoolSetting.Builder()
        .name("on-block-break-enabled").description("Trigger on block break.").defaultValue(false).build());
    private final Setting<String> onBlockBreakFilter = sgBlockBreak.add(new StringSetting.Builder()
        .name("on-block-break-filter").description("Block names to match (comma separated, empty=all).").defaultValue("").build());
    private final Setting<String> onBlockBreakAction = sgBlockBreak.add(new StringSetting.Builder()
        .name("on-block-break-action").description("Action to fire.").defaultValue("").build());

    private final Setting<Boolean> onBlockPlaceEnabled = sgBlockPlace.add(new BoolSetting.Builder()
        .name("on-block-place-enabled").description("Trigger on block place.").defaultValue(false).build());
    private final Setting<String> onBlockPlaceAction = sgBlockPlace.add(new StringSetting.Builder()
        .name("on-block-place-action").description("Action to fire.").defaultValue("").build());

    private final Setting<Boolean> onXPGainEnabled = sgXPGain.add(new BoolSetting.Builder()
        .name("on-xp-gain-enabled").description("Trigger on XP level change.").defaultValue(false).build());
    private final Setting<Integer> onXPGainThreshold = sgXPGain.add(new IntSetting.Builder()
        .name("xp-gain-threshold").description("XP levels gained to trigger.").defaultValue(1).min(1).max(100).sliderRange(1, 50).build());
    private final Setting<String> onXPGainAction = sgXPGain.add(new StringSetting.Builder()
        .name("on-xp-gain-action").description("Action to fire.").defaultValue("").build());

    private final Setting<Boolean> onGamemodeChangeEnabled = sgGamemode.add(new BoolSetting.Builder()
        .name("on-gamemode-change-enabled").description("Trigger on gamemode change.").defaultValue(false).build());
    private final Setting<String> onGamemodeChangeAction = sgGamemode.add(new StringSetting.Builder()
        .name("on-gamemode-change-action").description("Action to fire.").defaultValue("").build());

    private final Setting<Boolean> onWeatherChangeEnabled = sgWeather.add(new BoolSetting.Builder()
        .name("on-weather-change-enabled").description("Trigger on rain start/stop.").defaultValue(false).build());
    private final Setting<Boolean> onWeatherRainStart = sgWeather.add(new BoolSetting.Builder()
        .name("weather-rain-start").description("Trigger when rain starts.").defaultValue(true).build());
    private final Setting<Boolean> onWeatherRainStop = sgWeather.add(new BoolSetting.Builder()
        .name("weather-rain-stop").description("Trigger when rain stops.").defaultValue(true).build());
    private final Setting<String> onWeatherChangeAction = sgWeather.add(new StringSetting.Builder()
        .name("on-weather-change-action").description("Action to fire.").defaultValue("").build());

    private final Setting<Boolean> onDimensionChangeEnabled = sgDimension.add(new BoolSetting.Builder()
        .name("on-dimension-change-enabled").description("Trigger on dimension change.").defaultValue(false).build());
    private final Setting<String> onDimensionChangeAction = sgDimension.add(new StringSetting.Builder()
        .name("on-dimension-change-action").description("Action to fire.").defaultValue("").build());

    private final Setting<Boolean> onChatMatchEnabled = sgChatMatch.add(new BoolSetting.Builder()
        .name("on-chat-match-enabled").description("Trigger on chat message matching regex.").defaultValue(false).build());
    private final Setting<String> onChatMatchRegex = sgChatMatch.add(new StringSetting.Builder()
        .name("chat-match-regex").description("Regex pattern for chat messages.").defaultValue(".*").build());
    private final Setting<String> onChatMatchAction = sgChatMatch.add(new StringSetting.Builder()
        .name("on-chat-match-action").description("Action to fire.").defaultValue("").build());

    private final Setting<Boolean> onTitleActionbarEnabled = sgTitle.add(new BoolSetting.Builder()
        .name("on-title-actionbar-enabled").description("Trigger on title/actionbar matching regex.").defaultValue(false).build());
    private final Setting<String> onTitleActionbarRegex = sgTitle.add(new StringSetting.Builder()
        .name("title-actionbar-regex").description("Regex pattern for title/actionbar.").defaultValue(".*").build());
    private final Setting<String> onTitleActionbarAction = sgTitle.add(new StringSetting.Builder()
        .name("on-title-actionbar-action").description("Action to fire.").defaultValue("").build());

    private final Setting<Boolean> onScreenOpenEnabled = sgScreenOpen.add(new BoolSetting.Builder()
        .name("on-screen-open-enabled").description("Trigger when a screen opens.").defaultValue(false).build());
    private final Setting<String> onScreenOpenFilter = sgScreenOpen.add(new StringSetting.Builder()
        .name("screen-open-filter").description("Screen class name filter (empty=any).").defaultValue("").build());
    private final Setting<String> onScreenOpenAction = sgScreenOpen.add(new StringSetting.Builder()
        .name("on-screen-open-action").description("Action to fire.").defaultValue("").build());

    private final Setting<Boolean> onMoveDistanceEnabled = sgMoveDistance.add(new BoolSetting.Builder()
        .name("on-move-distance-enabled").description("Trigger after moving a distance.").defaultValue(false).build());
    private final Setting<Double> onMoveDistanceThreshold = sgMoveDistance.add(new DoubleSetting.Builder()
        .name("move-distance-threshold").description("Distance in blocks to trigger.").defaultValue(100.0).min(1.0).sliderRange(1.0, 10000.0).build());
    private final Setting<Boolean> onMoveDistanceRepeat = sgMoveDistance.add(new BoolSetting.Builder()
        .name("move-distance-repeat").description("Repeat trigger every threshold distance.").defaultValue(true).build());
    private final Setting<String> onMoveDistanceAction = sgMoveDistance.add(new StringSetting.Builder()
        .name("on-move-distance-action").description("Action to fire.").defaultValue("").build());

    private final Setting<Boolean> onTimeInWorldEnabled = sgTimeInWorld.add(new BoolSetting.Builder()
        .name("on-time-in-world-enabled").description("Trigger after N ticks in world.").defaultValue(false).build());
    private final Setting<Integer> onTimeInWorldTicks = sgTimeInWorld.add(new IntSetting.Builder()
        .name("time-in-world-ticks").description("Ticks since join to trigger.").defaultValue(200).min(1).max(720000).sliderRange(1, 720000).build());
    private final Setting<Boolean> onTimeInWorldRepeat = sgTimeInWorld.add(new BoolSetting.Builder()
        .name("time-in-world-repeat").description("Repeat trigger every N ticks.").defaultValue(false).build());
    private final Setting<String> onTimeInWorldAction = sgTimeInWorld.add(new StringSetting.Builder()
        .name("on-time-in-world-action").description("Action to fire.").defaultValue("").build());

    private final Setting<Boolean> onPotionEffectEnabled = sgPotion.add(new BoolSetting.Builder()
        .name("on-potion-effect-enabled").description("Trigger on potion effect changes.").defaultValue(false).build());
    private final Setting<String> onPotionEffectFilter = sgPotion.add(new StringSetting.Builder()
        .name("potion-effect-filter").description("Effect name filter (empty=any).").defaultValue("").build());
    private final Setting<Boolean> onPotionEffectGained = sgPotion.add(new BoolSetting.Builder()
        .name("potion-effect-gained").description("Trigger when effect gained.").defaultValue(true).build());
    private final Setting<Boolean> onPotionEffectLost = sgPotion.add(new BoolSetting.Builder()
        .name("potion-effect-lost").description("Trigger when effect lost.").defaultValue(true).build());
    private final Setting<String> onPotionEffectAction = sgPotion.add(new StringSetting.Builder()
        .name("on-potion-effect-action").description("Action to fire.").defaultValue("").build());

    private final Setting<Boolean> onArmorBreakEnabled = sgArmorBreak.add(new BoolSetting.Builder()
        .name("on-armor-break-enabled").description("Trigger when armor slot becomes empty.").defaultValue(false).build());
    private final Setting<String> onArmorBreakSlot = sgArmorBreak.add(new StringSetting.Builder()
        .name("armor-break-slot").description("Slot name (head/chest/legs/feet or empty=all).").defaultValue("").build());
    private final Setting<String> onArmorBreakAction = sgArmorBreak.add(new StringSetting.Builder()
        .name("on-armor-break-action").description("Action to fire.").defaultValue("").build());

    private final Setting<Boolean> onTargetSwitchEnabled = sgTargetSwitch.add(new BoolSetting.Builder()
        .name("on-target-switch-enabled").description("Trigger when combat target changes.").defaultValue(false).build());
    private final Setting<Integer> onTargetSwitchComboMin = sgTargetSwitch.add(new IntSetting.Builder()
        .name("target-switch-combo-min").description("Minimum combo to consider target switch.").defaultValue(3).min(0).max(100).sliderRange(0, 50).build());
    private final Setting<String> onTargetSwitchAction = sgTargetSwitch.add(new StringSetting.Builder()
        .name("on-target-switch-action").description("Action to fire.").defaultValue("").build());

    private final Setting<String> defaultAction = sgGeneral.add(new StringSetting.Builder()
        .name("default-action").description("Fallback action string.").defaultValue("").build());
    private final Setting<Boolean> logActions = sgGeneral.add(new BoolSetting.Builder()
        .name("log-actions").description("Log actions to chat.").defaultValue(true).build());
    private final Setting<Integer> globalCooldownMs = sgGeneral.add(new IntSetting.Builder()
        .name("global-cooldown-ms").description("Minimum ms between any trigger fires.").defaultValue(500).min(0).max(60000).sliderRange(0, 10000).build());

    private float lastHealth = 20.0f;
    private int lastXPLevel = 0;
    private boolean wasDead = false;
    private boolean wasRaining = false;
    private ResourceKey<Level> lastDimension = null;
    private Vec3 lastPosition = Vec3.ZERO;
    private double accumulatedDistance = 0.0;
    private long joinTick = 0;
    private long currentTick = 0;
    private long lastFireTime = 0;
    private UUID lastTargetUuid = null;
    private int lastCombo = 0;
    private final Map<EquipmentSlot, Boolean> armorSlotFilled = new EnumMap<>(EquipmentSlot.class);
    private final Set<Holder<MobEffect>> activeEffects = new HashSet<>();
    private final List<Trigger> triggers = new CopyOnWriteArrayList<>();
    private final Map<String, Macro> macros = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Orbiter-Actions-Scheduler");
        t.setDaemon(true);
        return t;
    });
    private final Queue<DelayedAction> delayedActions = new ConcurrentLinkedQueue<>();
    private final Queue<ScheduledAction> scheduledActions = new ConcurrentLinkedQueue<>();
    private boolean pendingRespawn = false;

    private static class DelayedAction {
        final String action;
        final long executeAt;
        DelayedAction(String action, long delayMs) {
            this.action = action;
            this.executeAt = System.currentTimeMillis() + delayMs;
        }
    }

    private static class ScheduledAction {
        final String action;
        final long executeTick;
        ScheduledAction(String action, long executeTick) {
            this.action = action;
            this.executeTick = executeTick;
        }
    }

    public Actions() {
        super(Orbiter.CATEGORY, "actions", "Reactive trigger/action system with module toggles, commands, chat, disconnect, and conditional logic.");
    }

    @Override
    public void onActivate() {
        lastHealth = mc.player != null ? mc.player.getHealth() : 20.0f;
        lastXPLevel = mc.player != null ? mc.player.experienceLevel : 0;
        wasDead = false;
        pendingRespawn = false;
        wasRaining = mc.level != null && mc.level.isRaining();
        lastDimension = mc.level != null ? mc.level.dimension() : Level.OVERWORLD;
        lastPosition = mc.player != null ? mc.player.getEyePosition() : Vec3.ZERO;
        accumulatedDistance = 0.0;
        joinTick = currentTick;
        lastFireTime = 0;
        lastTargetUuid = null;
        lastCombo = 0;

        armorSlotFilled.clear();
        if (mc.player != null) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                armorSlotFilled.put(slot, !mc.player.getItemBySlot(slot).isEmpty());
            }
        }

        activeEffects.clear();
        if (mc.player != null) {
            for (MobEffectInstance effect : mc.player.getActiveEffects()) {
                activeEffects.add(effect.getEffect());
            }
        }

        loadAllMacros();
        info("Actions module activated with %d triggers loaded.", triggers.size());
    }

    @Override
    public void onDeactivate() {
        triggers.clear();

        scheduler.shutdownNow();
        delayedActions.clear();
        scheduledActions.clear();
        info("Actions module deactivated.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;
        currentTick++;

        long now = System.currentTimeMillis();
        while (!delayedActions.isEmpty()) {
            DelayedAction da = delayedActions.peek();
            if (now >= da.executeAt) {
                delayedActions.poll();
                fire(da.action);
            } else {
                break;
            }
        }

        while (!scheduledActions.isEmpty()) {
            ScheduledAction sa = scheduledActions.peek();
            if (currentTick >= sa.executeTick) {
                scheduledActions.poll();
                fire(sa.action);
            } else {
                break;
            }
        }

        checkHealthTrigger();

        checkDurabilityTrigger();

        checkPlayerRangeTrigger();

        checkEntityNearTrigger();

        checkHeldItemTrigger();

        checkLowMaterialTrigger();

        checkLagTrigger();

        checkOnDeathTrigger();

        checkOnRespawnTrigger();

        checkOnBlockBreakTrigger();

        checkXPGainTrigger();

        checkWeatherChangeTrigger();

        checkDimensionChangeTrigger();

        checkMoveDistanceTrigger();

        checkTimeInWorldTrigger();

        checkPotionEffectTrigger();

        checkArmorBreakTrigger();

        checkTargetSwitchTrigger();

        lastHealth = mc.player.getHealth();
        lastXPLevel = mc.player.experienceLevel;
        lastPosition = mc.player.getEyePosition();
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null) return;

        if (event.packet instanceof ClientboundSetHealthPacket) {
            ClientboundSetHealthPacket packet = (ClientboundSetHealthPacket) event.packet;
            float newHealth = packet.getHealth();
            float drop = lastHealth - newHealth;

            if (healthEnabled.get() && healthAction.get() != null && !healthAction.get().isEmpty()) {
                if (drop >= healthDropThreshold.get().floatValue()) {
                    fire(healthAction.get());
                }
                if (healthHighDamage.get() && drop >= 10.0f) {
                    fire(healthAction.get());
                }
            }

            lastHealth = newHealth;
        }

        if (event.packet instanceof ClientboundGameEventPacket) {
            ClientboundGameEventPacket packet = (ClientboundGameEventPacket) event.packet;

            if (onGamemodeChangeEnabled.get()) {
                try {
                    var reason = packet.getEvent();
                    String reasonStr = reason != null ? reason.toString() : "";
                    if (reasonStr.contains("GAME_MODE") || reasonStr.contains("WIN")) {
                        fire(onGamemodeChangeAction.get());
                    }
                } catch (Exception ignored) {

                }
            }
        }

        if (event.packet instanceof ClientboundSystemChatPacket) {
            ClientboundSystemChatPacket packet = (ClientboundSystemChatPacket) event.packet;
            if (onChatMatchEnabled.get()) {
                try {
                    String content = packet.content().getString();
                    Pattern p = Pattern.compile(onChatMatchRegex.get());
                    if (p.matcher(content).find()) {
                        fire(onChatMatchAction.get());
                    }
                } catch (Exception ignored) {}
            }
        }

        if (event.packet instanceof ClientboundSetTitleTextPacket || event.packet instanceof ClientboundSetSubtitleTextPacket) {
            Component titleText = null;
            if (event.packet instanceof ClientboundSetTitleTextPacket) {
                titleText = ((ClientboundSetTitleTextPacket) event.packet).text();
            } else if (event.packet instanceof ClientboundSetSubtitleTextPacket) {
                titleText = ((ClientboundSetSubtitleTextPacket) event.packet).text();
            }
            if (titleText != null && onTitleActionbarEnabled.get()) {
                try {
                    String content = titleText.getString();
                    Pattern p = Pattern.compile(onTitleActionbarRegex.get());
                    if (p.matcher(content).find()) {
                        fire(onTitleActionbarAction.get());
                    }
                } catch (Exception ignored) {}
            }
        }

        if (event.packet instanceof ClientboundSetActionBarTextPacket) {
            ClientboundSetActionBarTextPacket overlay = (ClientboundSetActionBarTextPacket) event.packet;
            if (onTitleActionbarEnabled.get()) {
                try {
                    String content = overlay.text().getString();
                    Pattern p = Pattern.compile(onTitleActionbarRegex.get());
                    if (p.matcher(content).find()) {
                        fire(onTitleActionbarAction.get());
                    }
                } catch (Exception ignored) {}
            }
        }

        if (event.packet instanceof ClientboundRespawnPacket) {

            if (onDimensionChangeEnabled.get() && lastDimension != null) {
                fire(onDimensionChangeAction.get());
            }
            if (wasDead && onRespawnEnabled.get()) {
                pendingRespawn = true;
            }
        }

        if (event.packet instanceof ClientboundEntityEventPacket) {
            ClientboundEntityEventPacket statusPacket = (ClientboundEntityEventPacket) event.packet;

            if (statusPacket.getEventId() == 35) {
                Entity entity = statusPacket.getEntity(mc.level);
                if (entity instanceof Player) {
                    if (entity == mc.player && totemSelfEnabled.get()) {
                        fire(totemSelfAction.get());
                    } else if (entity != mc.player && totemEnemyEnabled.get()) {
                        fire(totemEnemyAction.get());
                    }
                }
            }

            if (statusPacket.getEventId() == 3) {
                Entity entity = statusPacket.getEntity(mc.level);
                if (entity == mc.player) {
                    wasDead = true;
                }
            }
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null) return;

        if (event.packet instanceof ServerboundUseItemOnPacket) {
            if (onBlockPlaceEnabled.get()) {
                fire(onBlockPlaceAction.get());
            }
        }
    }

    @EventHandler
    private void onBreakBlock(BreakBlockEvent event) {
        if (onBlockBreakEnabled.get()) {
            String blockName = "";
            if (mc.level != null) {
                BlockState state = mc.level.getBlockState(event.blockPos);
                blockName = state.getBlock().getName().getString().toLowerCase();
            }

            String filter = onBlockBreakFilter.get();
            if (filter == null || filter.isEmpty()) {
                fire(onBlockBreakAction.get());
            } else {
                String[] filterParts = filter.split(",");
                for (String f : filterParts) {
                    if (blockName.contains(f.trim().toLowerCase())) {
                        fire(onBlockBreakAction.get());
                        break;
                    }
                }
            }
        }
    }

    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        if (onBlockPlaceEnabled.get()) {
            fire(onBlockPlaceAction.get());
        }
    }

    @EventHandler
    private void onPickItems(PickItemsEvent event) {
        if (pickupEnabled.get()) {
            String filter = pickupFilter.get();
            if (filter == null || filter.isEmpty()) {
                fire(pickupAction.get());
            } else {

                ItemStack mainHand = mc.player.getMainHandItem();
                if (mainHand != null && !mainHand.isEmpty() && mainHand.getItemName().getString().toLowerCase().contains(filter.toLowerCase())) {
                    fire(pickupAction.get());
                }
            }
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {

        delayedActions.clear();
        scheduledActions.clear();
    }

    private void checkHealthTrigger() {
        if (!healthEnabled.get() || healthAction.get().isEmpty()) return;
        float currentHealth = mc.player.getHealth();
        float drop = lastHealth - currentHealth;
        if (drop >= healthDropThreshold.get().floatValue()) {
            fire(healthAction.get());
        }
        if (healthHighDamage.get() && drop >= 10.0f) {
            fire(healthAction.get());
        }
    }

    private void checkDurabilityTrigger() {
        if (!durabilityEnabled.get() || durabilityAction.get().isEmpty()) return;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD}) {
            ItemStack stack = mc.player.getItemBySlot(slot);
            if (stack != null && !stack.isEmpty() && stack.isDamageableItem()) {
                int max = stack.getMaxDamage();
                int current = stack.getDamageValue();
                int remaining = max - current;
                int percent = (int) ((remaining / (double) max) * 100);
                if (percent <= durabilityThreshold.get()) {
                    fire(durabilityAction.get());
                    return;
                }
            }
        }
        ItemStack mainHand = mc.player.getMainHandItem();
        ItemStack offHand = mc.player.getOffhandItem();
        for (ItemStack stack : new ItemStack[]{mainHand, offHand}) {
            if (stack != null && stack.isDamageableItem()) {
                int max = stack.getMaxDamage();
                int current = stack.getDamageValue();
                int remaining = max - current;
                int percent = (int) ((remaining / (double) max) * 100);
                if (percent <= durabilityThreshold.get()) {
                    fire(durabilityAction.get());
                    return;
                }
            }
        }
    }

    private void checkPlayerRangeTrigger() {
        if (!playerRangeEnabled.get() || playerRangeAction.get().isEmpty()) return;
        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            double dist = mc.player.distanceTo(player);
            boolean inRange = dist <= playerRangeDistance.get();

            if (playerRangeInside.get() && inRange) {
                fire(playerRangeAction.get());
                return;
            }
            if (!playerRangeInside.get() && !inRange) {
                fire(playerRangeAction.get());
                return;
            }
        }
    }

    private void checkEntityNearTrigger() {
        if (!entityNearEnabled.get() || entityNearAction.get().isEmpty()) return;
        for (Entity entity : ((meteordevelopment.meteorclient.mixin.LevelAccessor) mc.level).meteor$getEntityLookup().getAll()) {
            if (entity == mc.player) continue;
            double dist = mc.player.distanceTo(entity);
            if (dist <= entityNearDistance.get()) {
                String filter = entityNearFilter.get();
                if (filter == null || filter.isEmpty()) {
                    fire(entityNearAction.get());
                    return;
                }
                String typeName = entity.getType().getDescription().getString().toLowerCase();
                if (typeName.contains(filter.toLowerCase())) {
                    fire(entityNearAction.get());
                    return;
                }
            }
        }
    }

    private void checkHeldItemTrigger() {
        if (!heldItemEnabled.get() || heldItemAction.get().isEmpty()) return;
        ItemStack held = mc.player.getMainHandItem();
        String filter = heldItemFilter.get();
        if (held != null && !held.isEmpty()) {
            String name = held.getItemName().getString().toLowerCase();
            if (filter == null || filter.isEmpty() || name.contains(filter.toLowerCase())) {
                fire(heldItemAction.get());
            }
        }
    }

    private void checkLowMaterialTrigger() {
        if (!lowMaterialEnabled.get() || lowMaterialAction.get().isEmpty()) return;
        String itemName = lowMaterialItem.get().toLowerCase();
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack != null && !stack.isEmpty()) {
                String name = stack.getItemName().getString().toLowerCase();
                if (name.contains(itemName)) {
                    count += stack.getCount();
                }
            }
        }
        if (count <= lowMaterialThreshold.get()) {
            fire(lowMaterialAction.get());
        }
    }

    private void checkLagTrigger() {
        if (!lagEnabled.get() || lagAction.get().isEmpty()) return;

        float tps = estimateTPS();
        if (lagBelow.get() && tps < lagTpsThreshold.get()) {
            fire(lagAction.get());
        } else if (!lagBelow.get() && tps >= lagTpsThreshold.get()) {
            fire(lagAction.get());
        }
    }

    private float estimateTPS() {

        updateTPSTracker();
        return calculateTPS();
    }

    private void checkOnDeathTrigger() {
        if (!onDeathEnabled.get()) return;
        float currentHealth = mc.player.getHealth();
        if (currentHealth <= 0 && !wasDead) {
            wasDead = true;
            fire(onDeathAction.get());
        }
    }

    private void checkOnRespawnTrigger() {
        if (!onRespawnEnabled.get()) return;
        if (wasDead && mc.player.getHealth() > 0) {
            wasDead = false;
            fire(onRespawnAction.get());
        }
        if (pendingRespawn) {
            pendingRespawn = false;
            wasDead = false;
            fire(onRespawnAction.get());
        }
    }

    private int lastBlockBreakCount = 0;
    private void checkOnBlockBreakTrigger() {

    }

    private void checkXPGainTrigger() {
        if (!onXPGainEnabled.get()) return;
        int currentLevel = mc.player.experienceLevel;
        int diff = currentLevel - lastXPLevel;
        if (diff >= onXPGainThreshold.get()) {
            fire(onXPGainAction.get());
        }

    }

    private void checkWeatherChangeTrigger() {
        if (!onWeatherChangeEnabled.get()) return;
        if (mc.level == null) return;
        boolean isRaining = mc.level.isRaining();
        if (isRaining != wasRaining) {
            if (isRaining && onWeatherRainStart.get()) {
                fire(onWeatherChangeAction.get());
            }
            if (!isRaining && onWeatherRainStop.get()) {
                fire(onWeatherChangeAction.get());
            }
            wasRaining = isRaining;
        }
    }

    private void checkDimensionChangeTrigger() {
        if (!onDimensionChangeEnabled.get()) return;
        if (mc.level == null) return;
        ResourceKey<Level> currentDim = mc.level.dimension();
        if (!currentDim.equals(lastDimension)) {
            fire(onDimensionChangeAction.get());
            lastDimension = currentDim;
        }
    }

    private void checkMoveDistanceTrigger() {
        if (!onMoveDistanceEnabled.get()) return;
        Vec3 currentPos = mc.player.getEyePosition();
        double dist = lastPosition.distanceTo(currentPos);
        if (dist > 0.1) {
            accumulatedDistance += dist;
        }

        if (accumulatedDistance >= onMoveDistanceThreshold.get()) {
            fire(onMoveDistanceAction.get());
            if (onMoveDistanceRepeat.get()) {
                accumulatedDistance -= onMoveDistanceThreshold.get();
            } else {
                accumulatedDistance = 0;
            }
        }
    }

    private void checkTimeInWorldTrigger() {
        if (!onTimeInWorldEnabled.get()) return;
        long ticksSinceJoin = currentTick - joinTick;
        if (ticksSinceJoin >= onTimeInWorldTicks.get()) {
            fire(onTimeInWorldAction.get());
            if (onTimeInWorldRepeat.get()) {
                joinTick = currentTick;
            }
        }
    }

    private void checkPotionEffectTrigger() {
        if (!onPotionEffectEnabled.get()) return;
        Set<Holder<MobEffect>> currentEffects = new HashSet<>();
        for (MobEffectInstance inst : mc.player.getActiveEffects()) {
            currentEffects.add(inst.getEffect());
        }

        if (onPotionEffectGained.get()) {
            for (Holder<MobEffect> effect : currentEffects) {
                if (!activeEffects.contains(effect)) {
                    String filter = onPotionEffectFilter.get();
                    if (filter == null || filter.isEmpty() ||
                        effect.value().getDisplayName().getString().toLowerCase().contains(filter.toLowerCase())) {
                        fire(onPotionEffectAction.get());
                    }
                }
            }
        }

        if (onPotionEffectLost.get()) {
            for (Holder<MobEffect> effect : activeEffects) {
                if (!currentEffects.contains(effect)) {
                    String filter = onPotionEffectFilter.get();
                    if (filter == null || filter.isEmpty() ||
                        effect.value().getDisplayName().getString().toLowerCase().contains(filter.toLowerCase())) {
                        fire(onPotionEffectAction.get());
                    }
                }
            }
        }

        activeEffects.clear();
        activeEffects.addAll(currentEffects);
    }

    private void checkArmorBreakTrigger() {
        if (!onArmorBreakEnabled.get()) return;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            boolean wasFilled = armorSlotFilled.getOrDefault(slot, false);
            boolean isFilled = !mc.player.getItemBySlot(slot).isEmpty();
            if (wasFilled && !isFilled) {
                String filter = onArmorBreakSlot.get();
                if (filter == null || filter.isEmpty() ||
                    filter.equalsIgnoreCase(slot.name().toLowerCase()) ||
                    filter.equalsIgnoreCase(slot.getType().name())) {
                    fire(onArmorBreakAction.get());
                }
            }
            armorSlotFilled.put(slot, isFilled);
        }
    }

    private void checkTargetSwitchTrigger() {
        if (!onTargetSwitchEnabled.get()) return;

        if (mc.crosshairPickEntity != null && mc.crosshairPickEntity instanceof LivingEntity) {
            LivingEntity target = (LivingEntity) mc.crosshairPickEntity;
            UUID targetUuid = target.getUUID();

            if (lastTargetUuid != null && !lastTargetUuid.equals(targetUuid)) {
                int lastComboCount = ComboTracker.getCombo(lastTargetUuid);
                if (lastComboCount >= onTargetSwitchComboMin.get()) {
                    fire(onTargetSwitchAction.get());
                }
            }

            lastTargetUuid = targetUuid;
        }
    }

    private void checkNearbyPlayerItem() {
        if (!nearbyPlayerItemEnabled.get()) return;
        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            double dist = mc.player.distanceTo(player);
            if (dist <= nearbyPlayerItemRange.get()) {
                ItemStack mainHand = player.getMainHandItem();
                String filter = nearbyPlayerItemFilter.get();
                if (mainHand != null && !mainHand.isEmpty()) {
                    String name = mainHand.getItemName().getString().toLowerCase();
                    if (filter == null || filter.isEmpty() || name.contains(filter.toLowerCase())) {
                        fire(nearbyPlayerItemAction.get());
                        return;
                    }
                }
            }
        }
    }

    private boolean pearlThrown = false;
    private int pearlThrowTick = 0;
    private void checkPearlLand() {
        if (!pearlEnabled.get() || !pearlLand.get()) return;
        if (pearlThrown && (currentTick - pearlThrowTick) > 40) {

            fire(pearlAction.get());
            pearlThrown = false;
        }
    }

    public void fire(String action) {
        if (action == null || action.isEmpty()) return;

        long now = System.currentTimeMillis();
        if (now - lastFireTime < globalCooldownMs.get()) return;
        lastFireTime = now;

        String[] actionParts = action.split(";;");
        for (String part : actionParts) {
            fireSingle(part.trim());
        }
    }

    private void fireSingle(String action) {
        if (action == null || action.isEmpty()) return;

        if (logActions.get()) {
            info("Firing action: %s", action);
        }

        if (action.startsWith("delay:")) {
            try {
                String rest = action.substring(6);
                int spaceIdx = rest.indexOf(' ');
                if (spaceIdx > 0) {
                    long delayMs = Long.parseLong(rest.substring(0, spaceIdx).trim());
                    String subAction = rest.substring(spaceIdx + 1).trim();
                    delayedActions.add(new DelayedAction(subAction, delayMs));
                }
            } catch (NumberFormatException ignored) {}
            return;
        }

        if (action.startsWith("repeat:")) {
            try {
                String rest = action.substring(7);
                int spaceIdx = rest.indexOf(' ');
                if (spaceIdx > 0) {
                    String countAndInterval = rest.substring(0, spaceIdx).trim();
                    String subAction = rest.substring(spaceIdx + 1).trim();
                    String[] parts = countAndInterval.split(":");
                    int count = Integer.parseInt(parts[0]);
                    long intervalMs = parts.length > 1 ? Long.parseLong(parts[1]) : 100;
                    for (int i = 0; i < count; i++) {
                        final long delay = i * intervalMs;
                        delayedActions.add(new DelayedAction(subAction, delay));
                    }
                }
            } catch (NumberFormatException ignored) {}
            return;
        }

        if (action.startsWith("if:")) {
            try {
                String rest = action.substring(3);
                int spaceIdx = rest.indexOf(' ');
                if (spaceIdx > 0) {
                    String condition = rest.substring(0, spaceIdx).trim();
                    String subAction = rest.substring(spaceIdx + 1).trim();
                    if (evaluateCondition(condition)) {
                        fireSingle(subAction);
                    }
                }
            } catch (Exception ignored) {}
            return;
        }

        if (action.startsWith("schedule:")) {
            try {
                String rest = action.substring(9);
                int spaceIdx = rest.indexOf(' ');
                if (spaceIdx > 0) {
                    long ticks = Long.parseLong(rest.substring(0, spaceIdx).trim());
                    String subAction = rest.substring(spaceIdx + 1).trim();
                    scheduledActions.add(new ScheduledAction(subAction, currentTick + ticks));
                }
            } catch (NumberFormatException ignored) {}
            return;
        }

        if (action.startsWith("macro:")) {
            String macroName = action.substring(6).trim();
            executeMacro(macroName);
            return;
        }

        if (action.startsWith("hotbar:")) {
            try {
                int slot = Integer.parseInt(action.substring(7).trim());
                if (slot >= 0 && slot <= 8) {
                    mc.player.getInventory().setSelectedSlot(slot);
                    ClientPacketListener handler = mc.getConnection();
                    if (handler != null) {
                        handler.send(new ServerboundSetCarriedItemPacket(slot));
                    }
                }
            } catch (NumberFormatException ignored) {}
            return;
        }

        if (action.startsWith("drop:")) {
            try {
                int slot = Integer.parseInt(action.substring(5).trim());
                if (slot >= 0 && slot < mc.player.getInventory().getContainerSize()) {
                    ItemStack stack = mc.player.getInventory().getItem(slot);
                    if (!stack.isEmpty()) {
                        mc.player.drop(stack, true);
                        mc.player.getInventory().setItem(slot, ItemStack.EMPTY);
                    }
                }
            } catch (NumberFormatException ignored) {}
            return;
        }

        if (action.equals("use-item")) {
            if (mc.gameMode != null) {
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            }
            return;
        }

        if (action.startsWith("sneak:")) {
            String mode = action.substring(6).trim().toLowerCase();
            KeyMapping sneakKey = mc.options.keyShift;
            switch (mode) {
                case "on":
                    sneakKey.setDown(true);
                    break;
                case "off":
                    sneakKey.setDown(false);
                    break;
                case "toggle":
                    sneakKey.setDown(!sneakKey.isDown());
                    break;
            }
            return;
        }

        if (action.startsWith("sprint:")) {
            String mode = action.substring(7).trim().toLowerCase();
            KeyMapping sprintKey = mc.options.keySprint;
            switch (mode) {
                case "on":
                    sprintKey.setDown(true);
                    break;
                case "off":
                    sprintKey.setDown(false);
                    break;
                case "toggle":
                    sprintKey.setDown(!sprintKey.isDown());
                    break;
            }
            return;
        }

        if (action.startsWith("look:")) {
            try {
                String coords = action.substring(5).trim();
                String[] parts = coords.split("\\s+");
                if (parts.length >= 2) {
                    float yaw = Float.parseFloat(parts[0]);
                    float pitch = Float.parseFloat(parts[1]);
                    mc.player.setYRot(yaw);
                    mc.player.setXRot(pitch);

                    ClientPacketListener handler = mc.getConnection();
                    if (handler != null) {
                        handler.send(new ServerboundMovePlayerPacket.PosRot(
                            mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                            yaw, pitch, mc.player.onGround(), false
                        ));
                    }
                }
            } catch (NumberFormatException ignored) {}
            return;
        }

        if (action.startsWith("yaw:")) {
            try {
                float yaw = Float.parseFloat(action.substring(4).trim());
                mc.player.setYRot(yaw);
                ClientPacketListener handler = mc.getConnection();
                if (handler != null) {
                    handler.send(new ServerboundMovePlayerPacket.PosRot(
                        mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                        yaw, mc.player.getXRot(), mc.player.onGround(), false
                    ));
                }
            } catch (NumberFormatException ignored) {}
            return;
        }

        if (action.startsWith("pitch:")) {
            try {
                float pitch = Float.parseFloat(action.substring(6).trim());
                mc.player.setXRot(pitch);
                ClientPacketListener handler = mc.getConnection();
                if (handler != null) {
                    handler.send(new ServerboundMovePlayerPacket.PosRot(
                        mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                        mc.player.getYRot(), pitch, mc.player.onGround(), false
                    ));
                }
            } catch (NumberFormatException ignored) {}
            return;
        }

        if (action.equals("jump")) {
            mc.player.jumpFromGround();
            return;
        }

        if (action.startsWith("place:")) {
            try {
                String rest = action.substring(6).trim();
                String[] parts = rest.split("\\s+");
                if (parts.length >= 4) {
                    String blockName = parts[0];
                    int x = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);
                    int z = Integer.parseInt(parts[3]);
                    BlockPos pos = new BlockPos(x, y, z);

                    ClientPacketListener handler = mc.getConnection();
                    if (handler != null) {
                        BlockHitResult hitResult = new BlockHitResult(
                            new Vec3(x + 0.5, y + 0.5, z + 0.5),
                            Direction.UP, pos, false
                        );
                        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
                    }
                }
            } catch (NumberFormatException ignored) {}
            return;
        }

        if (action.startsWith("toggle:")) {
            String moduleName = action.substring(7).trim();
            toggleModule(moduleName);
            return;
        }

        if (action.startsWith("message:")) {
            String text = action.substring(8);
            ClientPacketListener handler = mc.getConnection();
            if (handler != null) {
                if (text.length() > 256) text = text.substring(0, 256);
                handler.sendChat(text);
            }
            return;
        }

        if (action.startsWith("command:")) {
            String cmd = action.substring(8);
            if (!cmd.startsWith("/")) cmd = "/" + cmd;
            ClientPacketListener handler = mc.getConnection();
            if (handler != null) {
                if (cmd.length() > 256) cmd = cmd.substring(0, 256);
                handler.sendChat(cmd);
            }
            return;
        }

        if (action.equals("disconnect")) {
            if (mc.level != null) {
                mc.level.disconnect(Component.literal("Actions disconnect"));
            }
            return;
        }

        if (action.startsWith("log:")) {
            String text = action.substring(4);
            info(text);
            return;
        }

        if (action.startsWith("info:")) {
            info(action.substring(5));
            return;
        }

        if (action.startsWith("warn:")) {
            warning(action.substring(5));
            return;
        }

        if (action.startsWith("wait:")) {
            try {
                long waitMs = Long.parseLong(action.substring(5).trim());

                delayedActions.add(new DelayedAction("log:waited " + Math.min(waitMs, 5000) + "ms (non-blocking)", Math.min(waitMs, 5000)));
            } catch (NumberFormatException ignored) {}
            return;
        }

        if (action.startsWith("/")) {
            ClientPacketListener handler = mc.getConnection();
            if (handler != null && action.length() <= 256) {
                handler.sendChat(action);
            }
        }
    }

    private boolean evaluateCondition(String condition) {
        if (condition == null || condition.isEmpty()) return false;
        condition = condition.trim();

        try {

            if (condition.startsWith("health")) {
                float health = mc.player.getHealth();
                return compareValue(condition.substring(6), health);
            }

            if (condition.startsWith("hunger")) {
                int hunger = mc.player.getFoodData().getFoodLevel();
                return compareIntValue(condition.substring(6), hunger);
            }

            if (condition.startsWith("level")) {
                int level = mc.player.experienceLevel;
                return compareIntValue(condition.substring(5), level);
            }

            if (condition.startsWith("holding:")) {
                String itemName = condition.substring(8).toLowerCase();
                ItemStack mainHand = mc.player.getMainHandItem();
                ItemStack offHand = mc.player.getOffhandItem();
                return mainHand.getItemName().getString().toLowerCase().contains(itemName) ||
                       offHand.getItemName().getString().toLowerCase().contains(itemName);
            }

            if (condition.startsWith("dimension:")) {
                String dim = condition.substring(10).toLowerCase();
                String current = mc.level.dimension().identifier().toString().toLowerCase();
                return current.contains(dim);
            }

            if (condition.equals("sneaking")) return mc.player.isShiftKeyDown();
            if (condition.equals("!sneaking")) return !mc.player.isShiftKeyDown();

            if (condition.equals("sprinting")) return mc.player.isSprinting();
            if (condition.equals("!sprinting")) return !mc.player.isSprinting();

            if (condition.equals("raining")) return mc.level.isRaining();
            if (condition.equals("!raining")) return !mc.level.isRaining();

            if (condition.equals("day")) {
                long time = mc.level.getLevelData().getGameTime() % 24000;
                return time >= 0 && time < 12000;
            }
            if (condition.equals("night")) {
                long time = mc.level.getLevelData().getGameTime() % 24000;
                return time >= 12000 && time < 24000;
            }

            if (condition.startsWith("tps")) {
                float tps = estimateTPS();
                return compareValue(condition.substring(3), tps);
            }

            if (condition.startsWith("slot:")) {
                String rest = condition.substring(5);
                String[] parts = rest.split(":", 2);
                if (parts.length == 2) {
                    int slot = Integer.parseInt(parts[0]);
                    String itemName = parts[1].toLowerCase();
                    if (slot >= 0 && slot < mc.player.getInventory().getContainerSize()) {
                        ItemStack stack = mc.player.getInventory().getItem(slot);
                        return stack.getItemName().getString().toLowerCase().contains(itemName);
                    }
                }
            }

        } catch (Exception ignored) {}

        return false;
    }

    private boolean compareValue(String expr, float current) {
        expr = expr.trim();

        if (expr.startsWith("==")) return current == Float.parseFloat(expr.substring(2));
        if (expr.startsWith("<=")) return current <= Float.parseFloat(expr.substring(2));
        if (expr.startsWith(">=")) return current >= Float.parseFloat(expr.substring(2));
        if (expr.startsWith("<"))  return current < Float.parseFloat(expr.substring(1));
        if (expr.startsWith(">"))  return current > Float.parseFloat(expr.substring(1));
        if (expr.startsWith("="))  return current == Float.parseFloat(expr.substring(1));
        return false;
    }

    private boolean compareIntValue(String expr, int current) {
        expr = expr.trim();

        if (expr.startsWith("==")) return current == Integer.parseInt(expr.substring(2));
        if (expr.startsWith("<=")) return current <= Integer.parseInt(expr.substring(2));
        if (expr.startsWith(">=")) return current >= Integer.parseInt(expr.substring(2));
        if (expr.startsWith("<"))  return current < Integer.parseInt(expr.substring(1));
        if (expr.startsWith(">"))  return current > Integer.parseInt(expr.substring(1));
        if (expr.startsWith("="))  return current == Integer.parseInt(expr.substring(1));
        return false;
    }

    private void toggleModule(String name) {

        meteordevelopment.meteorclient.systems.modules.Modules modules =
            meteordevelopment.meteorclient.systems.modules.Modules.get();

        for (Module module : modules.getAll()) {
            if (module.name.equalsIgnoreCase(name) ||
                module.name.replace("-", " ").equalsIgnoreCase(name.replace("_", " "))) {
                module.toggle();
                info("Toggled module: %s → %s", module.name, module.isActive() ? "ON" : "OFF");
                return;
            }
        }
        info("Module not found: %s", name);
    }

    public void addTrigger(Trigger trigger) {
        triggers.add(trigger);
        info("Added trigger: %s (%s)", trigger.id, trigger.type);
    }

    public void removeTrigger(String id) {
        triggers.removeIf(t -> t.id.equals(id));
        info("Removed trigger: %s", id);
    }

    public void enableTrigger(String id) {
        for (Trigger t : triggers) {
            if (t.id.equals(id)) { t.enabled = true; break; }
        }
    }

    public void disableTrigger(String id) {
        for (Trigger t : triggers) {
            if (t.id.equals(id)) { t.enabled = false; break; }
        }
    }

    public List<Trigger> getTriggers() {
        return Collections.unmodifiableList(triggers);
    }

    public void setTriggerCooldown(String id, long cooldownMs) {
        for (Trigger t : triggers) {
            if (t.id.equals(id)) { t.cooldownMs = cooldownMs; break; }
        }
    }

    public boolean evaluateLogic(List<Boolean> results) {
        if (results.isEmpty()) return false;
        if (logicType.get() == LogicType.And) {
            for (boolean b : results) { if (!b) return false; }
            return true;
        } else {
            for (boolean b : results) { if (b) return true; }
            return false;
        }
    }

    public void saveMacro(String name, List<String> actions) {
        Macro macro = new Macro(name, new ArrayList<>(actions));
        macros.put(name.toLowerCase(), macro);

        try {
            Path macroDir = Paths.get("orbiter-macros");
            if (!Files.exists(macroDir)) {
                Files.createDirectories(macroDir);
            }
            Path macroFile = macroDir.resolve(name.toLowerCase() + ".macro");
            Files.write(macroFile, actions, java.nio.charset.StandardCharsets.UTF_8);
            info("Saved macro: %s (%d actions)", name, actions.size());
        } catch (IOException e) {
            warning("Failed to save macro '%s': %s", name, e.getMessage());
        }
    }

    public Macro loadMacro(String name) {

        Macro cached = macros.get(name.toLowerCase());
        if (cached != null) return cached;

        try {
            Path macroFile = Paths.get("orbiter-macros", name.toLowerCase() + ".macro");
            if (Files.exists(macroFile)) {
                List<String> lines = Files.readAllLines(macroFile, java.nio.charset.StandardCharsets.UTF_8);
                Macro macro = new Macro(name, lines);
                macros.put(name.toLowerCase(), macro);
                info("Loaded macro: %s (%d actions)", name, lines.size());
                return macro;
            }
        } catch (IOException e) {
            warning("Failed to load macro '%s': %s", name, e.getMessage());
        }
        return null;
    }

    public void executeMacro(String name) {
        Macro macro = loadMacro(name);
        if (macro == null) {
            info("Macro not found: %s", name);
            return;
        }

        info("Executing macro: %s (%d actions)", name, macro.actions.size());
        long delayMs = 100;

        for (int i = 0; i < macro.actions.size(); i++) {
            String action = macro.actions.get(i);
            if (action.startsWith("delay:")) {

                try {
                    String rest = action.substring(6);
                    int spaceIdx = rest.indexOf(' ');
                    if (spaceIdx > 0) {
                        long d = Long.parseLong(rest.substring(0, spaceIdx).trim());
                        String subAction = rest.substring(spaceIdx + 1).trim();
                        delayedActions.add(new DelayedAction(subAction, i * delayMs + d));
                    } else {
                        long d = Long.parseLong(rest.trim());
                        delayMs = d;
                    }
                } catch (NumberFormatException ignored) {}
            } else {
                delayedActions.add(new DelayedAction(action, (long) i * delayMs));
            }
        }
    }

    public List<String> listMacros() {
        List<String> names = new ArrayList<>(macros.keySet());

        try {
            Path macroDir = Paths.get("orbiter-macros");
            if (Files.exists(macroDir)) {
                Files.list(macroDir)
                    .filter(p -> p.toString().endsWith(".macro"))
                    .map(p -> p.getFileName().toString().replace(".macro", ""))
                    .filter(name -> !names.contains(name))
                    .forEach(names::add);
            }
        } catch (IOException ignored) {}

        Collections.sort(names);
        return names;
    }

    public void deleteMacro(String name) {
        macros.remove(name.toLowerCase());
        try {
            Path macroFile = Paths.get("orbiter-macros", name.toLowerCase() + ".macro");
            Files.deleteIfExists(macroFile);
            info("Deleted macro: %s", name);
        } catch (IOException e) {
            warning("Failed to delete macro '%s': %s", name, e.getMessage());
        }
    }

    private void loadAllMacros() {
        macros.clear();
        try {
            Path macroDir = Paths.get("orbiter-macros");
            if (Files.exists(macroDir)) {
                Files.list(macroDir)
                    .filter(p -> p.toString().endsWith(".macro"))
                    .forEach(p -> {
                        String name = p.getFileName().toString().replace(".macro", "");
                        loadMacro(name);
                    });
            }
        } catch (IOException ignored) {}
    }

    public void saveTriggers() {
        try {
            Path triggerDir = Paths.get("orbiter-triggers");
            if (!Files.exists(triggerDir)) {
                Files.createDirectories(triggerDir);
            }

            for (Trigger trigger : triggers) {
                Path triggerFile = triggerDir.resolve(trigger.id + ".trigger");
                StringBuilder sb = new StringBuilder();
                sb.append("__type=").append(trigger.type).append("\n");
                sb.append("__id=").append(trigger.id).append("\n");
                sb.append("__enabled=").append(trigger.enabled).append("\n");
                sb.append("__cooldown=").append(trigger.cooldownMs).append("\n");

                for (Map.Entry<String, String> entry : trigger.config.entrySet()) {
                    sb.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
                }

                for (int i = 0; i < trigger.actions.size(); i++) {
                    sb.append("__action").append(i).append("=").append(trigger.actions.get(i)).append("\n");
                }

                Files.write(triggerFile, sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            info("Saved %d triggers.", triggers.size());
        } catch (IOException e) {
            warning("Failed to save triggers: %s", e.getMessage());
        }
    }

    public void loadTriggers() {
        triggers.clear();
        try {
            Path triggerDir = Paths.get("orbiter-triggers");
            if (!Files.exists(triggerDir)) return;

            Files.list(triggerDir)
                .filter(p -> p.toString().endsWith(".trigger"))
                .forEach(p -> {
                    try {
                        List<String> lines = Files.readAllLines(p, java.nio.charset.StandardCharsets.UTF_8);
                        String type = "", id = "";
                        boolean enabled = true;
                        long cooldown = 0;
                        Map<String, String> config = new HashMap<>();
                        List<String> actions = new ArrayList<>();

                        for (String line : lines) {
                            if (line.startsWith("__type=")) {
                                type = line.substring(7);
                            } else if (line.startsWith("__id=")) {
                                id = line.substring(5);
                            } else if (line.startsWith("__enabled=")) {
                                enabled = Boolean.parseBoolean(line.substring(10));
                            } else if (line.startsWith("__cooldown=")) {
                                cooldown = Long.parseLong(line.substring(11));
                            } else if (line.startsWith("__action")) {
                                int eqIdx = line.indexOf('=');
                                if (eqIdx > 0) {
                                    actions.add(line.substring(eqIdx + 1));
                                }
                            } else if (line.contains("=")) {
                                int eqIdx = line.indexOf('=');
                                config.put(line.substring(0, eqIdx), line.substring(eqIdx + 1));
                            }
                        }

                        Trigger trigger = new Trigger(id, type, config, actions);
                        trigger.enabled = enabled;
                        trigger.cooldownMs = cooldown;
                        triggers.add(trigger);
                    } catch (IOException ignored) {}
                });

            info("Loaded %d triggers.", triggers.size());
        } catch (IOException e) {
            warning("Failed to load triggers: %s", e.getMessage());
        }
    }

    public Trigger createHealthTrigger(String id, double threshold, boolean highDamage, String action) {
        Map<String, String> config = new HashMap<>();
        config.put("threshold", String.valueOf(threshold));
        config.put("highDamage", String.valueOf(highDamage));
        return new Trigger(id, "health", config, Collections.singletonList(action));
    }

    public Trigger createDurabilityTrigger(String id, int threshold, String action) {
        Map<String, String> config = new HashMap<>();
        config.put("threshold", String.valueOf(threshold));
        return new Trigger(id, "durability", config, Collections.singletonList(action));
    }

    public Trigger createPlayerRangeTrigger(String id, double distance, boolean inside, String action) {
        Map<String, String> config = new HashMap<>();
        config.put("distance", String.valueOf(distance));
        config.put("inside", String.valueOf(inside));
        return new Trigger(id, "playerRange", config, Collections.singletonList(action));
    }

    public Trigger createEntityNearTrigger(String id, double distance, String filter, String action) {
        Map<String, String> config = new HashMap<>();
        config.put("distance", String.valueOf(distance));
        config.put("filter", filter != null ? filter : "");
        return new Trigger(id, "entityNear", config, Collections.singletonList(action));
    }

    public Trigger createTotemTrigger(String id, boolean self, String action) {
        Map<String, String> config = new HashMap<>();
        config.put("self", String.valueOf(self));
        return new Trigger(id, "totem", config, Collections.singletonList(action));
    }

    public Trigger createHeldItemTrigger(String id, String filter, String action) {
        Map<String, String> config = new HashMap<>();
        config.put("filter", filter != null ? filter : "");
        return new Trigger(id, "heldItem", config, Collections.singletonList(action));
    }

    public Trigger createLagTrigger(String id, double tpsThreshold, boolean below, String action) {
        Map<String, String> config = new HashMap<>();
        config.put("tpsThreshold", String.valueOf(tpsThreshold));
        config.put("below", String.valueOf(below));
        return new Trigger(id, "lag", config, Collections.singletonList(action));
    }

    public Trigger createChatMatchTrigger(String id, String regex, String action) {
        Map<String, String> config = new HashMap<>();
        config.put("regex", regex);
        return new Trigger(id, "chatMatch", config, Collections.singletonList(action));
    }

    public Trigger createDimensionChangeTrigger(String id, String action) {
        return new Trigger(id, "dimensionChange", new HashMap<>(), Collections.singletonList(action));
    }

    public Trigger createDeathTrigger(String id, String action) {
        return new Trigger(id, "onDeath", new HashMap<>(), Collections.singletonList(action));
    }

    public Trigger createRespawnTrigger(String id, String action) {
        return new Trigger(id, "onRespawn", new HashMap<>(), Collections.singletonList(action));
    }

    public Trigger createBlockBreakTrigger(String id, String blockFilter, String action) {
        Map<String, String> config = new HashMap<>();
        config.put("blockFilter", blockFilter != null ? blockFilter : "");
        return new Trigger(id, "onBlockBreak", config, Collections.singletonList(action));
    }

    public Trigger createBlockPlaceTrigger(String id, String action) {
        return new Trigger(id, "onBlockPlace", new HashMap<>(), Collections.singletonList(action));
    }

    public Trigger createXPGainTrigger(String id, int threshold, String action) {
        Map<String, String> config = new HashMap<>();
        config.put("threshold", String.valueOf(threshold));
        return new Trigger(id, "onXPGain", config, Collections.singletonList(action));
    }

    public Trigger createGamemodeChangeTrigger(String id, String action) {
        return new Trigger(id, "onGamemodeChange", new HashMap<>(), Collections.singletonList(action));
    }

    public Trigger createWeatherChangeTrigger(String id, boolean rainStart, boolean rainStop, String action) {
        Map<String, String> config = new HashMap<>();
        config.put("rainStart", String.valueOf(rainStart));
        config.put("rainStop", String.valueOf(rainStop));
        return new Trigger(id, "onWeatherChange", config, Collections.singletonList(action));
    }

    public Trigger createMoveDistanceTrigger(String id, double distance, boolean repeat, String action) {
        Map<String, String> config = new HashMap<>();
        config.put("distance", String.valueOf(distance));
        config.put("repeat", String.valueOf(repeat));
        return new Trigger(id, "onMoveDistance", config, Collections.singletonList(action));
    }

    public Trigger createTimeInWorldTrigger(String id, int ticks, boolean repeat, String action) {
        Map<String, String> config = new HashMap<>();
        config.put("ticks", String.valueOf(ticks));
        config.put("repeat", String.valueOf(repeat));
        return new Trigger(id, "onTimeInWorld", config, Collections.singletonList(action));
    }

    public Trigger createPotionEffectTrigger(String id, String filter, boolean gained, boolean lost, String action) {
        Map<String, String> config = new HashMap<>();
        config.put("filter", filter != null ? filter : "");
        config.put("gained", String.valueOf(gained));
        config.put("lost", String.valueOf(lost));
        return new Trigger(id, "onPotionEffect", config, Collections.singletonList(action));
    }

    public Trigger createArmorBreakTrigger(String id, String slot, String action) {
        Map<String, String> config = new HashMap<>();
        config.put("slot", slot != null ? slot : "");
        return new Trigger(id, "onArmorBreak", config, Collections.singletonList(action));
    }

    public Trigger createTargetSwitchTrigger(String id, int comboMin, String action) {
        Map<String, String> config = new HashMap<>();
        config.put("comboMin", String.valueOf(comboMin));
        return new Trigger(id, "onTargetSwitch", config, Collections.singletonList(action));
    }

    public Trigger createTitleActionbarTrigger(String id, String regex, String action) {
        Map<String, String> config = new HashMap<>();
        config.put("regex", regex);
        return new Trigger(id, "onTitleActionbar", config, Collections.singletonList(action));
    }

    public Trigger createScreenOpenTrigger(String id, String screenFilter, String action) {
        Map<String, String> config = new HashMap<>();
        config.put("screenFilter", screenFilter != null ? screenFilter : "");
        return new Trigger(id, "onScreenOpen", config, Collections.singletonList(action));
    }

    public Trigger createVelocityTrigger(String id, double threshold, String action) {
        Map<String, String> config = new HashMap<>();
        config.put("threshold", String.valueOf(threshold));
        return new Trigger(id, "velocity", config, Collections.singletonList(action));
    }

    public Trigger createPearlTrigger(String id, boolean land, String action) {
        Map<String, String> config = new HashMap<>();
        config.put("land", String.valueOf(land));
        return new Trigger(id, "pearl", config, Collections.singletonList(action));
    }

    public Trigger createLowMaterialTrigger(String id, String item, int threshold, String action) {
        Map<String, String> config = new HashMap<>();
        config.put("item", item);
        config.put("threshold", String.valueOf(threshold));
        return new Trigger(id, "lowMaterial", config, Collections.singletonList(action));
    }

    public Trigger createUsedItemTrigger(String id, String filter, String action) {
        Map<String, String> config = new HashMap<>();
        config.put("filter", filter != null ? filter : "");
        return new Trigger(id, "usedItem", config, Collections.singletonList(action));
    }

    public Trigger createNearbyPlayerItemTrigger(String id, String filter, double range, String action) {
        Map<String, String> config = new HashMap<>();
        config.put("filter", filter != null ? filter : "");
        config.put("range", String.valueOf(range));
        return new Trigger(id, "nearbyPlayerItem", config, Collections.singletonList(action));
    }

    public Trigger createPickupTrigger(String id, String filter, String action) {
        Map<String, String> config = new HashMap<>();
        config.put("filter", filter != null ? filter : "");
        return new Trigger(id, "pickup", config, Collections.singletonList(action));
    }

    public void fireTriggersOfType(String type) {
        long now = System.currentTimeMillis();
        for (Trigger trigger : triggers) {
            if (!trigger.enabled) continue;
            if (!trigger.type.equals(type)) continue;
            if (trigger.cooldownMs > 0 && (now - trigger.lastFired) < trigger.cooldownMs) continue;

            trigger.lastFired = now;
            for (String action : trigger.actions) {
                fire(action);
            }
        }
    }

    public boolean processCommand(String command) {
        if (command == null || !command.startsWith(".actions")) return false;

        String[] parts = command.split("\\s+");
        if (parts.length < 2) {
            info("Usage: .actions <trigger|macro|fire|save|load> ...");
            return true;
        }

        String sub = parts[1].toLowerCase();

        switch (sub) {
            case "trigger": {
                if (parts.length < 3) {
                    info("Usage: .actions trigger <add|remove|list|enable|disable> ...");
                    return true;
                }
                String triggerOp = parts[2].toLowerCase();
                switch (triggerOp) {
                    case "add": {
                        if (parts.length < 6) {
                            info("Usage: .actions trigger add <id> <type> <action>");
                            return true;
                        }
                        String id = parts[3];
                        String type = parts[4];
                        String action = String.join(" ", Arrays.copyOfRange(parts, 5, parts.length));
                        addTrigger(new Trigger(id, type, new HashMap<>(), Collections.singletonList(action)));
                        return true;
                    }
                    case "remove": {
                        if (parts.length < 4) {
                            info("Usage: .actions trigger remove <id>");
                            return true;
                        }
                        removeTrigger(parts[3]);
                        return true;
                    }
                    case "list": {
                        if (triggers.isEmpty()) {
                            info("No triggers defined.");
                        } else {
                            for (Trigger t : triggers) {
                                info("[%s] %s: %s → %s", t.enabled ? "ON" : "OFF", t.id, t.type, String.join(";;", t.actions));
                            }
                        }
                        return true;
                    }
                    case "enable": {
                        if (parts.length < 4) return true;
                        enableTrigger(parts[3]);
                        return true;
                    }
                    case "disable": {
                        if (parts.length < 4) return true;
                        disableTrigger(parts[3]);
                        return true;
                    }
                    default:
                        info("Unknown trigger operation: %s", triggerOp);
                        return true;
                }
            }

            case "macro": {
                if (parts.length < 3) {
                    info("Usage: .actions macro <save|load|list|delete|run> ...");
                    return true;
                }
                String macroOp = parts[2].toLowerCase();
                switch (macroOp) {
                    case "save": {
                        if (parts.length < 5) {
                            info("Usage: .actions macro save <name> <action1>;;<action2>;;...");
                            return true;
                        }
                        String name = parts[3];
                        String allActions = String.join(" ", Arrays.copyOfRange(parts, 4, parts.length));
                        List<String> actions = Arrays.asList(allActions.split(";;"));
                        saveMacro(name, actions);
                        return true;
                    }
                    case "load": {
                        if (parts.length < 4) return true;
                        Macro m = loadMacro(parts[3]);
                        if (m != null) {
                            info("Macro '%s': %d actions", m.name, m.actions.size());
                            for (int i = 0; i < m.actions.size(); i++) {
                                info("  %d: %s", i + 1, m.actions.get(i));
                            }
                        } else {
                            info("Macro not found: %s", parts[3]);
                        }
                        return true;
                    }
                    case "list": {
                        List<String> names = listMacros();
                        if (names.isEmpty()) {
                            info("No macros defined.");
                        } else {
                            for (String n : names) {
                                Macro m = macros.get(n);
                                info("  %s (%d actions)", n, m != null ? m.actions.size() : 0);
                            }
                        }
                        return true;
                    }
                    case "delete": {
                        if (parts.length < 4) return true;
                        deleteMacro(parts[3]);
                        return true;
                    }
                    case "run": {
                        if (parts.length < 4) return true;
                        executeMacro(parts[3]);
                        return true;
                    }
                    default:
                        info("Unknown macro operation: %s", macroOp);
                        return true;
                }
            }

            case "fire": {
                if (parts.length < 3) {
                    info("Usage: .actions fire <action>");
                    return true;
                }
                String action = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));
                fire(action);
                return true;
            }

            case "save": {
                saveTriggers();
                return true;
            }

            case "load": {
                loadTriggers();
                return true;
            }

            default:
                info("Unknown subcommand: %s", sub);
                return true;
        }
    }

    public static class CompositeTrigger {
        public final String id;
        public final LogicType logic;
        public final List<Trigger> subTriggers;
        public final List<String> actions;
        public boolean enabled = true;
        public long lastFired = 0;
        public long cooldownMs = 0;

        public CompositeTrigger(String id, LogicType logic, List<Trigger> subTriggers, List<String> actions) {
            this.id = id;
            this.logic = logic;
            this.subTriggers = subTriggers;
            this.actions = actions;
        }
    }

    private final List<CompositeTrigger> compositeTriggers = new CopyOnWriteArrayList<>();

    public void addCompositeTrigger(CompositeTrigger ct) {
        compositeTriggers.add(ct);
        info("Added composite trigger: %s (%s, %d sub-triggers)", ct.id, ct.logic, ct.subTriggers.size());
    }

    public void removeCompositeTrigger(String id) {
        compositeTriggers.removeIf(ct -> ct.id.equals(id));
    }

    public static class ActionChain {
        public final String id;
        public final List<ChainStep> steps;
        public boolean enabled = true;

        public ActionChain(String id, List<ChainStep> steps) {
            this.id = id;
            this.steps = steps;
        }
    }

    public static class ChainStep {
        public final String action;
        public final long delayMs;

        public ChainStep(String action, long delayMs) {
            this.action = action;
            this.delayMs = delayMs;
        }
    }

    private final List<ActionChain> actionChains = new CopyOnWriteArrayList<>();

    public void addActionChain(ActionChain chain) {
        actionChains.add(chain);
    }

    public void removeActionChain(String id) {
        actionChains.removeIf(c -> c.id.equals(id));
    }

    public void executeActionChain(String id) {
        for (ActionChain chain : actionChains) {
            if (chain.id.equals(id) && chain.enabled) {
                long cumulativeDelay = 0;
                for (ChainStep step : chain.steps) {
                    cumulativeDelay += step.delayMs;
                    delayedActions.add(new DelayedAction(step.action, cumulativeDelay));
                }
                return;
            }
        }
        info("Action chain not found: %s", id);
    }

    private final Map<String, String> stateVariables = new ConcurrentHashMap<>();

    public void setStateVar(String key, String value) {
        stateVariables.put(key, value);
    }

    public String getStateVar(String key) {
        return stateVariables.getOrDefault(key, "");
    }

    public void clearStateVar(String key) {
        stateVariables.remove(key);
    }

    public Map<String, String> getAllStateVars() {
        return Collections.unmodifiableMap(stateVariables);
    }

    private boolean evaluateExtendedCondition(String condition) {
        if (condition.startsWith("var:")) {
            String rest = condition.substring(4);
            int opIdx = -1;
            String op = "";

            if (rest.contains(">=")) { opIdx = rest.indexOf(">="); op = ">="; }
            else if (rest.contains("<=")) { opIdx = rest.indexOf("<="); op = "<="; }
            else if (rest.contains("==")) { opIdx = rest.indexOf("=="); op = "=="; }
            else if (rest.contains("!=")) { opIdx = rest.indexOf("!="); op = "!="; }
            else if (rest.contains("="))  { opIdx = rest.indexOf("="); op = "="; }
            else if (rest.contains(">"))  { opIdx = rest.indexOf(">"); op = ">"; }
            else if (rest.contains("<"))  { opIdx = rest.indexOf("<"); op = "<"; }

            if (opIdx >= 0) {
                String key = rest.substring(0, opIdx);
                String valueStr = rest.substring(opIdx + op.length());
                String currentVal = stateVariables.getOrDefault(key, "0");

                try {
                    double current = Double.parseDouble(currentVal);
                    double compare = Double.parseDouble(valueStr);
                    switch (op) {
                        case ">=": return current >= compare;
                        case "<=": return current <= compare;
                        case "==": return current == compare;
                        case "!=": return current != compare;
                        case ">":  return current > compare;
                        case "<":  return current < compare;
                        case "=":  return currentVal.equals(valueStr);
                    }
                } catch (NumberFormatException e) {

                    if (op.equals("=")) return currentVal.equals(valueStr);
                    if (op.equals("!=")) return !currentVal.equals(valueStr);
                }
            }
            return false;
        }

        return evaluateCondition(condition);
    }

    private void fireExtended(String action) {
        if (action == null || action.isEmpty()) return;

        if (action.startsWith("set:")) {
            String rest = action.substring(4);
            int eqIdx = rest.indexOf('=');
            if (eqIdx > 0) {
                String key = rest.substring(0, eqIdx);
                String value = rest.substring(eqIdx + 1);
                stateVariables.put(key, value);
                if (logActions.get()) info("Set state: %s=%s", key, value);
            }
            return;
        }

        if (action.startsWith("increment:")) {
            String rest = action.substring(10);
            String[] parts = rest.split(":", 2);
            if (parts.length == 2) {
                String key = parts[0];
                try {
                    double current = Double.parseDouble(stateVariables.getOrDefault(key, "0"));
                    double delta = Double.parseDouble(parts[1]);
                    stateVariables.put(key, String.valueOf(current + delta));
                } catch (NumberFormatException ignored) {}
            }
            return;
        }

        if (action.startsWith("decrement:")) {
            String rest = action.substring(10);
            String[] parts = rest.split(":", 2);
            if (parts.length == 2) {
                String key = parts[0];
                try {
                    double current = Double.parseDouble(stateVariables.getOrDefault(key, "0"));
                    double delta = Double.parseDouble(parts[1]);
                    stateVariables.put(key, String.valueOf(current - delta));
                } catch (NumberFormatException ignored) {}
            }
            return;
        }

        if (action.startsWith("reset:")) {
            stateVariables.remove(action.substring(6));
            return;
        }

        if (action.startsWith("chain:")) {
            executeActionChain(action.substring(6));
            return;
        }

        fireSingle(action);
    }

    private final Map<Integer, Long> thrownPearls = new ConcurrentHashMap<>();

    public void trackPearl(int entityId) {
        thrownPearls.put(entityId, currentTick);
    }

    public void onPearlLand(int entityId) {
        Long thrownTick = thrownPearls.remove(entityId);
        if (thrownTick != null && pearlEnabled.get() && pearlLand.get()) {
            fire(pearlAction.get());
        }
    }

    private Vec3 lastVelocity = Vec3.ZERO;

    private void trackVelocity() {
        if (mc.player == null) return;
        Vec3 currentVelocity = mc.player.getDeltaMovement();
        double magnitude = currentVelocity.length();

        if (velocityEnabled.get() && magnitude >= velocityThreshold.get()) {
            fire(velocityAction.get());
        }

        lastVelocity = currentVelocity;
    }

    private final LinkedList<Long> tickTimestamps = new LinkedList<>();
    private static final int TPS_SAMPLE_SIZE = 20;

    private void updateTPSTracker() {
        long now = System.currentTimeMillis();
        tickTimestamps.addLast(now);

        while (tickTimestamps.size() > TPS_SAMPLE_SIZE) {
            tickTimestamps.removeFirst();
        }
    }

    private float calculateTPS() {
        if (tickTimestamps.size() < 2) return 20.0f;

        long oldest = tickTimestamps.getFirst();
        long newest = tickTimestamps.getLast();
        long elapsed = newest - oldest;

        if (elapsed <= 0) return 20.0f;

        float tps = (tickTimestamps.size() - 1) * 1000.0f / elapsed;
        return Math.min(tps, 20.0f);
    }

    private final Map<String, Integer> lastItemCounts = new ConcurrentHashMap<>();

    private void updateInventoryMonitor() {
        if (mc.player == null) return;

        Map<String, Integer> currentCounts = new HashMap<>();
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                String name = stack.getItemName().getString().toLowerCase();
                currentCounts.merge(name, stack.getCount(), Integer::sum);
            }
        }

        if (lowMaterialEnabled.get()) {
            String target = lowMaterialItem.get().toLowerCase();
            int current = 0;
            for (Map.Entry<String, Integer> entry : currentCounts.entrySet()) {
                if (entry.getKey().contains(target)) {
                    current += entry.getValue();
                }
            }
        }

        lastItemCounts.clear();
        lastItemCounts.putAll(currentCounts);
    }

    private long totalActionsFired = 0;
    private long totalTriggersFired = 0;
    private final Map<String, Long> triggerFireCounts = new ConcurrentHashMap<>();

    public long getTotalActionsFired() { return totalActionsFired; }
    public long getTotalTriggersFired() { return totalTriggersFired; }

    public Map<String, Long> getTriggerFireStats() {
        return Collections.unmodifiableMap(triggerFireCounts);
    }

    public void resetStats() {
        totalActionsFired = 0;
        totalTriggersFired = 0;
        triggerFireCounts.clear();
    }

    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Actions Module Debug Info:\n");
        sb.append("  Triggers: ").append(triggers.size()).append("\n");
        sb.append("  Composite Triggers: ").append(compositeTriggers.size()).append("\n");
        sb.append("  Action Chains: ").append(actionChains.size()).append("\n");
        sb.append("  Macros: ").append(macros.size()).append("\n");
        sb.append("  State Variables: ").append(stateVariables.size()).append("\n");
        sb.append("  Total Actions Fired: ").append(totalActionsFired).append("\n");
        sb.append("  Total Triggers Fired: ").append(totalTriggersFired).append("\n");
        sb.append("  Delayed Actions Pending: ").append(delayedActions.size()).append("\n");
        sb.append("  Scheduled Actions Pending: ").append(scheduledActions.size()).append("\n");
        sb.append("  Current Tick: ").append(currentTick).append("\n");
        sb.append("  Last Health: ").append(lastHealth).append("\n");
        sb.append("  Was Dead: ").append(wasDead).append("\n");
        sb.append("  Was Raining: ").append(wasRaining).append("\n");
        sb.append("  Accumulated Distance: ").append(accumulatedDistance).append("\n");
        return sb.toString();
    }

    public String exportAll() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Orbiter Actions Export\n");
        sb.append("# Triggers\n");
        for (Trigger t : triggers) {
            sb.append("TRIGGER:").append(t.id).append(":").append(t.type).append(":")
              .append(t.enabled).append(":").append(t.cooldownMs);
            for (Map.Entry<String, String> e : t.config.entrySet()) {
                sb.append(":C:").append(e.getKey()).append("=").append(e.getValue());
            }
            for (String a : t.actions) {
                sb.append(":A:").append(a);
            }
            sb.append("\n");
        }
        sb.append("# Macros\n");
        for (Macro m : macros.values()) {
            sb.append("MACRO:").append(m.name);
            for (String a : m.actions) {
                sb.append(":").append(a.replace(":", "\\:"));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public void importAll(String data) {
        if (data == null || data.isEmpty()) return;

        for (String line : data.split("\n")) {
            line = line.trim();
            if (line.startsWith("#") || line.isEmpty()) continue;

            if (line.startsWith("TRIGGER:")) {
                String[] parts = line.substring(8).split(":");
                if (parts.length < 4) continue;
                String id = parts[0];
                String type = parts[1];
                boolean enabled = Boolean.parseBoolean(parts[2]);
                long cooldown = Long.parseLong(parts[3]);
                Map<String, String> config = new HashMap<>();
                List<String> actions = new ArrayList<>();

                for (int i = 4; i < parts.length; i++) {
                    if (parts[i].startsWith("C:")) {
                        String[] kv = parts[i].substring(2).split("=", 2);
                        if (kv.length == 2) config.put(kv[0], kv[1]);
                    } else if (parts[i].startsWith("A:")) {
                        actions.add(parts[i].substring(2));
                    }
                }

                Trigger trigger = new Trigger(id, type, config, actions);
                trigger.enabled = enabled;
                trigger.cooldownMs = cooldown;
                triggers.add(trigger);
            }

            if (line.startsWith("MACRO:")) {
                String[] parts = line.substring(6).split(":");
                if (parts.length < 1) continue;
                String name = parts[0];
                List<String> actions = new ArrayList<>();
                for (int i = 1; i < parts.length; i++) {
                    actions.add(parts[i].replace("\\:", ":"));
                }
                macros.put(name.toLowerCase(), new Macro(name, actions));
            }
        }

        info("Import complete. %d triggers, %d macros.", triggers.size(), macros.size());
    }

    public float getCurrentHealth() {
        return mc.player != null ? mc.player.getHealth() : 0;
    }

    public int getCurrentHunger() {
        return mc.player != null ? mc.player.getFoodData().getFoodLevel() : 0;
    }

    public int getCurrentXPLevel() {
        return mc.player != null ? mc.player.experienceLevel : 0;
    }

    public String getCurrentDimension() {
        if (mc.level == null) return "unknown";
        return mc.level.dimension().identifier().toString();
    }

    public double getDistanceTraveled() {
        return accumulatedDistance;
    }

    public long getTicksSinceJoin() {
        return currentTick - joinTick;
    }

    public long getSecondsSinceJoin() {
        return (currentTick - joinTick) / 20;
    }

    public boolean isInCombat() {
        return mc.crosshairPickEntity != null && mc.crosshairPickEntity instanceof LivingEntity;
    }

    public int getCurrentCombo() {
        if (mc.crosshairPickEntity != null && mc.crosshairPickEntity instanceof LivingEntity) {
            return ComboTracker.getCombo(mc.crosshairPickEntity.getUUID());
        }
        return 0;
    }

    public boolean isFullyArmored() {
        if (mc.player == null) return false;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (mc.player.getItemBySlot(slot).isEmpty()) return false;
        }
        return true;
    }

    public int getTotalArmorPoints() {
        if (mc.player == null) return 0;
        return mc.player.getArmorValue();
    }

    public int getItemCount(String itemName) {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItemName().getString().toLowerCase().contains(itemName.toLowerCase())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public String getCurrentGamemode() {
        if (mc.gameMode == null) return "unknown";
        return mc.gameMode.getPlayerMode().name().toLowerCase();
    }

    public float getTPS() {
        return calculateTPS();
    }

    public double getPlayerY() {
        return mc.player != null ? mc.player.getY() : 0;
    }

    public boolean isInNether() {
        return mc.level != null && mc.level.dimension() == Level.NETHER;
    }

    public boolean isInEnd() {
        return mc.level != null && mc.level.dimension() == Level.END;
    }

    public boolean isInOverworld() {
        return mc.level != null && mc.level.dimension() == Level.OVERWORLD;
    }

    public int getActiveEffectCount() {
        return mc.player != null ? mc.player.getActiveEffects().size() : 0;
    }

    public boolean hasEffect(String effectName) {
        if (mc.player == null) return false;
        for (MobEffectInstance inst : mc.player.getActiveEffects()) {
            if (inst.getEffect().value().getDisplayName().getString().toLowerCase().contains(effectName.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public int getNearbyPlayerCount(double range) {
        if (mc.player == null || mc.level == null) return 0;
        int count = 0;
        for (Player player : mc.level.players()) {
            if (player != mc.player && mc.player.distanceTo(player) <= range) {
                count++;
            }
        }
        return count;
    }

    public Player getClosestPlayer(double range) {
        if (mc.player == null || mc.level == null) return null;
        Player closest = null;
        double closestDist = range;
        for (Player player : mc.level.players()) {
            if (player != mc.player) {
                double dist = mc.player.distanceTo(player);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = player;
                }
            }
        }
        return closest;
    }

    public int getTriggerCount() {
        return triggers.size();
    }

    public int getMacroCount() {
        return macros.size();
    }

    public List<String> getActiveEffectNames() {
        if (mc.player == null) return Collections.emptyList();
        List<String> names = new ArrayList<>();
        for (MobEffectInstance inst : mc.player.getActiveEffects()) {
            names.add(inst.getEffect().value().getDisplayName().getString());
        }
        return names;
    }

    public void resetState() {
        lastHealth = mc.player != null ? mc.player.getHealth() : 20.0f;
        lastXPLevel = mc.player != null ? mc.player.experienceLevel : 0;
        wasDead = false;
        pendingRespawn = false;
        wasRaining = mc.level != null && mc.level.isRaining();
        lastDimension = mc.level != null ? mc.level.dimension() : Level.OVERWORLD;
        lastPosition = mc.player != null ? mc.player.getEyePosition() : Vec3.ZERO;
        accumulatedDistance = 0;
        joinTick = currentTick;
        lastTargetUuid = null;
        lastCombo = 0;
        stateVariables.clear();
        delayedActions.clear();
        scheduledActions.clear();
        thrownPearls.clear();
        tickTimestamps.clear();
        armorSlotFilled.clear();
        activeEffects.clear();
        info("All action state reset.");
    }
}
