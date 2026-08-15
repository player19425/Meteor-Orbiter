package orbiter.modules.misc;

import orbiter.mixin.ServerProtectCommandTreeAccessor;
import orbiter.Orbiter;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.nbt.NumericTag;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ServerProtect extends Module {

    public static ServerProtect get() {
        return Modules.get().get(ServerProtect.class);
    }

    private final SettingGroup sgCrash = settings.createGroup("PlayerCrasher");

    private final Setting<Boolean> checkExplosion = sgCrash.add(new BoolSetting.Builder()
        .name("explosion-validation")
        .description("Block malicious explosion packets with extreme coordinates/velocity.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> checkParticle = sgCrash.add(new BoolSetting.Builder()
        .name("particle-validation")
        .description("Block malicious particle packets with extreme count or position.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> checkPosition = sgCrash.add(new BoolSetting.Builder()
        .name("position-validation")
        .description("Block malicious position packets with extreme coordinates/rotation.")
        .defaultValue(true)
        .build());

    private final Setting<Double> maxWorldPos = sgCrash.add(new DoubleSetting.Builder()
        .name("max-world-position")
        .description("Maximum allowed coordinate value. Vanilla world is plus/minus 30M.")
        .defaultValue(30000000.0)
        .min(1000)
        .sliderRange(1000, 60000000)
        .build());

    private final Setting<Double> maxKnockback = sgCrash.add(new DoubleSetting.Builder()
        .name("max-knockback")
        .description("Maximum allowed knockback velocity. Vanilla never exceeds ~10.")
        .defaultValue(100.0)
        .min(10)
        .sliderRange(10, 1000)
        .build());

    private final Setting<Integer> maxParticleCount = sgCrash.add(new IntSetting.Builder()
        .name("max-particle-count")
        .description("Maximum particles per packet.")
        .defaultValue(5000)
        .min(100)
        .sliderRange(100, 50000)
        .build());

    private final Setting<Double> maxParticleSpeed = sgCrash.add(new DoubleSetting.Builder()
        .name("max-particle-speed")
        .description("Maximum absolute particle speed/offset accepted from one server packet.")
        .defaultValue(64.0)
        .min(0.0)
        .sliderRange(0.0, 1000.0)
        .build());

    private final Setting<Double> maxRotation = sgCrash.add(new DoubleSetting.Builder()
        .name("max-rotation")
        .description("Maximum allowed yaw/pitch in degrees.")
        .defaultValue(360.0)
        .min(90)
        .sliderRange(90, 720)
        .build());

    private final SettingGroup sgItems = settings.createGroup("Items");

    private final Setting<Boolean> sanitizeItems = sgItems.add(new BoolSetting.Builder()
        .name("sanitize-items")
        .description("Remove excessive lore from incoming items client-side.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> maxLoreLength = sgItems.add(new IntSetting.Builder()
        .name("max-lore-length")
        .description("Maximum total lore character count.")
        .defaultValue(2000)
        .min(100)
        .sliderRange(100, 10000)
        .visible(sanitizeItems::get)
        .build());

    private final Setting<Boolean> removeObfuscated = sgItems.add(new BoolSetting.Builder()
        .name("remove-obfuscated")
        .description("Remove obfuscated formatting from item lore and names.")
        .defaultValue(true)
        .visible(sanitizeItems::get)
        .build());

    private final Setting<Boolean> removeChinese = sgItems.add(new BoolSetting.Builder()
        .name("remove-cjk-abuse")
        .description("Strip massive CJK/Unicode character spam from item lore.")
        .defaultValue(true)
        .visible(sanitizeItems::get)
        .build());

    private final Setting<Boolean> sanitizeNames = sgItems.add(new BoolSetting.Builder()
        .name("sanitize-names")
        .description("Inspect custom names too (not just lore) for nested translate bombs and obfuscation abuse.")
        .defaultValue(true)
        .visible(sanitizeItems::get)
        .build());

    private final Setting<Integer> maxNameLength = sgItems.add(new IntSetting.Builder()
        .name("max-name-length")
        .description("Maximum combined character length of a custom item name.")
        .defaultValue(256)
        .min(16)
        .sliderRange(16, 4000)
        .visible(() -> sanitizeItems.get() && sanitizeNames.get())
        .build());

    private final Setting<Boolean> stripEntityData = sgItems.add(new BoolSetting.Builder()
        .name("legacy-remove-malicious-entity-data")
        .description("Legacy destructive fallback. Removes entity/block data only after it fails the malicious NBT scanner. "
            + "Disabled by default because custom spawn eggs legitimately use entity_data.")
        .defaultValue(false)
        .visible(sanitizeItems::get)
        .build());

    private final Setting<Boolean> validateEntityData = sgItems.add(new BoolSetting.Builder()
        .name("validate-entity-data")
        .description("Inspect entity and block-entity components using bounded validation.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> blockMaliciousEntityData = sgItems.add(new BoolSetting.Builder()
        .name("block-malicious-entity-data")
        .description("Block concretely malicious entity data instead of silently rewriting the live stack.")
        .defaultValue(true)
        .visible(validateEntityData::get)
        .build());

    private final Setting<Boolean> sanitizeCopyForTooltip = sgItems.add(new BoolSetting.Builder()
        .name("sanitize-copy-for-tooltip")
        .description("Show a local safe tooltip replacement without modifying the inventory stack.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> packetItemGuard = sgItems.add(new BoolSetting.Builder()
        .name("packet-item-guard")
        .description("Scan incoming screen-handler slot packets for crash items before the client accepts them.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> maxTextDepth = sgItems.add(new IntSetting.Builder()
        .name("max-text-depth")
        .description("Maximum nesting depth of translated Component (with/extra siblings). "
            + "Nested translate bombs crash the client by expanding exponentially.")
        .defaultValue(32)
        .min(4)
        .sliderRange(4, 256)
        .visible(sanitizeItems::get)
        .build());

    private final Setting<Integer> maxTranslateExpansion = sgItems.add(new IntSetting.Builder()
        .name("max-translate-expansion")
        .description("Maximum combined number of %s placeholder repetitions allowed across nested translates.")
        .defaultValue(32)
        .min(8)
        .sliderRange(8, 4096)
        .visible(sanitizeItems::get)
        .build());

    private final Setting<Boolean> floorItemLimit = sgItems.add(new BoolSetting.Builder()
        .name("floor-item-limit")
        .description("Limit dropped item entities on the ground.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> maxFloorItems = sgItems.add(new IntSetting.Builder()
        .name("max-floor-items")
        .description("Maximum dropped item entities before removing excess.")
        .defaultValue(200)
        .min(10)
        .sliderRange(10, 5000)
        .visible(floorItemLimit::get)
        .build());

    private final SettingGroup sgEntities = settings.createGroup("Entities");

    private final Setting<Boolean> entityLimit = sgEntities.add(new BoolSetting.Builder()
        .name("entity-limit")
        .description("Limit entities per type in loaded chunks.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> maxEntityTypeCount = sgEntities.add(new IntSetting.Builder()
        .name("max-per-entity-type")
        .description("Maximum entities of each type before removal.")
        .defaultValue(500)
        .min(50)
        .sliderRange(50, 10000)
        .visible(entityLimit::get)
        .build());

    private final Setting<Integer> maxTotalEntities = sgEntities.add(new IntSetting.Builder()
        .name("max-total-entities")
        .description("Maximum non-player entities retained in the local client world.")
        .defaultValue(2048)
        .min(50)
        .sliderRange(50, 20000)
        .visible(entityLimit::get)
        .build());

    private final Setting<Double> maxEntityBoundingSize = sgEntities.add(new DoubleSetting.Builder()
        .name("max-entity-bounding-size")
        .description("Locally discard entities with invalid or extreme render bounds.")
        .defaultValue(256.0)
        .min(1.0)
        .sliderRange(1.0, 4096.0)
        .visible(entityLimit::get)
        .build());

    private final Setting<Boolean> limitTnt = sgEntities.add(new BoolSetting.Builder()
        .name("limit-tnt")
        .description("Specifically limit TNT entities.")
        .defaultValue(true)
        .visible(entityLimit::get)
        .build());

    private final Setting<Integer> maxTnt = sgEntities.add(new IntSetting.Builder()
        .name("max-tnt")
        .description("Maximum TNT entities before removal.")
        .defaultValue(100)
        .min(10)
        .sliderRange(10, 1000)
        .visible(() -> entityLimit.get() && limitTnt.get())
        .build());

    private final Setting<Integer> maxSpawnsPerTick = sgEntities.add(new IntSetting.Builder()
        .name("max-entity-spawns-per-tick")
        .description("Maximum entity spawn packets per tick. Higher values let entity spam through before cleanup.")
        .defaultValue(20)
        .min(5)
        .sliderRange(5, 200)
        .visible(entityLimit::get)
        .build());

    private final Setting<Integer> maxAreaEffectCloud = sgEntities.add(new IntSetting.Builder()
        .name("max-area-effect-cloud")
        .description("Maximum area_effect_cloud entities. Crashers use these with Radius:Infinity.")
        .defaultValue(0)
        .min(1)
        .sliderRange(1, 50)
        .visible(entityLimit::get)
        .build());

    private final SettingGroup sgText = settings.createGroup("Signs and Holograms");

    private final Setting<Boolean> sanitizeSigns = sgText.add(new BoolSetting.Builder()
        .name("sanitize-signs")
        .description("Strip excessive/abusive text from signs client-side.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> maxSignTextLength = sgText.add(new IntSetting.Builder()
        .name("max-sign-text-length")
        .description("Maximum characters per sign line.")
        .defaultValue(256)
        .min(32)
        .sliderRange(32, 2000)
        .visible(sanitizeSigns::get)
        .build());

    private final Setting<Boolean> hologramProtect = sgText.add(new BoolSetting.Builder()
        .name("hologram-protection")
        .description("Locally replace only structurally malicious hologram text. Legitimate holograms are preserved.")
        .defaultValue(false)
        .build());

    private final SettingGroup sgSpam = settings.createGroup("Spam Protection");

    private final Setting<Boolean> soundLimit = sgSpam.add(new BoolSetting.Builder()
        .name("sound-limiter")
        .description("Limit incoming play sound packets per tick.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> maxSoundsPerTick = sgSpam.add(new IntSetting.Builder()
        .name("max-sounds-per-tick")
        .description("Maximum sound packets allowed per tick.")
        .defaultValue(50)
        .min(5)
        .sliderRange(5, 500)
        .visible(soundLimit::get)
        .build());

    private final Setting<Boolean> bossbarLimit = sgSpam.add(new BoolSetting.Builder()
        .name("bossbar-limiter")
        .description("Limit the number of boss bars displayed.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> maxBossBars = sgSpam.add(new IntSetting.Builder()
        .name("max-bossbars")
        .description("Maximum boss bars allowed at once.")
        .defaultValue(0)
        .min(1)
        .sliderRange(1, 20)
        .visible(bossbarLimit::get)
        .build());

    private final Setting<Boolean> titleLimit = sgSpam.add(new BoolSetting.Builder()
        .name("title-limiter")
        .description("Limit incoming title packets per second.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> maxTitlesPerSecond = sgSpam.add(new IntSetting.Builder()
        .name("max-titles-per-second")
        .description("Maximum title packets per second.")
        .defaultValue(3)
        .min(1)
        .sliderRange(1, 20)
        .visible(titleLimit::get)
        .build());

    private final SettingGroup sgOthers = settings.createGroup("Others");

    private final Setting<Boolean> chatLengthLimit = sgOthers.add(new BoolSetting.Builder()
        .name("chat-length-limit")
        .description("Prevent sending chat messages exceeding 255 characters to avoid kicks.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> invalidSlotProtect = sgOthers.add(new BoolSetting.Builder()
        .name("invalid-slot-protection")
        .description("Block slot update packets with invalid slot IDs.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> hideScoreboard = sgOthers.add(new BoolSetting.Builder()
        .name("hide-scoreboard")
        .description("Block scoreboard display packets to reduce visual clutter.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> blockLargeChat = sgOthers.add(new BoolSetting.Builder()
        .name("block-large-incoming-chat")
        .description("Block incoming chat/tellraw messages over the char limit. Scans for translate bombs before resolving text.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> maxChatsPerTick = sgOthers.add(new IntSetting.Builder()
        .name("max-incoming-chats-per-tick")
        .description("Maximum system/profileless chat messages processed per client tick.")
        .defaultValue(20)
        .min(1)
        .sliderRange(1, 500)
        .visible(blockLargeChat::get)
        .build());

    private final Setting<Integer> maxIncomingChatLength = sgOthers.add(new IntSetting.Builder()
        .name("max-incoming-chat-length")
        .description("Max characters allowed in incoming chat messages.")
        .defaultValue(10000)
        .min(100)
        .sliderRange(100, 50000)
        .visible(blockLargeChat::get)
        .build());

    private final SettingGroup sgAdv = settings.createGroup("Advanced Crash Protection");

    private final SettingGroup sgDialogs = settings.createGroup("Dialogs");

    private final Setting<Boolean> dialogGuard = sgDialogs.add(new BoolSetting.Builder()
        .name("dialog-guard")
        .description("Validate server dialogs before Minecraft constructs their screens.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> blockUnclosableDialogs = sgDialogs.add(new BoolSetting.Builder()
        .name("block-unclosable-dialogs")
        .description("Block dialogs that disable Escape and provide no visible exit action.")
        .defaultValue(true)
        .visible(dialogGuard::get)
        .build());

    private final Setting<Integer> maxDialogTitleChars = sgDialogs.add(new IntSetting.Builder()
        .name("max-dialog-title-chars")
        .description("Maximum resolved character count in a dialog title.")
        .defaultValue(512)
        .min(32)
        .sliderRange(32, 4096)
        .visible(dialogGuard::get)
        .build());

    private final Setting<Integer> maxDialogBodyElements = sgDialogs.add(new IntSetting.Builder()
        .name("max-dialog-body-elements")
        .description("Maximum body entries accepted in one dialog.")
        .defaultValue(64)
        .min(1)
        .sliderRange(1, 256)
        .visible(dialogGuard::get)
        .build());

    private final Setting<Integer> maxDialogInputs = sgDialogs.add(new IntSetting.Builder()
        .name("max-dialog-inputs")
        .description("Maximum input controls accepted in one dialog.")
        .defaultValue(32)
        .min(0)
        .sliderRange(0, 128)
        .visible(dialogGuard::get)
        .build());

    private final Setting<Integer> maxDialogActions = sgDialogs.add(new IntSetting.Builder()
        .name("max-dialog-actions")
        .description("Maximum buttons or nested dialog links accepted in one dialog.")
        .defaultValue(64)
        .min(1)
        .sliderRange(1, 256)
        .visible(dialogGuard::get)
        .build());

    private final Setting<Boolean> dialogEmergencyButton = sgDialogs.add(new BoolSetting.Builder()
        .name("dialog-emergency-button")
        .description("Add a local close button that suppresses all dialogs temporarily without firing server actions.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> dialogSuppressMinutes = sgDialogs.add(new IntSetting.Builder()
        .name("dialog-suppress-minutes")
        .description("Minutes the emergency button suppresses new dialogs. Disconnecting resets it immediately.")
        .defaultValue(15)
        .min(1)
        .sliderRange(1, 60)
        .visible(dialogEmergencyButton::get)
        .build());

    private final Setting<Boolean> guardCommandTree = sgAdv.add(new BoolSetting.Builder()
        .name("command-tree-guard")
        .description("Cancel command-tree packets that are absurdly large/deep. DISABLED by default because Paper servers with many plugins legitimately have 30k+ nodes.")
        .defaultValue(false)
        .build());

    private final Setting<Integer> maxCommandNodes = sgAdv.add(new IntSetting.Builder()
        .name("max-command-nodes")
        .description("Maximum command-tree nodes before the packet is dropped.")
        .defaultValue(50000)
        .min(5000)
        .sliderRange(5000, 200000)
        .visible(guardCommandTree::get)
        .build());

    private final Setting<Boolean> guardEntityNames = sgAdv.add(new BoolSetting.Builder()
        .name("entity-name-guard")
        .description("Sanitize translate bombs / excessive text in any entity custom name (not just armor stands).")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> guardAttributes = sgAdv.add(new BoolSetting.Builder()
        .name("attribute-guard")
        .description("Cancel entity-attribute packets carrying Infinity/NaN/extreme values.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> guardTeams = sgAdv.add(new BoolSetting.Builder()
        .name("team-scoreboard-guard")
        .description("Sanitize translate bombs in team prefix/suffix and scoreboard objective display names.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> guardMaps = sgAdv.add(new BoolSetting.Builder()
        .name("map-update-guard")
        .description("Limit map-update packets with excessive decorations or update frequency.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> maxMapIcons = sgAdv.add(new IntSetting.Builder()
        .name("max-map-icons")
        .description("Maximum map decorations (icons) per update before it is dropped.")
        .defaultValue(64)
        .min(4)
        .sliderRange(4, 512)
        .visible(guardMaps::get)
        .build());

    private final Setting<Integer> maxMapUpdatesPerTick = sgAdv.add(new IntSetting.Builder()
        .name("max-map-updates-per-tick")
        .description("Maximum map pixel-update packets per tick before excess are dropped.")
        .defaultValue(8)
        .min(1)
        .sliderRange(1, 64)
        .visible(guardMaps::get)
        .build());

    private final Setting<Boolean> guardSigns = sgAdv.add(new BoolSetting.Builder()
        .name("sign-content-guard")
        .description("Sanitize sign block-entity updates that carry translate bombs in front/back text.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> guardScreenTitles = sgAdv.add(new BoolSetting.Builder()
        .name("screen-title-guard")
        .description("Sanitize translate bombs in container-open screen titles and trade-offer names.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> guardPlayerList = sgAdv.add(new BoolSetting.Builder()
        .name("player-list-guard")
        .description("Sanitize translate bombs in tab-list display names.")
        .defaultValue(true)
        .build());

    private final SettingGroup sgCheckers = settings.createGroup("Health Checkers");

    private final Setting<Boolean> fpsChecker = sgCheckers.add(new BoolSetting.Builder()
        .name("fps-checker").description("Trigger protective actions after sustained low FPS.").defaultValue(true).build());
    private final Setting<Integer> minFps = sgCheckers.add(new IntSetting.Builder()
        .name("minimum-fps").defaultValue(10).min(1).sliderRange(1, 120).visible(fpsChecker::get).build());
    private final Setting<Boolean> tpsChecker = sgCheckers.add(new BoolSetting.Builder()
        .name("tps-checker").description("Trigger protective actions after sustained low server TPS.").defaultValue(true).build());
    private final Setting<Double> minTps = sgCheckers.add(new DoubleSetting.Builder()
        .name("minimum-tps").defaultValue(8.0).min(1.0).sliderRange(1.0, 20.0).visible(tpsChecker::get).build());
    private final Setting<Boolean> totalEntityChecker = sgCheckers.add(new BoolSetting.Builder()
        .name("entity-checker").description("Trigger protective actions when total local entities exceed the threshold.").defaultValue(true).build());
    private final Setting<Integer> entityCheckerThreshold = sgCheckers.add(new IntSetting.Builder()
        .name("entity-threshold").defaultValue(1500).min(1).sliderRange(1, 20000).visible(totalEntityChecker::get).build());
    private final Setting<Integer> checkerSustainTicks = sgCheckers.add(new IntSetting.Builder()
        .name("sustain-ticks").description("Consecutive ticks required before an action fires.").defaultValue(100).min(1).sliderRange(1, 1200).build());
    private final Setting<Integer> checkerCooldownTicks = sgCheckers.add(new IntSetting.Builder()
        .name("cooldown-ticks").description("Minimum ticks between repeated checker actions.").defaultValue(200).min(1).sliderRange(1, 2400).build());
    private final Setting<Boolean> checkerDisconnect = sgCheckers.add(new BoolSetting.Builder()
        .name("disconnect").description("Disconnect locally when a checker triggers.").defaultValue(false).build());
    private final Setting<Boolean> checkerRunCommand = sgCheckers.add(new BoolSetting.Builder()
        .name("run-command").description("Run the configured command when a checker triggers.").defaultValue(false).build());
    private final Setting<String> checkerCommand = sgCheckers.add(new StringSetting.Builder()
        .name("command").description("Command sent only when run-command is enabled.").defaultValue("help").visible(checkerRunCommand::get).build());

    private final SettingGroup sgNotifications = settings.createGroup("Notifications");
    private final Setting<Boolean> notifications = sgNotifications.add(new BoolSetting.Builder()
        .name("enabled").description("Show local alerts when protections trigger.").defaultValue(true).build());
    private final Setting<Integer> notificationCooldownTicks = sgNotifications.add(new IntSetting.Builder()
        .name("cooldown-ticks").description("Coalesce repeated alerts for this many ticks.").defaultValue(20).min(0).sliderRange(0, 200).build());

    private int soundCountThisTick;
    private int chatCountThisTick;
    private int titleCountThisSecond;
    private long lastTitleResetTime;
    private int bossBarCount;
    private int mapUpdateCountThisTick;
    private int itemEntityCount;
    private int entitySpawnsThisTick;
    private final Map<EntityType<?>, Integer> entityTypeCounts = new HashMap<>();
    private List<Entity> cachedEntities;

    private int blockedCountThisTick;
    private long lastBlockedTick;
    private int lowFpsTicks;
    private int lowTpsTicks;
    private int highEntityTicks;
    private long lastCheckerActionTick;
    private long lastNotificationTick;

    private static volatile long dialogsSuppressedUntilMs;

    public ServerProtect() {
        super(Orbiter.CATEGORY, "server-protect",
            "Comprehensive anti-abuse module. Blocks crash packets, entity spam, malicious items, and more.");
    }

    @Override
    public void onActivate() {
        soundCountThisTick = 0;
        chatCountThisTick = 0;
        titleCountThisSecond = 0;
        lastTitleResetTime = System.currentTimeMillis();
        bossBarCount = 0;
        itemEntityCount = 0;
        cachedEntities = null;
        entityTypeCounts.clear();
        blockedCountThisTick = 0;
        lastBlockedTick = -1;
        mapUpdateCountThisTick = 0;
        lowFpsTicks = 0;
        lowTpsTicks = 0;
        highEntityTicks = 0;
        lastCheckerActionTick = Long.MIN_VALUE / 2;
        lastNotificationTick = Long.MIN_VALUE / 2;
    }

    @Override
    public void onDeactivate() {
        bossBarCount = 0;
        itemEntityCount = 0;
        cachedEntities = null;
        entityTypeCounts.clear();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        dialogsSuppressedUntilMs = 0L;
    }

    public boolean shouldGuardDialogs() {
        return isActive() && dialogGuard.get();
    }

    public boolean shouldShowDialogEmergencyButton() {
        return isActive() && dialogEmergencyButton.get();
    }

    public static boolean areDialogsSuppressed() {
        return System.currentTimeMillis() < dialogsSuppressedUntilMs;
    }

    public static long dialogSuppressionRemainingMs() {
        return Math.max(0L, dialogsSuppressedUntilMs - System.currentTimeMillis());
    }

    public void suppressDialogs() {
        dialogsSuppressedUntilMs = System.currentTimeMillis() + dialogSuppressMinutes.get() * 60_000L;
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("\u00a7c[ServerProtect] Dialogs suppressed for "
                + dialogSuppressMinutes.get() + " minutes or until disconnect."));
        }
    }

    public boolean shouldBlockDialog(net.minecraft.server.dialog.Dialog dialog) {
        if (!shouldGuardDialogs() || dialog == null) return false;
        if (areDialogsSuppressed()) return true;

        net.minecraft.server.dialog.CommonDialogData common = dialog.common();
        if (common == null) return true;
        Component title = common.title();
        if (title != null && (isAbusiveText(title) || title.getString().length() > maxDialogTitleChars.get())) return true;
        if (common.body() == null || common.inputs() == null) return true;
        if (common.body().size() > maxDialogBodyElements.get() || common.inputs().size() > maxDialogInputs.get()) return true;

        int actions = countDialogActions(dialog);
        if (actions > maxDialogActions.get()) return true;
        return blockUnclosableDialogs.get() && !common.canCloseWithEscape() && !hasDialogExit(dialog);
    }

    private int countDialogActions(net.minecraft.server.dialog.Dialog dialog) {
        if (dialog instanceof net.minecraft.server.dialog.SimpleDialog simple) return simple.mainActions().size();
        if (dialog instanceof net.minecraft.server.dialog.MultiActionDialog multi) {
            return multi.actions().size() + (multi.exitAction().isPresent() ? 1 : 0);
        }
        if (dialog instanceof net.minecraft.server.dialog.DialogListDialog list) {
            return list.dialogs().size() + (list.exitAction().isPresent() ? 1 : 0);
        }
        if (dialog instanceof net.minecraft.server.dialog.ServerLinksDialog links) {
            return links.exitAction().isPresent() ? 1 : 0;
        }
        return 0;
    }

    private boolean hasDialogExit(net.minecraft.server.dialog.Dialog dialog) {
        if (dialog.onCancel().isPresent()) return true;
        if (dialog instanceof net.minecraft.server.dialog.SimpleDialog simple) return !simple.mainActions().isEmpty();
        if (dialog instanceof net.minecraft.server.dialog.MultiActionDialog multi) {
            return !multi.actions().isEmpty() || multi.exitAction().isPresent();
        }
        if (dialog instanceof net.minecraft.server.dialog.DialogListDialog list) {
            return list.dialogs().size() > 0 || list.exitAction().isPresent();
        }
        return dialog instanceof net.minecraft.server.dialog.ServerLinksDialog links && links.exitAction().isPresent();
    }

    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        if (!chatLengthLimit.get()) return;
        if (event.message.length() > 255) {
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("\u00a7c[ServerProtect] Message too long (" + event.message.length() + " chars, max 255)."));
            }
            event.setCancelled(true);
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null || mc.level == null) return;

        if (checkExplosion.get() && event.packet instanceof ClientboundExplodePacket pkt) {
            if (!isValidExplosion(pkt)) { event.cancel(); return; }
        }

        if (checkParticle.get() && event.packet instanceof ClientboundLevelParticlesPacket pkt) {
            if (!isValidParticle(pkt)) { event.cancel(); return; }
        }

        if (checkPosition.get() && event.packet instanceof ClientboundPlayerPositionPacket pkt) {
            if (!isValidPosition(pkt)) { event.cancel(); return; }
        }

        if (soundLimit.get() && event.packet instanceof ClientboundSoundPacket) {
            soundCountThisTick++;
            if (soundCountThisTick > maxSoundsPerTick.get()) { event.cancel(); return; }
        }

        if (bossbarLimit.get() && event.packet instanceof ClientboundBossEventPacket) {
            bossBarCount++;
            if (bossBarCount > maxBossBars.get()) {
                event.cancel();
                return;
            }
        }

        if (titleLimit.get()) {
            if (event.packet instanceof ClientboundSetTitleTextPacket || event.packet instanceof ClientboundSetSubtitleTextPacket) {
                long now = System.currentTimeMillis();
                if (now - lastTitleResetTime >= 1000) {
                    titleCountThisSecond = 0;
                    lastTitleResetTime = now;
                }
                titleCountThisSecond++;
                if (titleCountThisSecond > maxTitlesPerSecond.get()) { event.cancel(); return; }
            }
        }

        if (hideScoreboard.get() && event.packet instanceof net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket) {
            event.cancel();
            return;
        }

        if (blockLargeChat.get()) {
            if (event.packet instanceof ClientboundSystemChatPacket || event.packet instanceof ClientboundPlayerChatPacket) {
                chatCountThisTick++;
                if (chatCountThisTick > maxChatsPerTick.get()) {
                    notifyProtection("Chat rate limit triggered.");
                    event.cancel();
                    return;
                }
            }

            if (event.packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket pkt) {
                String raw = pkt.content().getString();
                if (raw.length() > maxIncomingChatLength.get()) {
                    event.cancel(); return;
                }
                if (raw.length() > 500 && isAbusiveText(pkt.content())) {
                    event.cancel(); return;
                }
            }
            if (event.packet instanceof net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket pkt) {
                String raw = pkt.message().getString();
                if (raw.length() > maxIncomingChatLength.get()) {
                    event.cancel(); return;
                }
                if (raw.length() > 500 && isAbusiveText(pkt.message())) {
                    event.cancel(); return;
                }
            }

            if (event.packet instanceof net.minecraft.network.protocol.game.ClientboundPlayerChatPacket pkt) {
                net.minecraft.network.chat.Component unsigned = pkt.unsignedContent();
                if (unsigned != null && isAbusiveText(unsigned)) {
                    event.cancel(); return;
                }
            }
        }

        if (invalidSlotProtect.get() && event.packet instanceof ClientboundContainerSetSlotPacket pkt) {
            int slot = pkt.getSlot();
            int syncId = pkt.getContainerId();

            if (syncId < -1 || syncId > 255 || slot < -1 || slot > 45) {
                event.cancel();
                return;
            }
        }

        if (shouldPacketItemGuard() && event.packet instanceof ClientboundContainerSetSlotPacket pkt) {
            ItemStack stack = pkt.getItem();
            if (stack != null && !stack.isEmpty() && isMaliciousItem(stack)) {

                notifyBlocked("safe local view for slot " + pkt.getSlot());
            }
        }

        if (shouldPacketItemGuard() && event.packet instanceof ClientboundContainerSetContentPacket pkt) {
            int hidden = 0;
            List<ItemStack> contents = pkt.items();
            if (contents != null) {
                for (ItemStack stack : contents) {
                    if (stack != null && !stack.isEmpty() && isMaliciousItem(stack)) hidden++;
                }
            }
            ItemStack cursor = pkt.carriedItem();
            if (cursor != null && !cursor.isEmpty() && isMaliciousItem(cursor)) hidden++;
            if (hidden > 0) notifyBlocked(hidden + " safe local item views in inventory sync");
        }

        if (event.packet instanceof ClientboundAddEntityPacket pkt) {
            EntityType<?> type = pkt.getType();
            entitySpawnsThisTick++;

            if (entitySpawnsThisTick > 100) {
                event.cancel();
                return;
            }

            if (type == EntityTypes.AREA_EFFECT_CLOUD) {
                event.cancel();
                return;
            }

            if (entityLimit.get() && entitySpawnsThisTick > maxSpawnsPerTick.get()) {
                event.cancel();
                return;
            }

            if (entityLimit.get()) {
                if (limitTnt.get() && type == EntityTypes.TNT) {
                    int current = entityTypeCounts.getOrDefault(EntityTypes.TNT, 0);
                    if (current >= maxTnt.get()) { event.cancel(); return; }
                }
            }
        }

        if (floorItemLimit.get() && event.packet instanceof ClientboundAddEntityPacket pkt) {
            if (pkt.getType() == EntityTypes.ITEM) {
                if (itemEntityCount >= maxFloorItems.get()) { event.cancel(); return; }
            }
        }

        if (guardCommandTree.get() && event.packet instanceof ClientboundCommandsPacket pkt) {

            ServerProtectCommandTreeAccessor acc = (ServerProtectCommandTreeAccessor) pkt;
            if (acc.orbiter$getEntries() != null && acc.orbiter$getEntries().size() > maxCommandNodes.get()) {
                notifyBlocked("oversized command tree (" + acc.orbiter$getEntries().size() + " nodes)");
                event.cancel();
                return;
            }
        }

        if (guardAttributes.get() && event.packet instanceof ClientboundUpdateAttributesPacket pkt) {
            if (hasExtremeAttributes(pkt.getValues())) {
                event.cancel();
                return;
            }
        }

        if (guardTeams.get() && event.packet instanceof ClientboundSetPlayerTeamPacket pkt) {
            if (pkt.getParameters().isPresent()) {
                ClientboundSetPlayerTeamPacket.Parameters team = pkt.getParameters().get();
                if (isAbusiveText(team.displayName())
                    || isAbusiveText(team.playerPrefix())
                    || isAbusiveText(team.playerSuffix())) {
                    event.cancel();
                    return;
                }
            }
        }

        if (guardTeams.get() && event.packet instanceof ClientboundSetObjectivePacket pkt) {
            if (pkt.getMethod() != ClientboundSetObjectivePacket.METHOD_REMOVE
                && isAbusiveText(pkt.getDisplayName())) {
                event.cancel();
                return;
            }
        }

        if (guardMaps.get() && event.packet instanceof ClientboundMapItemDataPacket pkt) {
            if (pkt.decorations().isPresent()) {
                int icons = pkt.decorations().get().size();
                if (icons > maxMapIcons.get()) {
                    event.cancel();
                    return;
                }
            }
            if (pkt.colorPatch().isPresent()) {
                mapUpdateCountThisTick++;
                if (mapUpdateCountThisTick > maxMapUpdatesPerTick.get()) {
                    event.cancel();
                    return;
                }
            }
        }

        if (guardSigns.get() && event.packet instanceof ClientboundBlockEntityDataPacket pkt) {
            if (pkt.getType() == net.minecraft.world.level.block.entity.BlockEntityTypes.SIGN
                || pkt.getType() == net.minecraft.world.level.block.entity.BlockEntityTypes.HANGING_SIGN) {
                CompoundTag nbt = pkt.getTag();
                if (nbt != null && (hasMaliciousSignText(nbt, "front_text") || hasMaliciousSignText(nbt, "back_text"))) {
                    event.cancel();
                    return;
                }
            }
        }

        if (guardScreenTitles.get() && event.packet instanceof ClientboundOpenScreenPacket pkt) {
            if (isAbusiveText(pkt.getTitle())) {
                event.cancel();
                return;
            }
        }

        if (guardScreenTitles.get() && event.packet instanceof ClientboundMerchantOffersPacket pkt) {
            net.minecraft.world.item.trading.MerchantOffers offers = pkt.getOffers();
            if (offers != null) {
                for (net.minecraft.world.item.trading.MerchantOffer offer : offers) {

                    if (isMaliciousItem(offer.getResult())) {
                        event.cancel();
                        return;
                    }
                    net.minecraft.world.item.ItemStack first = offer.getCostA();
                    if (first != null && !first.isEmpty() && isMaliciousItem(first)) {
                        event.cancel();
                        return;
                    }
                    net.minecraft.world.item.ItemStack second = offer.getCostB();
                    if (second != null && !second.isEmpty() && isMaliciousItem(second)) {
                        event.cancel();
                        return;
                    }
                }
            }
        }

        if (guardPlayerList.get() && event.packet instanceof ClientboundPlayerInfoUpdatePacket pkt) {
            for (ClientboundPlayerInfoUpdatePacket.Entry entry : pkt.entries()) {
                if (entry.displayName() != null && isAbusiveText(entry.displayName())) {

                    event.cancel();
                    return;
                }
            }
        }

        if (event.packet instanceof ClientboundSetEntityDataPacket pkt) {
            for (net.minecraft.network.syncher.SynchedEntityData.DataValue<?> entry : pkt.packedItems()) {
                Object val = entry.value();
                if (val instanceof Component text && isAbusiveText(text)) {
                    event.cancel();
                    return;
                }
                if (val instanceof String s && s.length() > 10000) {
                    event.cancel();
                    return;
                }

                if (val instanceof Float f && (!Float.isFinite(f) || Math.abs(f) > 1e9)) {
                    event.cancel();
                    return;
                }

                if (val instanceof Double d && (!Double.isFinite(d) || Math.abs(d) > 1e9)) {
                    event.cancel();
                    return;
                }

                if (val instanceof Integer i && (Math.abs(i) > 1e9)) {
                    event.cancel();
                    return;
                }
            }
        }

        if (event.packet instanceof net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket pkt) {
            net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect = pkt.getEffect();
            if (effect == MobEffects.MINING_FATIGUE || effect == MobEffects.DARKNESS) {
                event.cancel();
                return;
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        soundCountThisTick = 0;
        chatCountThisTick = 0;
        bossBarCount = 0;
        mapUpdateCountThisTick = 0;
        entitySpawnsThisTick = 0;

        if (blockedCountThisTick > 0 && mc.level.getGameTime() != lastBlockedTick) {
            notifyProtection("Applied " + blockedCountThisTick + " safe local item views.");
            blockedCountThisTick = 0;
        }
        lastBlockedTick = mc.level.getGameTime();

        List<Entity> allEntities = new ArrayList<>();
        for (Entity e : ((meteordevelopment.meteorclient.mixin.LevelAccessor) mc.level).meteor$getEntityLookup().getAll()) allEntities.add(e);
        cachedEntities = allEntities;

        itemEntityCount = 0;
        entityTypeCounts.clear();
        Map<EntityType<?>, List<Entity>> perType = new HashMap<>();

        for (Entity entity : allEntities) {
            if (entity instanceof Player) continue;
            if (entity instanceof ItemEntity) itemEntityCount++;

            EntityType<?> type = entity.getType();
            entityTypeCounts.merge(type, 1, Integer::sum);
            perType.computeIfAbsent(type, k -> new ArrayList<>()).add(entity);
        }

        if (entityLimit.get()) tickEntityLimiting(perType, allEntities);
        tickHealthCheckers(allEntities.size());

    }

    private void tickEntityLimiting(Map<EntityType<?>, List<Entity>> perType, List<Entity> allEntities) {
        List<Entity> invalid = new ArrayList<>();
        List<Entity> nonPlayers = new ArrayList<>();
        for (Entity entity : allEntities) {
            if (entity instanceof Player) continue;
            nonPlayers.add(entity);
            if (!isValidClientEntity(entity)) invalid.add(entity);
        }
        for (Entity entity : invalid) entity.discard();

        int totalExcess = nonPlayers.size() - invalid.size() - maxTotalEntities.get();
        if (totalExcess > 0) {
            Collections.shuffle(nonPlayers);
            for (Entity entity : nonPlayers) {
                if (totalExcess <= 0) break;
                if (invalid.contains(entity) || entity instanceof Player) continue;
                entity.discard();
                totalExcess--;
            }
            notifyProtection("Local entity limit triggered.");
        }
        if (limitTnt.get()) {
            List<Entity> tntList = perType.getOrDefault(EntityTypes.TNT, List.of());
            int excess = tntList.size() - maxTnt.get();
            if (excess > 0) {
                Collections.shuffle(tntList);
                for (int i = 0; i < excess; i++) tntList.get(i).discard();
            }
        }

        for (var entry : perType.entrySet()) {
            List<Entity> entities = entry.getValue();
            int excess = entities.size() - maxEntityTypeCount.get();
            if (excess > 0) {
                Collections.shuffle(entities);
                for (int i = 0; i < excess; i++) entities.get(i).discard();
            }
        }
    }

    private boolean isValidClientEntity(Entity entity) {
        if (entity == null) return false;
        if (!isFinite(entity.getX()) || !isFinite(entity.getY()) || !isFinite(entity.getZ())) return false;
        if (!isFinite(entity.getYRot()) || !isFinite(entity.getXRot())) return false;
        net.minecraft.world.phys.Vec3 velocity = entity.getDeltaMovement();
        if (velocity == null || !isFinite(velocity.x) || !isFinite(velocity.y) || !isFinite(velocity.z)) return false;
        net.minecraft.world.phys.AABB box = entity.getBoundingBox();
        if (box == null) return false;
        double max = maxEntityBoundingSize.get();
        return isFinite(box.getXsize()) && isFinite(box.getYsize()) && isFinite(box.getZsize())
            && box.getXsize() >= 0 && box.getYsize() >= 0 && box.getZsize() >= 0
            && box.getXsize() <= max && box.getYsize() <= max && box.getZsize() <= max;
    }

    private void tickHealthCheckers(int totalEntities) {
        lowFpsTicks = updateSustainedCounter(fpsChecker.get() && mc.getFps() < minFps.get(), lowFpsTicks);
        float tps = TickRate.INSTANCE.getTickRate();
        lowTpsTicks = updateSustainedCounter(tpsChecker.get() && tps > 0 && tps < minTps.get(), lowTpsTicks);
        highEntityTicks = updateSustainedCounter(totalEntityChecker.get() && totalEntities > entityCheckerThreshold.get(), highEntityTicks);

        String reason = null;
        if (lowFpsTicks >= checkerSustainTicks.get()) reason = "FPS remained below " + minFps.get();
        else if (lowTpsTicks >= checkerSustainTicks.get()) reason = String.format("TPS remained below %.1f", minTps.get());
        else if (highEntityTicks >= checkerSustainTicks.get()) reason = "Entity count remained above " + entityCheckerThreshold.get();
        if (reason == null) return;

        long tick = mc.level.getGameTime();
        if (tick - lastCheckerActionTick < checkerCooldownTicks.get()) return;
        lastCheckerActionTick = tick;
        notifyProtection(reason + ".");

        if (checkerRunCommand.get() && mc.player != null && mc.player.connection != null) {
            String commandValue = checkerCommand.get().trim();
            if (!commandValue.isEmpty()) {
                if (commandValue.startsWith("/")) commandValue = commandValue.substring(1);
                mc.player.connection.sendCommand(commandValue);
            }
        }
        if (checkerDisconnect.get() && mc.getConnection() != null) {
            mc.getConnection().getConnection().disconnect(Component.literal("ServerProtect: " + reason));
        }
    }

    private static int updateSustainedCounter(boolean breached, int current) {
        return breached ? Math.min(Integer.MAX_VALUE - 1, current + 1) : 0;
    }

    private void notifyProtection(String message) {
        if (!notifications.get() || mc.player == null || mc.level == null) return;
        long tick = mc.level.getGameTime();
        if (tick - lastNotificationTick < notificationCooldownTicks.get()) return;
        lastNotificationTick = tick;
        mc.player.sendSystemMessage(Component.literal("\u00a7c[ServerProtect] \u00a77" + message));
    }

    private boolean isValidExplosion(ClientboundExplodePacket pkt) {
        double max = maxWorldPos.get();
        double kb = maxKnockback.get();
        net.minecraft.world.phys.Vec3 center = pkt.center();
        if (!isFinite(center.x) || !isFinite(center.y) || !isFinite(center.z)) return false;
        if (Math.abs(center.x) > max || Math.abs(center.y) > max || Math.abs(center.z) > max) return false;
        if (pkt.playerKnockback().isPresent()) {
            net.minecraft.world.phys.Vec3 knock = pkt.playerKnockback().get();
            if (!isFinite(knock.x) || !isFinite(knock.y) || !isFinite(knock.z)) return false;
            if (Math.abs(knock.x) > kb || Math.abs(knock.y) > kb || Math.abs(knock.z) > kb) return false;
        }
        return true;
    }

    private boolean isValidParticle(ClientboundLevelParticlesPacket pkt) {
        if (pkt.getCount() < 0 || pkt.getCount() > maxParticleCount.get()) return false;
        double max = maxWorldPos.get();
        if (!isFinite(pkt.getX()) || !isFinite(pkt.getY()) || !isFinite(pkt.getZ())) return false;
        if (Math.abs(pkt.getX()) > max || Math.abs(pkt.getY()) > max || Math.abs(pkt.getZ()) > max) return false;
        double speed = maxParticleSpeed.get();
        return isFinite(pkt.getXDist()) && isFinite(pkt.getYDist()) && isFinite(pkt.getZDist()) && isFinite(pkt.getMaxSpeed())
            && Math.abs(pkt.getXDist()) <= speed && Math.abs(pkt.getYDist()) <= speed
            && Math.abs(pkt.getZDist()) <= speed && Math.abs(pkt.getMaxSpeed()) <= speed;
    }

    private boolean isValidPosition(ClientboundPlayerPositionPacket pkt) {
        double max = maxWorldPos.get();
        double rot = maxRotation.get();
        net.minecraft.world.phys.Vec3 pos = pkt.change().position();
        if (!isFinite(pos.x) || !isFinite(pos.y) || !isFinite(pos.z)) return false;
        if (Math.abs(pos.x) > max || Math.abs(pos.y) > max || Math.abs(pos.z) > max) return false;
        float yaw = pkt.change().yRot();
        float pitch = pkt.change().xRot();
        if (!isFinite(yaw) || !isFinite(pitch)) return false;
        if (Math.abs(yaw) > rot || Math.abs(pitch) > rot) return false;
        return true;
    }

    private static boolean isFinite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    private static boolean isFinite(float v) {
        return !Float.isNaN(v) && !Float.isInfinite(v);
    }

    private boolean isValidCommandTree(ClientboundCommandsPacket pkt) {

        return true;
    }

    private boolean isValidEntityAttributes(ClientboundUpdateAttributesPacket pkt) {
        List<ClientboundUpdateAttributesPacket.AttributeSnapshot> entries = pkt.getValues();
        if (entries == null) return true;
        for (ClientboundUpdateAttributesPacket.AttributeSnapshot entry : entries) {
            double base = entry.base();
            if (!isFinite(base) || Math.abs(base) > 1e9) return false;
            if (entry.modifiers() != null) {
                for (var mod : entry.modifiers()) {
                    double v = mod.amount();
                    if (!isFinite(v) || Math.abs(v) > 1e9) return false;
                }
            }
        }
        return true;
    }

    private boolean isValidTeam(ClientboundSetPlayerTeamPacket pkt) {
        var teamOpt = pkt.getParameters();
        if (teamOpt == null || teamOpt.isEmpty()) return true;
        var team = teamOpt.get();
        if (isAbusiveText(team.displayName())) return false;
        if (isAbusiveText(team.playerPrefix())) return false;
        if (isAbusiveText(team.playerSuffix())) return false;
        return true;
    }

    private boolean isValidScoreboardObjective(ClientboundSetObjectivePacket pkt) {
        if (pkt.getMethod() == ClientboundSetObjectivePacket.METHOD_REMOVE) return true;
        return !isAbusiveText(pkt.getDisplayName());
    }

    private boolean isValidMapUpdate(ClientboundMapItemDataPacket pkt) {
        var decos = pkt.decorations();
        if (decos != null && decos.isPresent()) {
            List<?> list = decos.get();
            if (list != null && list.size() > 256) return false;
        }
        return true;
    }

    private boolean isValidBlockEntityUpdate(ClientboundBlockEntityDataPacket pkt) {
        CompoundTag nbt = pkt.getTag();
        if (nbt == null) return true;

        for (String side : new String[]{"front_text", "back_text"}) {
            Tag sideEl = nbt.get(side);
            if (sideEl instanceof CompoundTag sideCompound) {
                Tag msgs = sideCompound.get("messages");
                if (msgs instanceof ListTag list) {
                    for (int i = 0; i < list.size(); i++) {
                        var e = list.get(i);
                        if (e instanceof net.minecraft.nbt.StringTag ns) {
                            String s = ns.asString().orElse("");
                            if (countFormatArgs(s) > 0 && countFormatArgs(s) * 4 > 32) return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private boolean isValidOpenScreen(ClientboundOpenScreenPacket pkt) {
        return !isAbusiveText(pkt.getTitle());
    }

    private static boolean isValidTradeOffers(ClientboundMerchantOffersPacket pkt) {
        net.minecraft.world.item.trading.MerchantOffers offers = pkt.getOffers();
        if (offers == null) return true;
        for (net.minecraft.world.item.trading.MerchantOffer offer : offers) {
            if (isMaliciousItem(offer.getResult())) return false;
            if (isMaliciousItem(offer.getResult())) return false;
            net.minecraft.world.item.ItemStack first = offer.getCostA();
            if (first != null && !first.isEmpty() && isMaliciousItem(first)) return false;
            net.minecraft.world.item.ItemStack second = offer.getCostB();
            if (second != null && !second.isEmpty() && isMaliciousItem(second)) return false;
        }
        return true;
    }

    public static boolean hasExcessiveCjk(String s) {
        int cjkCount = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) cjkCount++;
        }
        return cjkCount > 50;
    }

    public static String sanitizeText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() > maxLength) text = text.substring(0, maxLength);
        return text.replace("\u00A7k", "");
    }

    public boolean shouldSanitizeItems() { return isActive() && sanitizeItems.get(); }
    public int getMaxLoreLength() { return maxLoreLength.get(); }
    public boolean shouldRemoveObfuscated() { return isActive() && removeObfuscated.get(); }
    public boolean shouldRemoveChinese() { return isActive() && removeChinese.get(); }
    public boolean shouldSanitizeSigns() { return isActive() && sanitizeSigns.get(); }
    public int getMaxSignTextLength() { return maxSignTextLength.get(); }
    public boolean shouldLimitChat() { return isActive() && chatLengthLimit.get(); }
    public boolean shouldSanitizeNames() { return isActive() && sanitizeItems.get() && sanitizeNames.get(); }
    public int getMaxNameLength() { return maxNameLength.get(); }
    public boolean shouldStripEntityData() { return isActive() && sanitizeItems.get() && stripEntityData.get(); }
    public boolean shouldValidateEntityData() { return isActive() && validateEntityData.get(); }
    public boolean shouldBlockMaliciousEntityData() { return isActive() && blockMaliciousEntityData.get(); }
    public boolean shouldSanitizeCopyForTooltip() { return isActive() && sanitizeCopyForTooltip.get(); }
    public boolean isLegacyEntityRemovalEnabled() { return stripEntityData.get(); }
    public boolean shouldPacketItemGuard() { return isActive() && packetItemGuard.get(); }
    public int getMaxTextDepth() { return maxTextDepth.get(); }
    public int getMaxTranslateExpansion() { return maxTranslateExpansion.get(); }

    private final SettingGroup sgCrashFixer = settings.createGroup("Crash Fixer Integration");
    private final Setting<Boolean> particleThrottle = sgCrashFixer.add(new BoolSetting.Builder()
        .name("particle-throttle").description("Throttle particle spawns per tick (Crash Fixer).").defaultValue(true).build());
    private final Setting<Integer> maxParticlesPerTick = sgCrashFixer.add(new IntSetting.Builder()
        .name("max-particles-per-tick").description("Max particles spawned per tick.").defaultValue(500).min(50).sliderRange(50, 5000).build());
    private final Setting<Boolean> particlePacketClamp = sgCrashFixer.add(new BoolSetting.Builder()
        .name("particle-packet-clamp").description("Clamp particle packet count to prevent huge spawns.").defaultValue(true).build());
    private final Setting<Integer> maxParticlesPerPacket = sgCrashFixer.add(new IntSetting.Builder()
        .name("max-particles-per-packet").description("Max particles per single packet.").defaultValue(128).min(10).sliderRange(10, 1000).build());

    private final Setting<Boolean> soundThrottle = sgCrashFixer.add(new BoolSetting.Builder()
        .name("sound-throttle").description("4-layer sound spam throttle (per-tick, per-window, per-ID, per-category).").defaultValue(true).build());
    private final Setting<Integer> soundMaxPerTick = sgCrashFixer.add(new IntSetting.Builder()
        .name("sound-max-per-tick").defaultValue(256).min(10).sliderRange(10, 1000).build());
    private final Setting<Integer> soundMaxBlocksPerTick = sgCrashFixer.add(new IntSetting.Builder()
        .name("sound-max-blocks-per-tick").defaultValue(24).min(1).sliderRange(1, 200).build());
    private final Setting<Integer> soundWindowMs = sgCrashFixer.add(new IntSetting.Builder()
        .name("sound-window-ms").defaultValue(20).min(1).sliderRange(1, 200).build());
    private final Setting<Integer> soundMaxPerWindow = sgCrashFixer.add(new IntSetting.Builder()
        .name("sound-max-per-window").defaultValue(24).min(1).sliderRange(1, 200).build());
    private final Setting<Integer> soundMaxSamePerWindow = sgCrashFixer.add(new IntSetting.Builder()
        .name("sound-max-same-per-window").defaultValue(6).min(1).sliderRange(1, 100).build());
    private final Setting<Integer> soundCleanupThreshold = sgCrashFixer.add(new IntSetting.Builder()
        .name("sound-cleanup-threshold").defaultValue(4096).min(128).sliderRange(128, 10000).build());

    private final Setting<Boolean> translationRecursionFix = sgCrashFixer.add(new BoolSetting.Builder()
        .name("translation-recursion-fix").description("Prevent StackOverflow from recursive translatable text.").defaultValue(true).build());
    private final Setting<Integer> translationMaxRecursionDepth = sgCrashFixer.add(new IntSetting.Builder()
        .name("translation-max-recursion-depth").defaultValue(2).min(1).sliderRange(1, 10).build());
    private final Setting<Boolean> translationPayloadGuard = sgCrashFixer.add(new BoolSetting.Builder()
        .name("translation-payload-guard").description("Pre-expansion bomb check on translatable text.").defaultValue(true).build());
    private final Setting<Integer> translationMaxTemplateChars = sgCrashFixer.add(new IntSetting.Builder()
        .name("translation-max-template-chars").defaultValue(1024).min(64).sliderRange(64, 10000).build());
    private final Setting<Integer> translationMaxPlaceholders = sgCrashFixer.add(new IntSetting.Builder()
        .name("translation-max-placeholders").defaultValue(128).min(8).sliderRange(8, 1024).build());
    private final Setting<Integer> translationMaxArgs = sgCrashFixer.add(new IntSetting.Builder()
        .name("translation-max-args").defaultValue(32).min(4).sliderRange(4, 256).build());
    private final Setting<Integer> translationMaxArgChars = sgCrashFixer.add(new IntSetting.Builder()
        .name("translation-max-arg-chars").defaultValue(512).min(32).sliderRange(32, 5000).build());
    private final Setting<Integer> translationMaxExpandedChars = sgCrashFixer.add(new IntSetting.Builder()
        .name("translation-max-expanded-chars").defaultValue(4096).min(256).sliderRange(256, 50000).build());

    private final Setting<Boolean> entityNameSanitizer = sgCrashFixer.add(new BoolSetting.Builder()
        .name("entity-name-sanitizer").description("Advanced local fallback for structurally malicious entity names. Disabled to preserve plugin holograms.").defaultValue(false).build());
    private final Setting<Integer> nameMaxChars = sgCrashFixer.add(new IntSetting.Builder()
        .name("name-max-chars").defaultValue(96).min(16).sliderRange(16, 1000).build());
    private final Setting<Integer> nameMaxNodes = sgCrashFixer.add(new IntSetting.Builder()
        .name("name-max-nodes").defaultValue(24).min(4).sliderRange(4, 200).build());
    private final Setting<Integer> nameMaxDepth = sgCrashFixer.add(new IntSetting.Builder()
        .name("name-max-depth").defaultValue(6).min(1).sliderRange(1, 30).build());
    private final Setting<Integer> nameMaxStyleScore = sgCrashFixer.add(new IntSetting.Builder()
        .name("name-max-style-score").defaultValue(12).min(1).sliderRange(1, 100).build());
    private final Setting<Integer> nameMaxObfuscatedChars = sgCrashFixer.add(new IntSetting.Builder()
        .name("name-max-obfuscated-chars").defaultValue(24).min(0).sliderRange(0, 500).build());
    private final Setting<Integer> nameMaxComplexNodes = sgCrashFixer.add(new IntSetting.Builder()
        .name("name-max-complex-nodes").defaultValue(8).min(1).sliderRange(1, 100).build());

    private final Setting<Boolean> textDisplaySanitizer = sgCrashFixer.add(new BoolSetting.Builder()
        .name("text-display-sanitizer").description("Advanced local fallback for structurally malicious text displays. Disabled to preserve plugin holograms.").defaultValue(false).build());
    private final Setting<Integer> textDisplayMaxChars = sgCrashFixer.add(new IntSetting.Builder()
        .name("textdisplay-max-chars").defaultValue(2048).min(64).sliderRange(64, 20000).build());
    private final Setting<Integer> textDisplayMaxNodes = sgCrashFixer.add(new IntSetting.Builder()
        .name("textdisplay-max-nodes").defaultValue(384).min(8).sliderRange(8, 2000).build());
    private final Setting<Integer> textDisplayMaxDepth = sgCrashFixer.add(new IntSetting.Builder()
        .name("textdisplay-max-depth").defaultValue(12).min(1).sliderRange(1, 50).build());
    private final Setting<Integer> textDisplayMaxStyleScore = sgCrashFixer.add(new IntSetting.Builder()
        .name("textdisplay-max-style-score").defaultValue(160).min(1).sliderRange(1, 1000).build());
    private final Setting<Integer> textDisplayMaxObfuscatedChars = sgCrashFixer.add(new IntSetting.Builder()
        .name("textdisplay-max-obfuscated-chars").defaultValue(64).min(0).sliderRange(0, 1000).build());
    private final Setting<Integer> textDisplayMaxComplexNodes = sgCrashFixer.add(new IntSetting.Builder()
        .name("textdisplay-max-complex-nodes").defaultValue(128).min(1).sliderRange(1, 500).build());
    private final Setting<Integer> textDisplayMaxLineWidth = sgCrashFixer.add(new IntSetting.Builder()
        .name("textdisplay-max-line-width").defaultValue(256).min(32).sliderRange(32, 1000).build());

    private final Setting<Boolean> elderGuardianParticleFix = sgCrashFixer.add(new BoolSetting.Builder()
        .name("elder-guardian-particle-fix").description("Replace ELDER_GUARDIAN particle in area clouds with ELECTRIC_SPARK.").defaultValue(true).build());

    public boolean shouldThrottleParticles() { return isActive() && particleThrottle.get(); }
    public int getMaxParticlesPerTick() { return maxParticlesPerTick.get(); }
    public boolean shouldClampParticlePackets() { return isActive() && particlePacketClamp.get(); }
    public int getMaxParticlesPerPacket() { return maxParticlesPerPacket.get(); }
    public boolean shouldThrottleSounds() { return isActive() && soundThrottle.get(); }
    public int getMaxSoundsPerTick() { return soundMaxPerTick.get(); }
    public int getMaxBlockSoundsPerTick() { return soundMaxBlocksPerTick.get(); }
    public int getSoundWindowMs() { return soundWindowMs.get(); }
    public int getMaxPlaysPerWindow() { return soundMaxPerWindow.get(); }
    public int getMaxSameSoundPerWindow() { return soundMaxSamePerWindow.get(); }
    public int getSoundCleanupThreshold() { return soundCleanupThreshold.get(); }
    public boolean shouldRecursionGuard() { return isActive() && translationRecursionFix.get(); }
    public int getTranslationMaxRecursionDepth() { return translationMaxRecursionDepth.get(); }
    public boolean shouldPayloadGuard() { return isActive() && translationPayloadGuard.get(); }
    public int getTranslationMaxTemplateChars() { return translationMaxTemplateChars.get(); }
    public int getTranslationMaxPlaceholders() { return translationMaxPlaceholders.get(); }
    public int getTranslationMaxArgs() { return translationMaxArgs.get(); }
    public int getTranslationMaxArgChars() { return translationMaxArgChars.get(); }
    public int getTranslationMaxExpandedChars() { return translationMaxExpandedChars.get(); }
    public boolean shouldSanitizeEntityNames() { return isActive() && entityNameSanitizer.get(); }
    public int getNameMaxChars() { return nameMaxChars.get(); }
    public int getNameMaxNodes() { return nameMaxNodes.get(); }
    public int getNameMaxDepth() { return nameMaxDepth.get(); }
    public int getNameMaxStyleScore() { return nameMaxStyleScore.get(); }
    public int getNameMaxObfuscatedChars() { return nameMaxObfuscatedChars.get(); }
    public int getNameMaxComplexNodes() { return nameMaxComplexNodes.get(); }
    public boolean shouldSanitizeTextDisplays() { return isActive() && textDisplaySanitizer.get(); }
    public int getTextDisplayMaxChars() { return textDisplayMaxChars.get(); }
    public int getTextDisplayMaxNodes() { return textDisplayMaxNodes.get(); }
    public int getTextDisplayMaxDepth() { return textDisplayMaxDepth.get(); }
    public int getTextDisplayMaxStyleScore() { return textDisplayMaxStyleScore.get(); }
    public int getTextDisplayMaxObfuscatedChars() { return textDisplayMaxObfuscatedChars.get(); }
    public int getTextDisplayMaxComplexNodes() { return textDisplayMaxComplexNodes.get(); }
    public int getTextDisplayMaxLineWidth() { return textDisplayMaxLineWidth.get(); }
    public boolean shouldFixElderGuardianParticle() { return isActive() && elderGuardianParticleFix.get(); }
    public void warn(String msg) { warning(msg); }

    private void notify(String msg) {
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(msg));
    }

    private void notifyBlocked(String detail) {
        if (mc.player == null) return;
        long tick = mc.level == null ? 0L : mc.level.getGameTime();
        if (tick != lastBlockedTick) {
            if (blockedCountThisTick > 0) {
                notifyProtection("Applied " + blockedCountThisTick + " safe local item views.");
            }
            blockedCountThisTick = 0;
            lastBlockedTick = tick;
        }
        blockedCountThisTick++;
        if (blockedCountThisTick == 1 && detail != null) {
            notifyProtection("Unsafe item hidden locally (" + detail + "); source data unchanged.");
        }
    }

    public static boolean isMaliciousItem(ItemStack stack) {
        return isMaliciousItem(stack, 0, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()), new int[]{0});
    }

    private static boolean isMaliciousItem(ItemStack stack, int depth, Set<ItemStack> visited, int[] nodes) {
        if (stack == null || stack.isEmpty()) return false;
        if (depth > 4 || ++nodes[0] > 512 || !visited.add(stack)) return true;

        Component name = stack.get(DataComponents.CUSTOM_NAME);
        if (name != null && isAbusiveText(name)) return true;

        Component itemName = stack.get(DataComponents.ITEM_NAME);
        if (itemName != null && isAbusiveText(itemName)) return true;

        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null && lore.lines() != null) {
            for (Component line : lore.lines()) {
                if (isAbusiveText(line)) return true;
            }
        }

        if (hasMaliciousNbt(stack.get(DataComponents.ENTITY_DATA))) return true;
        if (hasMaliciousNbt(stack.get(DataComponents.BLOCK_ENTITY_DATA))) return true;

        ItemAttributeModifiers attrComp = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (attrComp != null && hasExtremeAttributeModifiers(attrComp)) return true;

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && !customData.isEmpty() && hasMaliciousNbt(customData.copyTag())) return true;
        CustomData bucketData = stack.get(DataComponents.BUCKET_ENTITY_DATA);
        if (bucketData != null && !bucketData.isEmpty() && hasMaliciousNbt(bucketData.copyTag())) return true;

        WrittenBookContent book = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (book != null && book.pages() != null) {
            for (var page : book.pages()) {
                if (isAbusiveText(page.raw())) return true;
            }
        }
        net.minecraft.world.item.component.WritableBookContent writable = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
        if (writable != null && writable.pages() != null) {
            for (var page : writable.pages()) {

                String s = page.raw();
                if (s != null && (countFormatArgs(s) > 0 || s.length() > 800)) return true;
            }
        }

        if (depth < 4) {
            BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
            if (bundle != null && !bundle.isEmpty()) {
                for (ItemStack inner : bundle.itemCopyStream().toList()) {
                    if (isMaliciousItem(inner, depth + 1, visited, nodes)) return true;
                }
            }
            ItemContainerContents container = stack.get(DataComponents.CONTAINER);
            if (container != null) {
                for (ItemStack inner : container.nonEmptyItemCopyStream().toList()) {
                    if (isMaliciousItem(inner, depth + 1, visited, nodes)) return true;
                }
            }
            ChargedProjectiles projectiles = stack.get(DataComponents.CHARGED_PROJECTILES);
            if (projectiles != null && !projectiles.isEmpty()) {
                for (ItemStack inner : projectiles.itemCopies()) {
                    if (isMaliciousItem(inner, depth + 1, visited, nodes)) return true;
                }
            }
        }
        return false;
    }

    public static boolean isMaliciousItemNamesOnly(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        if (name != null && isAbusiveText(name)) return true;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null && lore.lines() != null) {
            for (Component line : lore.lines()) if (isAbusiveText(line)) return true;
        }
        return false;
    }

    public static boolean isMaliciousEntityDataOnly(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (hasMaliciousNbt(stack.get(DataComponents.ENTITY_DATA))) return true;
        if (hasMaliciousNbt(stack.get(DataComponents.BLOCK_ENTITY_DATA))) return true;
        return false;
    }

    public static boolean isAbusiveText(Component text) {
        return isAbusiveText(text, 0, new int[]{0});
    }

    private static boolean isAbusiveText(Component text, int depth, int[] expansion) {
        if (text == null) return false;
        ServerProtect mod = ServerProtect.get();
        int maxDepth = (mod != null) ? mod.getMaxTextDepth() : 32;
        int maxExp = (mod != null) ? mod.getMaxTranslateExpansion() : 32;
        if (depth > maxDepth) return true;

        if (text.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
            int percentArgs = countFormatArgs(tc.getKey());
            Object[] args = tc.getArgs();
            if (percentArgs == 0 && args != null && args.length > 0) {

            }
            if (percentArgs > 0) {
                expansion[0] += percentArgs * (depth + 1);
                if (expansion[0] > maxExp) return true;
            }
            if (args != null) {
                for (Object arg : args) {
                    if (arg instanceof Component argText) {
                        if (isAbusiveText(argText, depth + 1, expansion)) return true;
                    } else if (arg instanceof String s) {
                        int sub = countFormatArgs(s);
                        if (sub > 0) {
                            expansion[0] += sub * (depth + 1);
                            if (expansion[0] > maxExp) return true;
                        }
                    }
                }
            }
        }

        String raw = text.getString();
        if (raw.contains("\u00A7k") && raw.length() > 64) return true;

        net.minecraft.network.chat.Style style = text.getStyle();
        if (style != null) {
            net.minecraft.network.chat.HoverEvent hover = style.getHoverEvent();
            if (hover != null && isAbusiveHover(hover, depth, expansion)) return true;
        }

        if (text.getSiblings() != null) {
            for (Component sib : text.getSiblings()) {
                if (isAbusiveText(sib, depth + 1, expansion)) return true;
            }
        }
        return false;
    }

    private static boolean isAbusiveHover(net.minecraft.network.chat.HoverEvent hover, int depth, int[] expansion) {
        if (hover instanceof net.minecraft.network.chat.HoverEvent.ShowText st) {
            if (isAbusiveText(st.value(), depth + 1, expansion)) return true;
        } else if (hover instanceof net.minecraft.network.chat.HoverEvent.ShowItem si) {
            if (isMaliciousItem(si.item().create(), depth + 1,
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()), new int[]{0})) return true;
        }

        if (hover instanceof net.minecraft.network.chat.HoverEvent.ShowEntity se) {
            net.minecraft.network.chat.HoverEvent.EntityTooltipInfo ec = se.entity();
            if (ec != null && ec.name != null && ec.name.isPresent()
                && isAbusiveText(ec.name.get(), depth + 1, expansion)) return true;
        }
        return false;
    }

    private static int countFormatArgs(String key) {
        if (key == null || key.isEmpty()) return 0;
        int n = 0;
        int len = key.length();
        for (int i = 0; i < len; i++) {
            if (key.charAt(i) != '%') continue;

            if (i == len - 1) { n++; break; }
            int j = i + 1;

            if (j < len && Character.isDigit(key.charAt(j))) {
                while (j < len && Character.isDigit(key.charAt(j))) j++;
                if (j < len && key.charAt(j) == '$') j++;
            }

            if (j < len) {
                char c = key.charAt(j);
                if (c == '%' || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
                    n++;
                }
            } else {

                n++;
            }
            if (n >= 64) return 64;
            i = j;
        }
        return n;
    }

    public static boolean hasMaliciousNbt(CompoundTag nbt) {
        if (nbt == null) return false;
        return scanNbt(nbt, 0, new int[]{0});
    }

    public static boolean hasMaliciousNbt(TypedEntityData<?> data) {
        if (data == null) return false;
        return scanNbt(data.copyTagWithoutId(), 0, new int[]{0});
    }

    private static boolean hasExtremeAttributeModifiers(ItemAttributeModifiers comp) {
        for (var entry : comp.modifiers()) {
            double v = entry.modifier().amount();
            if (Double.isInfinite(v) || Double.isNaN(v) || Math.abs(v) > 1e9) return true;
        }
        return false;
    }

    private static boolean scanNbt(Tag el, int depth, int[] expansion) {
        if (depth > 16) return true;
        if (el instanceof CompoundTag c) {
            for (String key : c.keySet()) {
                Tag child = c.get(key);
                if (child == null) continue;
                if (key.equals("id") && child instanceof net.minecraft.nbt.StringTag ns) {
                    String v = ns.asString().orElse("");
                    if (v.equals("minecraft:ender_dragon")) {

                        expansion[0] += 64;
                        if (expansion[0] > 256) return true;
                    }
                }
                if (child instanceof NumericTag num) {
                    double d = num.doubleValue();
                    if (Double.isInfinite(d) || Double.isNaN(d) || Math.abs(d) > 1e9) return true;
                }
                if (scanNbt(child, depth + 1, expansion)) return true;
            }
        } else if (el instanceof ListTag list) {
            int n = list.size();
            if (n > 256) return true;
            for (int i = 0; i < n; i++) {
                if (scanNbt(list.get(i), depth + 1, expansion)) return true;
            }
        } else if (el instanceof net.minecraft.nbt.StringTag ns) {
            String s = ns.asString().orElse("");
            if (s != null) {

                if (s.length() > 10000) return true;
                int pc = countFormatArgs(s);
                if (pc > 0) {
                    expansion[0] += pc * (depth + 1);
                    if (expansion[0] > 256) return true;
                }
                if (s.contains("\u00a7k") && s.length() > 64) return true;
            }
        }
        return false;
    }

    public static List<Component> createSafeItemTooltip(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !isMaliciousItem(stack)) return List.of();
        return List.of(
            Component.literal("\u00a7c[ServerProtect] Unsafe item display blocked"),
            Component.literal("\u00a77The server-owned item was not modified.")
        );
    }

    public static Component createSafeItemName(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !isMaliciousItem(stack)) return null;
        return Component.literal("\u00a7c[Unsafe item hidden]");
    }

    private boolean hasExtremeAttributes(List<ClientboundUpdateAttributesPacket.AttributeSnapshot> entries) {
        if (entries == null) return false;
        for (ClientboundUpdateAttributesPacket.AttributeSnapshot entry : entries) {
            double base = entry.base();
            if (!isFinite(base) || Math.abs(base) > 1e9) return true;
            for (net.minecraft.world.entity.ai.attributes.AttributeModifier mod : entry.modifiers()) {
                double v = mod.amount();
                if (!isFinite(v) || Math.abs(v) > 1e9) return true;
            }
        }
        return false;
    }

    private static boolean hasMaliciousSignText(CompoundTag root, String key) {
        if (root == null || key == null) return false;
        Tag section = root.get(key);
        if (!(section instanceof CompoundTag sectionC)) return false;
        Tag messages = sectionC.get("messages");
        if (!(messages instanceof ListTag list)) return false;
        int[] expansion = {0};
        for (int i = 0; i < list.size() && i < 64; i++) {
            Tag el = list.get(i);
            if (el instanceof net.minecraft.nbt.StringTag ns) {
                String s = ns.asString().orElse("");
                if (countFormatArgs(s) > 0) {
                    expansion[0] += countFormatArgs(s);
                    if (expansion[0] > 32) return true;
                }
                if (s.length() > 800) return true;
            }
        }
        return false;
    }
}
