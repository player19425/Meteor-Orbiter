
package orbiter.modules;

import orbiter.Orbiter;
import orbiter.modules.NoFriendHit;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.ServerboundMovePlayerPacketAccessor;
import meteordevelopment.meteorclient.mixininterface.IServerboundMovePlayerPacket;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.Flight;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.world.entity.Relative;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;

public class ForceInvisibility
extends Module {
    public enum AnchorMode {
        Fixed,
        Relative
    }

    public enum TravelMode {
        Smooth,
        VClip
    }

    public enum BreakMode {
        Ignore,
        StayDown
    }

    private enum HoldMode {
        None,
        Mining,
        Container,
        Collecting,
        PostAction
    }

    private enum PendingOutcome {
        Return,
        HoldMining,
        HoldContainer
    }

    private static final class QueuedAction {
        private final Packet<?> packet;
        private final Vec3 target;
        private final PendingOutcome outcome;
        private final boolean teleportXZ;
        private final boolean entityInteraction;
        private final int postActionHoldTicks;
        private final int createdAtAge;

        private QueuedAction(Packet<?> packet, Vec3 target, PendingOutcome outcome, boolean teleportXZ, boolean entityInteraction, int postActionHoldTicks, int createdAtAge) {
            this.packet = packet;
            this.target = target;
            this.outcome = outcome;
            this.teleportXZ = teleportXZ;
            this.entityInteraction = entityInteraction;
            this.postActionHoldTicks = Math.max(0, postActionHoldTicks);
            this.createdAtAge = Math.max(0, createdAtAge);
        }
    }
    private static final int INTERNAL_PACKET_TAG = 1337;
    private static final int MAX_ACTION_QUEUE = 8;
    private static final int ACTION_TIMEOUT_TICKS = 60;
    private static final int ACTION_FORCE_EXECUTE_TICKS = 8;
    private static final int RESPAWN_GRACE_TICKS = 40;
    private static final int MINING_HOLD_FAILSAFE_TICKS = 50;
    private static final int DAMAGE_VCLIP_SAFETY_TICKS = 12;
    private static final int EXTRA_NO_FALL_TICKS = 8;
    private static final int VCLIP_PENDING_TICKS = 3;
    private static final int PACKET_BUDGET_WINDOW_MS = 5000;
    private static final int PACKET_BUDGET_LIMIT = 300;
    private static final int MAX_BUFFERED_PACKETS = 256;
    private static final int MAX_BUFFER_FLUSH_PER_TICK = 12;
    private static final int MIN_PACKET_RESERVE = 8;
    private static final int CORRECTION_WINDOW_TICKS = 20;
    private static final int CORRECTION_STRIKE_THRESHOLD = 4;
    private static final int CORRECTION_FREEZE_BASE_TICKS = 8;
    private static final int CORRECTION_FREEZE_MAX_TICKS = 40;
    private static final double ESCAPE_BURST_BLOCKS = 200.0;
    private static final int ESCAPE_MAX_BURST_PACKETS = 20;
    private static final int ESCAPE_BURST_MAX_RETRIES = 16;
    private static final int ESCAPE_BURST_RETRY_TICKS = 2;
    private static final double HARD_DESYNC_RESET_Y = 192.0;
    private static final double POSITION_EPSILON = 0.05;
    private static final double HORIZONTAL_EPSILON_SQ = 9.0E-4;
    private static final int SEND_STALL_THRESHOLD = 18;
    private static final int SEND_STALL_RECOVERY_COOLDOWN = 6;
    private static final double HARD_MAX_PACKET_Y_STEP = 8.0;
    private static final double HARD_MAX_PACKET_XZ_STEP = 3.0;
    private final SettingGroup sgGeneral;
    private final SettingGroup sgActions;
    private final SettingGroup sgEscape;
    private final SettingGroup sgRender;
    private final Setting<AnchorMode> anchorMode;
    private final Setting<Integer> anchorY;
    private final Setting<Double> relativeAnchorOffset;
    private final Setting<Double> maxAnchorDelta;
    private final Setting<TravelMode> movementMode;
    private final Setting<Double> smoothSpeed;
    private final Setting<Integer> smoothCooldownTicks;
    private final Setting<Double> maxPacketYStep;
    private final Setting<Double> maxPacketXZStep;
    private final Setting<Boolean> antiKickPulse;
    private final Setting<Integer> antiKickDelayTicks;
    private final Setting<Double> antiKickDownStep;
    private final Setting<Integer> antiKickOffTicks;
    private final Setting<Double> vclipStep;
    private final Setting<Double> vclipMinStep;
    private final Setting<Integer> vclipBurstPackets;
    private final Setting<Integer> vclipCooldownTicks;
    private final Setting<Integer> vclipFailureBackoffTicks;
    private final Setting<Integer> noFallAssistInterval;
    private final Setting<Double> actionReachThreshold;
    private final Setting<Integer> actionSyncPackets;
    private final Setting<Boolean> actionPostSync;
    private final Setting<Boolean> confirmPlaceBeforeReturn;
    private final Setting<Integer> placeConfirmTicks;
    private final Setting<Boolean> confirmHitBeforeReturn;
    private final Setting<Integer> hitConfirmTicks;
    private final Setting<Boolean> avoidHitFov;
    private final Setting<Double> hitBehindOffset;
    private final Setting<Double> hitSideOffset;
    private final Setting<Boolean> forceHitTeleportXZ;
    private final Setting<Boolean> fastRiseAfterHit;
    private final Setting<Double> hitRiseStep;
    private final Setting<Integer> hitRiseBurstPackets;
    private final Setting<Boolean> aggressiveNoFall;
    private final Setting<Double> noFallDesyncThreshold;
    private final Setting<Boolean> paperOptimized;
    private final Setting<Double> paperMaxPacketStep;
    private final Setting<Integer> paperVclipConfirmPackets;
    private final Setting<Integer> paperFailureBackoffTicks;
    private final Setting<Boolean> teleportInteractions;
    private final Setting<BreakMode> breakMode;
    private final Setting<Boolean> creativeFastBlockActions;
    private final Setting<Integer> creativeDownResyncPackets;
    private final Setting<Integer> creativeUpResyncPackets;
    private final Setting<Boolean> creativeClearActionQueue;
    private final Setting<Integer> miningNoInputReleaseTicksSetting;
    private final Setting<Integer> miningPacketSilenceTicks;
    private final Setting<Boolean> allowPlace;
    private final Setting<Boolean> allowStorage;
    private final Setting<Boolean> allowEntityHit;
    private final Setting<Boolean> collectItems;
    private final Setting<Boolean> collectUntilClear;
    private final Setting<Integer> collectBurstTicks;
    private final Setting<Double> collectRange;
    private final Setting<Integer> containerGraceTicks;
    private final Setting<Integer> escapeSkyY;
    private final Setting<Integer> escapeBedrockOffset;
    private final Setting<Boolean> renderServerPos;
    private final Setting<SettingColor> serverBoxColor;
    private final Setting<SettingColor> serverLineColor;
    private final Setting<Boolean> renderLinkLine;
    private final Setting<Boolean> renderRealHitbox;
    private final Setting<SettingColor> realBoxColor;
    private final Setting<SettingColor> realLineColor;
    private boolean sendingPackets;
    private double serverX;
    private double serverZ;
    private double currentServerY;
    private HoldMode holdMode;
    private boolean miningHoldActive;
    private int miningNoInputReleaseTicks;
    private int lastMiningPacketAge;
    private int lastCreativeInstantActionAge;
    private int collectBurstTicksLeft;
    private int containerGraceTicksLeft;
    private boolean containerScreenSeen;
    private int postActionHoldTicksLeft;
    private boolean escapeOverrideActive;
    private double escapeOverrideY;
    private boolean escapeBurstPending;
    private int escapeBurstRetriesLeft;
    private int escapeBurstRetryTicks;
    private final Deque<QueuedAction> actionQueue;
    private QueuedAction activeAction;
    private int activeActionTicks;
    private int movementCooldownTicks;
    private int antiKickTicksLeft;
    private int antiKickOffTicksLeft;
    private int noFallAssistCooldownTicks;
    private int extraNoFallTicks;
    private int correctionWindowTicks;
    private int correctionStrikes;
    private int correctionFreezeTicks;
    private int consecutiveSendFailures;
    private final Deque<Long> sentPacketTimesMs;
    private final Deque<Packet<?>> bufferedPackets;
    private boolean vclipAttemptPending;
    private double pendingVClipStartY;
    private double pendingVClipTargetY;
    private int pendingVClipTicks;
    private int vclipFailureCount;
    private double adaptiveVClipStep;
    private int vclipDamageSafetyTicks;
    private int respawnGraceTicks;
    private int miningHoldFailsafeTicks;
    private boolean sawDeath;
    private int lastPlayerEntityId;
    private int lastPlayerAge;
    private Method packetGetEntityMethod;
    private boolean packetGetEntityLookupFailed;
    private Field packetEntityIdField;
    private boolean packetEntityIdLookupFailed;

    public ForceInvisibility() {
        super(Orbiter.CATEGORY, "force-invisibility", "Spoofs server Y and only drops to real Y when needed.");
        this.sgGeneral = this.settings.getDefaultGroup();
        this.sgActions = this.settings.createGroup("Actions");
        this.sgEscape = this.settings.createGroup("Escape");
        this.sgRender = this.settings.createGroup("Render");
        this.anchorMode = this.sgGeneral.add((((new EnumSetting.Builder<AnchorMode>().name("anchor-mode")).description("Fixed keeps exact fake Y. Relative keeps fake Y above real.")).defaultValue(AnchorMode.Fixed)).build());
        this.anchorY = this.sgGeneral.add(((((new IntSetting.Builder().name("anchor-y")).description("Exact fake Y in Fixed mode.")).defaultValue(100)).sliderRange(-64, 640).visible(() -> this.anchorMode.get() == AnchorMode.Fixed)).build());
        this.relativeAnchorOffset = this.sgGeneral.add((((new DoubleSetting.Builder().name("relative-anchor-offset")).description("Fake Y offset above real Y in Relative mode.")).defaultValue(96.0).min(1.0).sliderRange(1.0, 384.0).visible(() -> this.anchorMode.get() == AnchorMode.Relative)).build());
        this.maxAnchorDelta = this.sgGeneral.add(((new DoubleSetting.Builder().name("max-anchor-delta")).description("Maximum Y difference between real and fake anchor.")).defaultValue(72.0).min(8.0).sliderRange(8.0, 256.0).build());
        this.movementMode = this.sgGeneral.add((((new EnumSetting.Builder<TravelMode>().name("movement-mode")).description("How fake Y moves.")).defaultValue(TravelMode.VClip)).build());
        this.smoothSpeed = this.sgGeneral.add(((new DoubleSetting.Builder().name("smooth-speed")).description("Vertical distance moved per smooth step.")).defaultValue(1.75).min(0.25).sliderRange(0.25, 20.0).build());
        this.smoothCooldownTicks = this.sgGeneral.add((((new IntSetting.Builder().name("smooth-cooldown-ticks")).description("Ticks between smooth steps.")).defaultValue(1)).min(0).sliderRange(0, 6).build());
        this.maxPacketYStep = this.sgGeneral.add(((new DoubleSetting.Builder().name("max-packet-y-step")).description("Absolute maximum vertical delta per outgoing packet.")).defaultValue(3.0).min(0.5).sliderRange(0.5, 10.0).build());
        this.maxPacketXZStep = this.sgGeneral.add(((new DoubleSetting.Builder().name("max-packet-xz-step")).description("Absolute maximum horizontal delta (XZ) per outgoing packet.")).defaultValue(1.2).min(0.25).sliderRange(0.25, 6.0).build());
        this.antiKickPulse = this.sgGeneral.add((((new BoolSetting.Builder().name("anti-kick-pulse")).description("Flight-style anti-kick pulse.")).defaultValue(true)).build());
        this.antiKickDelayTicks = this.sgGeneral.add(((((new IntSetting.Builder().name("anti-kick-delay")).description("Ticks between packet anti-kick windows.")).defaultValue(10)).min(3).sliderRange(3, 80).visible(() -> this.antiKickPulse.get())).build());
        this.antiKickDownStep = this.sgGeneral.add((((new DoubleSetting.Builder().name("anti-kick-down-step")).description("How far the pulse moves down before returning.")).defaultValue(0.4).min(0.01).sliderRange(0.01, 0.08).visible(() -> this.antiKickPulse.get())).build());
        this.antiKickOffTicks = this.sgGeneral.add(((((new IntSetting.Builder().name("anti-kick-off-ticks")).description("Ticks per window to actively apply packet-level anti-kick.")).defaultValue(3)).min(1).sliderRange(1, 5).visible(() -> this.antiKickPulse.get())).build());
        this.vclipStep = this.sgGeneral.add((((new DoubleSetting.Builder().name("vclip-step")).description("Maximum vertical distance per vclip step.")).defaultValue(3.0).min(1.0).sliderRange(1.0, 30.0).visible(() -> this.movementMode.get() == TravelMode.VClip)).build());
        this.vclipMinStep = this.sgGeneral.add((((new DoubleSetting.Builder().name("vclip-min-step")).description("Minimum adaptive vclip step after failures.")).defaultValue(1.0).min(0.5).sliderRange(0.5, 10.0).visible(() -> this.movementMode.get() == TravelMode.VClip)).build());
        this.vclipBurstPackets = this.sgGeneral.add(((((new IntSetting.Builder().name("vclip-burst-packets")).description("Extra confirmation packets per vclip step.")).defaultValue(1)).min(1).sliderRange(1, 6).visible(() -> this.movementMode.get() == TravelMode.VClip)).build());
        this.vclipCooldownTicks = this.sgGeneral.add(((((new IntSetting.Builder().name("vclip-cooldown-ticks")).description("Ticks between vclip steps.")).defaultValue(1)).min(0).sliderRange(0, 12).visible(() -> this.movementMode.get() == TravelMode.VClip)).build());
        this.vclipFailureBackoffTicks = this.sgGeneral.add(((((new IntSetting.Builder().name("vclip-failure-backoff")).description("Base cooldown after vclip failure.")).defaultValue(10)).min(1).sliderRange(1, 40).visible(() -> this.movementMode.get() == TravelMode.VClip)).build());
        this.noFallAssistInterval = this.sgGeneral.add((((new IntSetting.Builder().name("no-fall-assist-interval")).description("Ticks between anti-fall assists.")).defaultValue(2)).min(1).sliderRange(1, 6).build());
        this.actionReachThreshold = this.sgActions.add(((new DoubleSetting.Builder().name("action-reach-threshold")).description("How close fake Y must be to real Y before action send.")).defaultValue(1.5).min(0.5).sliderRange(0.5, 4.0).build());
        this.actionSyncPackets = this.sgActions.add((((new IntSetting.Builder().name("action-sync-packets")).description("How many real-position packets to send around action packets.")).defaultValue(6)).min(1).sliderRange(1, 12).build());
        this.actionPostSync = this.sgActions.add((((new BoolSetting.Builder().name("action-post-sync")).description("Send one more real-position packet right after action packet.")).defaultValue(true)).build());
        this.confirmPlaceBeforeReturn = this.sgActions.add((((new BoolSetting.Builder().name("confirm-place-before-return")).description("After place, stay down briefly before going up.")).defaultValue(true)).build());
        this.placeConfirmTicks = this.sgActions.add(((((new IntSetting.Builder().name("place-confirm-ticks")).description("Ticks to stay down after place.")).defaultValue(1)).min(0).sliderRange(0, 10).visible(() -> this.confirmPlaceBeforeReturn.get())).build());
        this.confirmHitBeforeReturn = this.sgActions.add((((new BoolSetting.Builder().name("confirm-hit-before-return")).description("After hit/interact, stay down briefly before going up.")).defaultValue(true)).build());
        this.hitConfirmTicks = this.sgActions.add(((((new IntSetting.Builder().name("hit-confirm-ticks")).description("Ticks to stay down after hit/interact.")).defaultValue(1)).min(0).sliderRange(0, 10).visible(() -> this.confirmHitBeforeReturn.get())).build());
        this.avoidHitFov = this.sgActions.add((((new BoolSetting.Builder().name("avoid-hit-fov")).description("Offsets hit interactions to stay out of target view cone.")).defaultValue(true)).build());
        this.hitBehindOffset = this.sgActions.add((((new DoubleSetting.Builder().name("hit-behind-offset")).description("Back offset used for anti-FOV hit positioning.")).defaultValue(1.4).min(0.0).sliderRange(0.0, 4.0).visible(() -> this.avoidHitFov.get())).build());
        this.hitSideOffset = this.sgActions.add((((new DoubleSetting.Builder().name("hit-side-offset")).description("Side offset used for anti-FOV hit positioning.")).defaultValue(2.2).min(0.0).sliderRange(0.0, 6.0).visible(() -> this.avoidHitFov.get())).build());
        this.forceHitTeleportXZ = this.sgActions.add((((new BoolSetting.Builder().name("force-hit-teleport-xz")).description("Always use X/Z action teleport for entity hit/interact packets.")).defaultValue(true)).build());
        this.fastRiseAfterHit = this.sgActions.add((((new BoolSetting.Builder().name("fast-rise-after-hit")).description("Immediately climbs back to fake anchor after hit/interact.")).defaultValue(true)).build());
        this.hitRiseStep = this.sgActions.add((((new DoubleSetting.Builder().name("hit-rise-step")).description("Max Y increase per rapid-rise packet after hit/interact.")).defaultValue(3.0).min(0.5).sliderRange(0.5, 8.0).visible(() -> this.fastRiseAfterHit.get())).build());
        this.hitRiseBurstPackets = this.sgActions.add(((((new IntSetting.Builder().name("hit-rise-burst-packets")).description("How many rapid-rise packets to send after hit/interact.")).defaultValue(3)).min(1).sliderRange(1, 8).visible(() -> this.fastRiseAfterHit.get())).build());
        this.aggressiveNoFall = this.sgGeneral.add((((new BoolSetting.Builder().name("aggressive-no-fall")).description("Always send anti-fall assists while heavily desynced.")).defaultValue(true)).build());
        this.noFallDesyncThreshold = this.sgGeneral.add((((new DoubleSetting.Builder().name("no-fall-desync-threshold")).description("Desync distance that triggers aggressive anti-fall assists.")).defaultValue(1.0).min(0.5).sliderRange(0.5, 8.0).visible(() -> this.aggressiveNoFall.get())).build());
        this.paperOptimized = this.sgGeneral.add((((new BoolSetting.Builder().name("paper-optimized")).description("Use Paper tuned movement behavior.")).defaultValue(true)).build());
        this.paperMaxPacketStep = this.sgGeneral.add((((new DoubleSetting.Builder().name("paper-max-packet-step")).description("Maximum Y delta per movement packet in Paper mode.")).defaultValue(3.0).min(1.0).sliderRange(1.0, 10.0).visible(() -> this.paperOptimized.get())).build());
        this.paperVclipConfirmPackets = this.sgGeneral.add(((((new IntSetting.Builder().name("paper-vclip-confirm-packets")).description("Extra OnGround confirmation packets after each vclip step.")).defaultValue(0)).min(0).sliderRange(0, 6).visible(() -> this.paperOptimized.get())).build());
        this.paperFailureBackoffTicks = this.sgGeneral.add(((((new IntSetting.Builder().name("paper-failure-backoff")).description("Backoff used after repeated corrections.")).defaultValue(10)).min(1).sliderRange(1, 40).visible(() -> this.paperOptimized.get())).build());
        this.teleportInteractions = this.sgActions.add((((new BoolSetting.Builder().name("teleport-interactions")).description("Use action target X/Z while interacting.")).defaultValue(false)).build());
        this.breakMode = this.sgActions.add((((new EnumSetting.Builder<BreakMode>().name("break-mode")).description("Ignore cancels breaking. StayDown keeps fake Y at real while mining.")).defaultValue(BreakMode.StayDown)).build());
        this.creativeFastBlockActions = this.sgActions.add((((new BoolSetting.Builder().name("creative-fast-block-actions")).description("Use instant creative block action sync to reduce ghost blocks.")).defaultValue(true)).build());
        this.creativeDownResyncPackets = this.sgActions.add(((((new IntSetting.Builder().name("creative-down-resync-packets")).description("How many real-position packets to send before creative block actions.")).defaultValue(2)).min(1).sliderRange(1, 6).visible(() -> this.creativeFastBlockActions.get())).build());
        this.creativeUpResyncPackets = this.sgActions.add(((((new IntSetting.Builder().name("creative-up-resync-packets")).description("How many anchor-position packets to send after creative block actions.")).defaultValue(2)).min(1).sliderRange(1, 6).visible(() -> this.creativeFastBlockActions.get())).build());
        this.creativeClearActionQueue = this.sgActions.add(((((new BoolSetting.Builder().name("creative-clear-action-queue")).description("Clear queued holds after instant creative block actions.")).defaultValue(true)).visible(() -> this.creativeFastBlockActions.get())).build());
        this.miningNoInputReleaseTicksSetting = this.sgActions.add((((new IntSetting.Builder().name("mining-no-input-release-ticks")).description("Release mining hold after attack is no longer pressed.")).defaultValue(6)).min(2).sliderRange(2, 30).build());
        this.miningPacketSilenceTicks = this.sgActions.add((((new IntSetting.Builder().name("mining-packet-silence-ticks")).description("Release mining hold if no mining packets arrive for too long.")).defaultValue(10)).min(4).sliderRange(4, 40).build());
        this.allowPlace = this.sgActions.add((((new BoolSetting.Builder().name("allow-place")).description("Drop to place then return.")).defaultValue(true)).build());
        this.allowStorage = this.sgActions.add((((new BoolSetting.Builder().name("allow-storage")).description("Drop for storage and stay down while screen is open.")).defaultValue(true)).build());
        this.allowEntityHit = this.sgActions.add((((new BoolSetting.Builder().name("allow-hit")).description("Drop for entity hit/interact then return.")).defaultValue(true)).build());
        this.collectItems = this.sgActions.add((((new BoolSetting.Builder().name("collect-items")).description("Drop to real Y to collect nearby item/XP.")).defaultValue(false)).build());
        this.collectUntilClear = this.sgActions.add(((((new BoolSetting.Builder().name("collect-until-clear")).description("Stay down until collectibles are gone.")).defaultValue(true)).visible(() -> this.collectItems.get())).build());
        this.collectBurstTicks = this.sgActions.add(((((new IntSetting.Builder().name("collect-burst-ticks")).description("Stay-down ticks if collect-until-clear is off.")).defaultValue(6)).min(1).sliderRange(1, 40).visible(() -> (Boolean)this.collectItems.get() != false && (Boolean)this.collectUntilClear.get() == false)).build());
        this.collectRange = this.sgActions.add((((new DoubleSetting.Builder().name("collect-range")).description("Range for item/XP collect detection.")).defaultValue(3.0).min(0.5).sliderRange(0.5, 8.0).visible(() -> this.collectItems.get())).build());
        this.containerGraceTicks = this.sgActions.add(((((new IntSetting.Builder().name("container-grace-ticks")).description("Ticks to wait for a storage screen to open.")).defaultValue(8)).min(1).sliderRange(1, 30).visible(() -> this.allowStorage.get())).build());
        this.escapeSkyY = this.sgEscape.add((((new IntSetting.Builder().name("escape-sky-y")).description("Default .escape sky target Y.")).defaultValue(320)).sliderRange(-64, 640).build());
        this.escapeBedrockOffset = this.sgEscape.add((((new IntSetting.Builder().name("escape-bedrock-offset")).description("Vertical distance used by .escape bedrock from your current Y.")).defaultValue(200)).min(8).sliderRange(8, 512).build());
        this.renderServerPos = this.sgRender.add(((new BoolSetting.Builder().name("render-server-pos")).defaultValue(true)).build());
        this.serverBoxColor = this.sgRender.add(((new ColorSetting.Builder().name("server-box-color")).defaultValue(new SettingColor(255, 80, 80, 45)).visible(() -> this.renderServerPos.get())).build());
        this.serverLineColor = this.sgRender.add(((new ColorSetting.Builder().name("server-line-color")).defaultValue(new SettingColor(255, 120, 120, 255)).visible(() -> this.renderServerPos.get())).build());
        this.renderLinkLine = this.sgRender.add(((((new BoolSetting.Builder().name("render-real-link")).description("Draw a line between real and fake position.")).defaultValue(true)).visible(() -> this.renderServerPos.get())).build());
        this.renderRealHitbox = this.sgRender.add((((new BoolSetting.Builder().name("render-real-hitbox")).description("Render your real client hitbox.")).defaultValue(true)).build());
        this.realBoxColor = this.sgRender.add(((new ColorSetting.Builder().name("real-box-color")).defaultValue(new SettingColor(80, 170, 255, 35)).visible(() -> this.renderRealHitbox.get())).build());
        this.realLineColor = this.sgRender.add(((new ColorSetting.Builder().name("real-line-color")).defaultValue(new SettingColor(100, 190, 255, 255)).visible(() -> this.renderRealHitbox.get())).build());
        this.sendingPackets = false;
        this.serverX = 0.0;
        this.serverZ = 0.0;
        this.currentServerY = 0.0;
        this.holdMode = HoldMode.None;
        this.miningHoldActive = false;
        this.miningNoInputReleaseTicks = 0;
        this.lastMiningPacketAge = -1;
        this.lastCreativeInstantActionAge = -1;
        this.collectBurstTicksLeft = 0;
        this.containerGraceTicksLeft = 0;
        this.containerScreenSeen = false;
        this.postActionHoldTicksLeft = 0;
        this.escapeOverrideActive = false;
        this.escapeOverrideY = 320.0;
        this.escapeBurstPending = false;
        this.escapeBurstRetriesLeft = 0;
        this.escapeBurstRetryTicks = 0;
        this.actionQueue = new ArrayDeque<QueuedAction>();
        this.activeAction = null;
        this.activeActionTicks = 0;
        this.movementCooldownTicks = 0;
        this.antiKickTicksLeft = 0;
        this.noFallAssistCooldownTicks = 0;
        this.extraNoFallTicks = 0;
        this.correctionWindowTicks = 0;
        this.correctionStrikes = 0;
        this.correctionFreezeTicks = 0;
        this.consecutiveSendFailures = 0;
        this.sentPacketTimesMs = new ArrayDeque<Long>();
        this.bufferedPackets = new ArrayDeque<>();
        this.vclipAttemptPending = false;
        this.pendingVClipStartY = Double.NaN;
        this.pendingVClipTargetY = Double.NaN;
        this.pendingVClipTicks = 0;
        this.vclipFailureCount = 0;
        this.adaptiveVClipStep = 4.0;
        this.vclipDamageSafetyTicks = 0;
        this.respawnGraceTicks = 0;
        this.miningHoldFailsafeTicks = 0;
        this.sawDeath = false;
        this.lastPlayerEntityId = Integer.MIN_VALUE;
        this.lastPlayerAge = -1;
        this.packetGetEntityLookupFailed = false;
        this.packetEntityIdLookupFailed = false;
    }

    public void onActivate() {
        if (this.mc.player == null || this.mc.getConnection() == null) return;
        this.resetRuntimeState();
        this.serverX = this.mc.player.getX();
        this.serverZ = this.mc.player.getZ();
        this.currentServerY = this.clampWorldY(this.mc.player.getY());
        this.lastPlayerEntityId = this.mc.player.getId();
        this.lastPlayerAge = this.mc.player.tickCount;
        this.mc.player.fallDistance = 0.0;
    }

    public void onDeactivate() {
        if (this.mc.player != null && this.mc.getConnection() != null) {
            double realX = this.mc.player.getX();
            double realY = this.clampWorldY(this.mc.player.getY());
            double realZ = this.mc.player.getZ();
            this.serverX = realX;
            this.serverZ = realZ;
            this.currentServerY = realY;
            this.withPacketBypass(() -> this.sendDirectResyncPackets(realX, realY, realZ, 6));
            this.mc.player.setPos(realX, realY, realZ);
            this.mc.player.setDeltaMovement(this.mc.player.getDeltaMovement().x, 0.0, this.mc.player.getDeltaMovement().z);
            this.mc.player.fallDistance = 0.0;
        }
        this.resetRuntimeState();
        this.escapeOverrideActive = false;
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        this.resetRuntimeState();
        this.respawnGraceTicks = 40;
        if (this.mc.player != null) {
            this.serverX = this.mc.player.getX();
            this.serverZ = this.mc.player.getZ();
            this.currentServerY = this.clampWorldY(this.mc.player.getY());
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.mc.player == null || this.mc.getConnection() == null || this.mc.level == null) {
            return;
        }
        double realX = this.mc.player.getX();
        double realY = this.mc.player.getY();
        double realZ = this.mc.player.getZ();
        if (!Double.isFinite(this.serverX)) {
            this.serverX = realX;
        }
        if (!Double.isFinite(this.serverZ)) {
            this.serverZ = realZ;
        }
        if (!Double.isFinite(this.currentServerY)) {
            this.currentServerY = this.clampWorldY(realY);
        }
        if (Math.abs(this.currentServerY - realY) > 192.0) {
            this.hardResyncToReal(realX, realY, realZ, false);
        }
        boolean deadNow = this.isPlayerDead();
        boolean playerChanged = this.hasPlayerIdentityChanged();
        if (deadNow) {
            this.handleDeadState(realX, realY, realZ);
            this.sendPermanentNoFall(realX, realY, realZ);
            this.trackPlayerState();
            return;
        }
        if (playerChanged || this.sawDeath) {
            this.hardResyncToReal(realX, realY, realZ, true);
            this.sawDeath = false;
            this.respawnGraceTicks = Math.max(this.respawnGraceTicks, 40);
        }
        if (this.mc.player.hurtTime > 0 || this.mc.player.getHealth() <= 4.0f) {
            this.vclipDamageSafetyTicks = Math.max(this.vclipDamageSafetyTicks, 12);
            this.extraNoFallTicks = Math.max(this.extraNoFallTicks, 24);
        }
        if (this.respawnGraceTicks > 0) {
            --this.respawnGraceTicks;
            this.clearActionState();
            this.resetHoldState();
            this.resetVClipState();
            this.serverX = realX;
            this.serverZ = realZ;
            this.currentServerY = this.clampWorldY(realY);
            this.sendPermanentNoFall(realX, realY, realZ);
            this.trackPlayerState();
            return;
        }
        if (this.shouldTemporarilyBypassSpoofing()) {
            this.hardResyncToReal(realX, realY, realZ, false);
            this.sendPermanentNoFall(realX, realY, realZ);
            this.trackPlayerState();
            return;
        }
        if (this.consecutiveSendFailures >= 18) {
            this.recoverFromSendStall(realX, realY, realZ);
            this.sendPermanentNoFall(realX, realY, realZ);
            this.trackPlayerState();
            return;
        }
        if (this.movementCooldownTicks > 0) {
            --this.movementCooldownTicks;
        }
        if (this.noFallAssistCooldownTicks > 0) {
            --this.noFallAssistCooldownTicks;
        }
        if (this.correctionWindowTicks > 0) {
            --this.correctionWindowTicks;
        }
        if (this.correctionFreezeTicks > 0) {
            --this.correctionFreezeTicks;
        }
        if (this.escapeBurstRetryTicks > 0) {
            --this.escapeBurstRetryTicks;
        }
        if (this.vclipDamageSafetyTicks > 0) {
            --this.vclipDamageSafetyTicks;
        }
        this.flushBufferedPackets();
        this.updateVClipAttemptState();
        this.updateHoldState();
        this.updateMiningHoldSafety();
        if (this.escapeBurstPending) {
            this.escapeBurstPending = false;
            this.escapeBurstRetriesLeft = 16;
            this.escapeBurstRetryTicks = 0;
        }
        this.processEscapeBurst(realY);
        this.doAntiKickPulse(realX, realY, realZ);
        this.pruneStaleQueuedActions();
        if (this.activeAction == null && !this.actionQueue.isEmpty()) {
            this.activeAction = this.actionQueue.pollFirst();
            this.activeActionTicks = 0;
        }
        if (this.activeAction != null) {
            ++this.activeActionTicks;
            if (this.activeActionTicks > 60) {
                this.activeAction = null;
                this.activeActionTicks = 0;
                this.hardResyncToReal(realX, realY, realZ, false);
            } else {
                this.processActiveAction(realX, realY, realZ);
            }
        } else {
            double targetY;
            TravelMode travelMode = this.getSafeTravelMode();
            double d = targetY = this.holdMode == HoldMode.None ? this.getDesiredAnchorY(realY) : this.clampWorldY(realY);
            if (this.correctionFreezeTicks > 0 && targetY > this.currentServerY && this.holdMode == HoldMode.None && !this.escapeOverrideActive) {
                targetY = this.currentServerY;
            }
            this.travelToward(targetY, travelMode, realX, realZ);
        }
        this.sendPermanentNoFall(realX, realY, realZ);
        this.trackPlayerState();
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (this.mc.player == null || this.mc.getConnection() == null || this.mc.level == null) {
            return;
        }
        if (this.sendingPackets) {
            return;
        }
        if (this.respawnGraceTicks > 0) {
            return;
        }
        if (this.shouldTemporarilyBypassSpoofing()) {
            return;
        }
        Packet packet = event.packet;
        if (packet instanceof ServerboundMovePlayerPacket) {
            ServerboundMovePlayerPacket move = (ServerboundMovePlayerPacket)packet;
            if (((IServerboundMovePlayerPacket)move).meteor$getTag() == 1337) {
                return;
            }
            ((ServerboundMovePlayerPacketAccessor)move).meteor$setOnGround(true);
            if (this.mc.player.fallDistance > 2.5 || this.mc.player.getDeltaMovement().y < -0.3) {
                this.extraNoFallTicks = Math.max(this.extraNoFallTicks, 24);
                this.noFallAssistCooldownTicks = 0;
            }
            if (Modules.get().isActive(Flight.class)) {
                return;
            }
            if (move.hasPosition()) {
                event.cancel();
            }
            return;
        }
        packet = event.packet;
        if (packet instanceof ServerboundPlayerActionPacket) {
            ServerboundPlayerActionPacket packet2 = (ServerboundPlayerActionPacket)packet;
            ServerboundPlayerActionPacket.Action action = packet2.getAction();
            if (this.isMiningAction(action)) {
                if (this.breakMode.get() == BreakMode.Ignore) {
                    return;
                }
                event.cancel();
                if (this.isCreativePlayer() && this.handleCreativeMiningImmediate(packet2, action)) {
                    return;
                }
                this.handleMiningPacket(packet2, action);
            }
            return;
        }
        packet = event.packet;
        if (packet instanceof ServerboundUseItemOnPacket) {
            ServerboundUseItemOnPacket packet3 = (ServerboundUseItemOnPacket)packet;
            BlockPos blockPos = packet3.getHitResult().getBlockPos();
            boolean container = this.isContainerInteraction(blockPos);
            if (this.isCreativePlayer() && ((Boolean)this.creativeFastBlockActions.get()).booleanValue() && !container && ((Boolean)this.allowPlace.get()).booleanValue() && this.activeAction == null && this.actionQueue.isEmpty() && this.holdMode != HoldMode.Mining) {
                event.cancel();
                if (!this.sendCreativeInstantBlockAction((Packet<?>)packet3, packet3.getHitResult().getLocation(), (Boolean)this.teleportInteractions.get())) {
                    this.handleBlockInteract(packet3);
                }
                return;
            }
            event.cancel();
            this.handleBlockInteract(packet3);
            return;
        }
        packet = event.packet;
        if (packet instanceof ServerboundInteractPacket) {
            ServerboundInteractPacket packet4 = (ServerboundInteractPacket)packet;
            event.cancel();
            this.handleEntityInteract(packet4);
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof ClientboundRespawnPacket) {
            this.respawnGraceTicks = 40;
            this.vclipDamageSafetyTicks = 12;
            if (this.mc.player != null) {
                this.hardResyncToReal(this.mc.player.getX(), this.mc.player.getY(), this.mc.player.getZ(), true);
            } else {
                this.resetRuntimeState();
            }
            return;
        }
        Packet packet = event.packet;
        if (!(packet instanceof ClientboundPlayerPositionPacket)) {
            return;
        }
        ClientboundPlayerPositionPacket packet2 = (ClientboundPlayerPositionPacket)packet;
        if (this.mc.player == null) {
            return;
        }
        Vec3 packetPos = this.resolvePacketPosition(packet2);
        double realX = this.mc.player.getX();
        double realY = this.mc.player.getY();
        double realZ = this.mc.player.getZ();
        if (!this.isFiniteVec(packetPos)) {
            this.hardResyncToReal(realX, realY, realZ, false);
            return;
        }
        if (this.respawnGraceTicks > 0) {
            this.handleAcceptedCorrection(packetPos);
            return;
        }
        event.cancel();
        this.withPacketBypass(() -> this.mc.getConnection().send((Packet<?>)new ServerboundAcceptTeleportationPacket(packet2.id())));
        if (this.vclipAttemptPending) {
            this.evaluateVClipCorrection(packetPos);
        }
        if (this.isPlayerDead()) {
            this.handleAcceptedCorrection(packetPos);
            return;
        }
        if (this.correctionWindowTicks <= 0) {
            this.correctionWindowTicks = 20;
            this.correctionStrikes = 0;
        }
        if (this.isCorrectionNearTracked(packetPos)) {
            this.correctionStrikes = Math.max(0, this.correctionStrikes - 1);
            this.handleAcceptedCorrection(packetPos);
            return;
        }
        ++this.correctionStrikes;
        this.adaptiveVClipStep = Math.max(this.getVclipMinStepValue(), this.adaptiveVClipStep * 0.75);
        int freezeBase = (Boolean)this.paperOptimized.get() != false ? Math.max(8, (Integer)this.paperFailureBackoffTicks.get()) : 8;
        int freezePenalty = Math.min(20, this.correctionStrikes * 2);
        this.correctionFreezeTicks = Math.max(this.correctionFreezeTicks, Math.min(40, freezeBase + freezePenalty));
        this.movementCooldownTicks = Math.max(this.movementCooldownTicks, 2);
        this.extraNoFallTicks = Math.max(this.extraNoFallTicks, 16);
        boolean nearReal = this.isCorrectionNearReal(packetPos, realX, realY, realZ);
        boolean farFromTracked = this.isCorrectionFarFromTracked(packetPos);
        if (nearReal && this.activeAction == null && this.holdMode == HoldMode.None && !this.escapeOverrideActive) {
            this.hardResyncToReal(realX, realY, realZ, false);
            return;
        }
        if (farFromTracked || this.correctionStrikes >= 4) {
            this.handleAcceptedCorrection(packetPos);
            this.correctionFreezeTicks = Math.max(this.correctionFreezeTicks, 4);
            this.movementCooldownTicks = Math.max(this.movementCooldownTicks, 2);
            return;
        }
        this.withPacketBypass(() -> this.sendTrackedPosition(this.serverX, this.currentServerY, this.serverZ, true));
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (this.mc.player == null) {
            return;
        }
        if (((Boolean)this.renderServerPos.get()).booleanValue()) {
            AABB box = new AABB(this.serverX - 0.3, this.currentServerY, this.serverZ - 0.3, this.serverX + 0.3, this.currentServerY + 1.8, this.serverZ + 0.3);
            event.renderer.box(box, (Color)this.serverBoxColor.get(), (Color)this.serverLineColor.get(), ShapeMode.Both, 1);
            if (((Boolean)this.renderLinkLine.get()).booleanValue()) {
                Vec3 eye = this.mc.player.getEyePosition();
                event.renderer.line(eye.x, eye.y, eye.z, this.serverX, this.currentServerY + 1.62, this.serverZ, (Color)this.serverLineColor.get());
            }
        }
        if (((Boolean)this.renderRealHitbox.get()).booleanValue()) {
            event.renderer.box(this.mc.player.getBoundingBox(), (Color)this.realBoxColor.get(), (Color)this.realLineColor.get(), ShapeMode.Both, 1);
        }
    }

    public void requestEscapeSky(Double requestedY) {
        this.escapeOverrideActive = true;
        double requested = requestedY != null && Double.isFinite(requestedY) ? requestedY : (double)((Integer)this.escapeSkyY.get()).intValue();
        this.escapeOverrideY = this.clampWorldY(requested);
        this.clearActionState();
        this.resetHoldState();
        this.resetVClipState();
        this.clearPacketBudgetState();
        this.correctionFreezeTicks = 0;
        this.correctionStrikes = 0;
        this.correctionWindowTicks = 0;
        this.escapeBurstPending = true;
        this.escapeBurstRetriesLeft = 16;
        this.escapeBurstRetryTicks = 0;
        this.movementCooldownTicks = 0;
    }

    public void requestEscapeBedrock() {
        double baseY;
        this.escapeOverrideActive = true;
        double d = baseY = this.mc.player != null ? this.mc.player.getY() : this.currentServerY;
        if (!Double.isFinite(baseY)) {
            baseY = this.currentServerY;
        }
        this.escapeOverrideY = this.clampWorldY(baseY + Math.max(8.0, (double)((Integer)this.escapeBedrockOffset.get()).intValue()));
        this.clearActionState();
        this.resetHoldState();
        this.resetVClipState();
        this.clearPacketBudgetState();
        this.correctionFreezeTicks = 0;
        this.correctionStrikes = 0;
        this.correctionWindowTicks = 0;
        this.escapeBurstPending = true;
        this.escapeBurstRetriesLeft = 16;
        this.escapeBurstRetryTicks = 0;
        this.movementCooldownTicks = 0;
    }

    public void cancelEscapeSpam() {
        this.escapeOverrideActive = false;
        this.escapeBurstPending = false;
        this.escapeBurstRetriesLeft = 0;
        this.escapeBurstRetryTicks = 0;
        this.clearActionState();
        this.resetHoldState();
        this.resetVClipState();
        if (!this.isActive() || this.mc.player == null || this.mc.getConnection() == null) {
            return;
        }
        this.hardResyncToReal(this.mc.player.getX(), this.mc.player.getY(), this.mc.player.getZ(), false);
    }

    public boolean isEscapeSpamActive() {
        return this.escapeOverrideActive;
    }

    private void processEscapeBurst(double realY) {
        boolean done;
        if (!this.escapeOverrideActive) {
            return;
        }
        if (this.escapeBurstRetriesLeft <= 0) {
            return;
        }
        if (this.escapeBurstRetryTicks > 0) {
            return;
        }
        boolean bl = done = this.trySendEscapeBurst(realY) || Math.abs(this.getDesiredAnchorY(realY) - this.currentServerY) <= 1.0;
        if (done) {
            this.escapeBurstRetriesLeft = 0;
            return;
        }
        --this.escapeBurstRetriesLeft;
        this.escapeBurstRetryTicks = 2;
    }

    private boolean trySendEscapeBurst(double realY) {
        if (!this.escapeOverrideActive || this.mc.player == null || this.mc.getConnection() == null) {
            return false;
        }
        double targetY = this.getDesiredAnchorY(realY);
        if (Math.abs(targetY - this.currentServerY) < 1.0) {
            return true;
        }
        int budgetRemaining = Math.max(0, this.getPacketBudgetRemaining() - 8);
        if (budgetRemaining <= 0) {
            return false;
        }
        int packetLimit = Math.max(1, Math.min(20, budgetRemaining));
        double maxBurstDistance = Math.max(8.0, 200.0);
        double[] sentDistance = new double[]{0.0};
        boolean[] sent = new boolean[]{false};
        this.withPacketBypass(() -> {
            double remaining;
            for (int i = 0; i < packetLimit && !(Math.abs(remaining = targetY - this.currentServerY) <= 0.75) && !(sentDistance[0] >= maxBurstDistance); ++i) {
                double packetStep = Math.min(this.getEffectiveMaxYStep(), Math.abs(remaining));
                if ((packetStep = Math.min(packetStep, maxBurstDistance - sentDistance[0])) <= 0.0) break;
                double previousY = this.currentServerY;
                double nextY = this.clampWorldY(this.currentServerY + Math.copySign(packetStep, remaining));
                if (!this.sendTrackedPosition(this.serverX, nextY, this.serverZ, true)) break;
                sentDistance[0] = sentDistance[0] + Math.abs(nextY - previousY);
                sent[0] = true;
            }
        });
        if (sent[0]) {
            this.extraNoFallTicks = Math.max(this.extraNoFallTicks, 24);
            this.movementCooldownTicks = Math.max(this.movementCooldownTicks, 1);
        }
        return sent[0] || Math.abs(targetY - this.currentServerY) <= 1.0;
    }

    private void handleMiningPacket(ServerboundPlayerActionPacket packet, ServerboundPlayerActionPacket.Action action) {
        if (this.breakMode.get() == BreakMode.Ignore) {
            return;
        }
        boolean start = action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK;
        boolean stop = action == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK || action == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK;
        int n = this.lastMiningPacketAge = this.mc.player != null ? this.mc.player.tickCount : -1;
        if (start) {
            this.miningHoldActive = true;
            this.miningHoldFailsafeTicks = 50;
            this.miningNoInputReleaseTicks = Math.max(2, (Integer)this.miningNoInputReleaseTicksSetting.get());
        }
        if (stop) {
            this.miningHoldActive = false;
            this.miningHoldFailsafeTicks = 0;
            this.miningNoInputReleaseTicks = 0;
            this.lastMiningPacketAge = -1;
        }
        PendingOutcome outcome = start ? PendingOutcome.HoldMining : PendingOutcome.Return;
        boolean prioritize = true;
        this.queueAction((Packet<?>)packet, Vec3.atCenterOf((Vec3i)packet.getPos()), outcome, 0, prioritize, (Boolean)this.teleportInteractions.get(), false);
    }

    private boolean handleCreativeMiningImmediate(ServerboundPlayerActionPacket packet, ServerboundPlayerActionPacket.Action action) {
        if (packet == null || this.mc.player == null || this.mc.getConnection() == null) {
            return false;
        }
        if (!this.isMiningAction(action)) {
            return false;
        }
        boolean start = action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK;
        boolean stop = action == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK || action == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK;
        double realX = this.mc.player.getX();
        double realY = this.clampWorldY(this.mc.player.getY());
        double realZ = this.mc.player.getZ();
        Vec3 target = Vec3.atCenterOf((Vec3i)packet.getPos());
        double actionX = (Boolean)this.teleportInteractions.get() != false ? target.x : realX;
        double actionZ = (Boolean)this.teleportInteractions.get() != false ? target.z : realZ;
        int downPackets = Math.max(1, (Integer)this.creativeDownResyncPackets.get());
        this.withPacketBypass(() -> {
            this.sendDirectResyncPackets(actionX, realY, actionZ, downPackets);
            this.mc.getConnection().send((Packet)packet);
            if (((Boolean)this.actionPostSync.get()).booleanValue()) {
                this.sendDirectResyncPackets(actionX, realY, actionZ, 1);
            }
        });
        this.serverX = actionX;
        this.serverZ = actionZ;
        this.currentServerY = realY;
        this.movementCooldownTicks = 0;
        this.consecutiveSendFailures = 0;
        this.extraNoFallTicks = Math.max(this.extraNoFallTicks, 16);
        this.lastMiningPacketAge = this.mc.player.tickCount;
        if (start) {
            if (((Boolean)this.creativeClearActionQueue.get()).booleanValue()) {
                this.clearActionState();
            }
            this.holdMode = HoldMode.Mining;
            this.miningHoldActive = true;
            this.miningHoldFailsafeTicks = 50;
            this.miningNoInputReleaseTicks = Math.max(2, (Integer)this.miningNoInputReleaseTicksSetting.get());
        } else if (stop) {
            this.miningHoldActive = false;
            this.miningHoldFailsafeTicks = 0;
            this.miningNoInputReleaseTicks = 0;
            if (this.activeAction == null) {
                this.holdMode = HoldMode.None;
            }
        }
        return true;
    }

    private boolean isMiningAction(ServerboundPlayerActionPacket.Action action) {
        return action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK || action == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK || action == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK;
    }

    private boolean isCreativePlayer() {
        return this.mc.player != null && this.mc.player.getAbilities().instabuild;
    }

    private boolean sendCreativeInstantBlockAction(Packet<?> packet, Vec3 target, boolean teleportXZ) {
        if (packet == null || this.mc.player == null || this.mc.getConnection() == null) {
            return false;
        }
        double realX = this.mc.player.getX();
        double realY = this.clampWorldY(this.mc.player.getY());
        double realZ = this.mc.player.getZ();
        double actionX = teleportXZ ? target.x : realX;
        double actionZ = teleportXZ ? target.z : realZ;
        double actionY = realY;
        double returnX = actionX;
        double returnZ = actionZ;
        double returnY = this.getDesiredAnchorY(realY);
        if (!Double.isFinite(returnY)) {
            returnY = Double.isFinite(this.currentServerY) ? this.clampWorldY(this.currentServerY) : realY;
        }
        int downPackets = Math.max(1, (Integer)this.creativeDownResyncPackets.get());
        int upPackets = Math.max(1, (Integer)this.creativeUpResyncPackets.get());
        if (this.lastCreativeInstantActionAge >= 0 && this.mc.player.tickCount - this.lastCreativeInstantActionAge <= 1) {
            downPackets = 1;
            upPackets = 1;
        }
        this.lastCreativeInstantActionAge = this.mc.player.tickCount;
        int finalDownPackets = downPackets;
        int finalUpPackets = upPackets;
        double finalReturnY = returnY;
        this.withPacketBypass(() -> {
            this.sendDirectResyncPackets(actionX, actionY, actionZ, finalDownPackets);
            this.mc.getConnection().send(packet);
            this.sendDirectResyncPackets(returnX, finalReturnY, returnZ, finalUpPackets);
        });
        if (((Boolean)this.creativeClearActionQueue.get()).booleanValue()) {
            this.clearActionState();
        }
        this.resetHoldState();
        this.serverX = returnX;
        this.serverZ = returnZ;
        this.currentServerY = returnY;
        this.correctionFreezeTicks = 0;
        this.correctionWindowTicks = 0;
        this.correctionStrikes = 0;
        this.movementCooldownTicks = 0;
        this.extraNoFallTicks = Math.max(this.extraNoFallTicks, 16);
        this.consecutiveSendFailures = 0;
        return true;
    }

    private void handleBlockInteract(ServerboundUseItemOnPacket packet) {
        boolean container = this.isContainerInteraction(packet.getHitResult().getBlockPos());
        if (container && !((Boolean)this.allowStorage.get()).booleanValue()) {
            return;
        }
        if (!container && !((Boolean)this.allowPlace.get()).booleanValue()) {
            return;
        }
        this.queueAction((Packet<?>)packet, packet.getHitResult().getLocation(), container ? PendingOutcome.HoldContainer : PendingOutcome.Return, container ? 0 : this.getPlacePostActionTicks(), false, (Boolean)this.teleportInteractions.get(), false);
    }

    private void handleEntityInteract(ServerboundInteractPacket packet) {
        if (!((Boolean)this.allowEntityHit.get()).booleanValue()) {
            return;
        }
        NoFriendHit noFriendHit = (NoFriendHit)Modules.get().get(NoFriendHit.class);
        if (noFriendHit != null && noFriendHit.isActive() && noFriendHit.shouldBlockPacket(packet, false)) {
            return;
        }
        boolean teleportHit = (Boolean)this.teleportInteractions.get() != false || (Boolean)this.forceHitTeleportXZ.get() != false;
        int postActionTicks = (Boolean)this.fastRiseAfterHit.get() != false ? 0 : this.getHitPostActionTicks();
        this.queueAction((Packet<?>)packet, this.resolveEntityActionTarget(packet), PendingOutcome.Return, postActionTicks, false, teleportHit, true);
    }

    private void pruneStaleQueuedActions() {
        QueuedAction head;
        if (this.mc.player == null || this.actionQueue.isEmpty()) {
            return;
        }
        int nowAge = this.mc.player.tickCount;
        int maxQueuedAge = 120;
        while (!this.actionQueue.isEmpty() && (head = this.actionQueue.peekFirst()) != null && head.createdAtAge <= nowAge && nowAge - head.createdAtAge > maxQueuedAge) {
            this.actionQueue.pollFirst();
        }
    }

    private void queueAction(Packet<?> packet, Vec3 target, PendingOutcome outcome, int postActionHoldTicks, boolean prioritizeFront, boolean teleportXZ, boolean entityInteraction) {
        int createdAtAge;
        Vec3 safeTarget = this.sanitizeActionTarget(target);
        QueuedAction queued = new QueuedAction(packet, safeTarget, outcome, teleportXZ, entityInteraction, postActionHoldTicks, createdAtAge = this.mc.player != null ? this.mc.player.tickCount : 0);
        if (!this.shouldSkipDuplicateCheck(queued) && this.isDuplicateAction(queued)) {
            return;
        }
        if (this.activeAction == null && this.actionQueue.isEmpty()) {
            this.activeAction = queued;
            this.activeActionTicks = 0;
            return;
        }
        if (prioritizeFront) {
            while (this.actionQueue.size() >= 8) {
                this.actionQueue.pollLast();
            }
            this.actionQueue.addFirst(queued);
            return;
        }
        while (this.actionQueue.size() >= 8) {
            this.actionQueue.pollFirst();
        }
        this.actionQueue.addLast(queued);
    }

    private void processActiveAction(double realX, double realY, double realZ) {
        boolean forceNow;
        if (this.activeAction == null) {
            return;
        }
        double actionX = this.activeAction.teleportXZ ? this.activeAction.target.x : realX;
        double actionZ = this.activeAction.teleportXZ ? this.activeAction.target.z : realZ;
        double actionY = this.clampWorldY(realY);
        TravelMode safeTravelMode = this.getSafeTravelMode();
        double verticalDesync = Math.abs(this.currentServerY - realY);
        int plannedSyncPackets = this.getActionSyncPacketCount(actionY);
        double forcedReach = (Double)this.actionReachThreshold.get() + this.getEffectiveMaxYStep() * (double)plannedSyncPackets;
        boolean bl = forceNow = this.activeActionTicks >= 8;
        if (this.activeActionTicks >= 16 && verticalDesync > forcedReach) {
            this.serverX = realX;
            this.serverZ = realZ;
            this.currentServerY = actionY;
            verticalDesync = 0.0;
            this.extraNoFallTicks = Math.max(this.extraNoFallTicks, 16);
        }
        if (verticalDesync > (Double)this.actionReachThreshold.get() && (!forceNow || verticalDesync > forcedReach)) {
            this.travelToward(realY, safeTravelMode, actionX, actionZ);
            return;
        }
        if (!this.sendActionNow(this.activeAction, actionX, actionY, actionZ)) {
            this.movementCooldownTicks = Math.max(this.movementCooldownTicks, 1);
            return;
        }
        QueuedAction completed = this.activeAction;
        this.activeAction = null;
        this.activeActionTicks = 0;
        if (completed.entityInteraction && ((Boolean)this.fastRiseAfterHit.get()).booleanValue()) {
            this.sendFastRiseAfterHit(realY);
        }
        this.applyActionOutcome(completed);
    }

    private boolean sendActionNow(QueuedAction action, double actionX, double actionY, double actionZ) {
        if (this.mc.player == null || this.mc.getConnection() == null) {
            return false;
        }
        if (this.isMiningActionPacket(action.packet)) {
            return this.sendMiningActionNow(action.packet, actionX, actionY, actionZ);
        }
        int syncPackets = this.getActionSyncPacketCount(actionY);
        if (syncPackets <= 0) {
            boolean[] fallbackSent = new boolean[]{false};
            this.withPacketBypass(() -> {
                fallbackSent[0] = this.trySendPacketNow(action.packet);
            });
            if (!fallbackSent[0]) {
                return false;
            }
            this.serverX = actionX;
            this.serverZ = actionZ;
            this.currentServerY = actionY;
            this.extraNoFallTicks = Math.max(this.extraNoFallTicks, 16);
            this.movementCooldownTicks = Math.max(this.movementCooldownTicks, 1);
            return true;
        }
        boolean[] ok = new boolean[]{true};
        this.withPacketBypass(() -> {
            for (int i = 0; i < syncPackets; ++i) {
                if (this.sendTrackedPosition(actionX, actionY, actionZ, true)) continue;
                ok[0] = false;
                return;
            }
            if (Math.abs(this.currentServerY - actionY) > (Double)this.actionReachThreshold.get() + 0.35) {
                ok[0] = false;
                return;
            }
            if (!this.trySendPacketNow(action.packet)) {
                ok[0] = false;
                return;
            }
            if (((Boolean)this.actionPostSync.get()).booleanValue() && !this.sendTrackedPosition(actionX, actionY, actionZ, true)) {
                ok[0] = false;
            }
        });
        if (!ok[0]) {
            return false;
        }
        this.serverX = actionX;
        this.serverZ = actionZ;
        this.currentServerY = actionY;
        this.extraNoFallTicks = Math.max(this.extraNoFallTicks, 24);
        this.movementCooldownTicks = 0;
        return true;
    }

    private boolean sendMiningActionNow(Packet<?> packet, double actionX, double actionY, double actionZ) {
        if (packet == null || this.mc.player == null || this.mc.getConnection() == null) {
            return false;
        }
        int downPackets = this.isCreativePlayer() ? 2 : 1;
        this.withPacketBypass(() -> {
            this.sendDirectResyncPackets(actionX, actionY, actionZ, downPackets);
            this.mc.getConnection().send(packet);
            if (((Boolean)this.actionPostSync.get()).booleanValue()) {
                this.sendDirectResyncPackets(actionX, actionY, actionZ, 1);
            }
        });
        this.serverX = actionX;
        this.serverZ = actionZ;
        this.currentServerY = actionY;
        this.extraNoFallTicks = Math.max(this.extraNoFallTicks, 16);
        this.movementCooldownTicks = 0;
        this.consecutiveSendFailures = 0;
        return true;
    }

    private void sendFastRiseAfterHit(double realY) {
        if (this.mc.player == null || this.mc.getConnection() == null) {
            return;
        }
        if (!((Boolean)this.fastRiseAfterHit.get()).booleanValue()) {
            return;
        }
        double targetY = this.getDesiredAnchorY(realY);
        if (targetY <= this.currentServerY + 0.05) {
            return;
        }
        int burst = Math.max(1, (Integer)this.hitRiseBurstPackets.get());
        double step = Math.max(0.5, Math.min(8.0, (Double)this.hitRiseStep.get()));
        this.withPacketBypass(() -> {
            double nextY;
            double remaining;
            for (int i = 0; i < burst && !((remaining = targetY - this.currentServerY) <= 0.05) && this.sendTrackedPosition(this.serverX, nextY = this.clampWorldY(this.currentServerY + Math.min(step, remaining)), this.serverZ, true); ++i) {
            }
        });
        this.extraNoFallTicks = Math.max(this.extraNoFallTicks, 24);
        this.movementCooldownTicks = 0;
    }

    private void applyActionOutcome(QueuedAction action) {
        if (action.outcome == PendingOutcome.HoldMining) {
            this.holdMode = HoldMode.Mining;
            return;
        }
        if (action.outcome == PendingOutcome.HoldContainer) {
            this.holdMode = HoldMode.Container;
            this.containerGraceTicksLeft = Math.max(1, (Integer)this.containerGraceTicks.get());
            this.containerScreenSeen = this.mc.gui.screen() instanceof AbstractContainerScreen;
            return;
        }
        if (action.postActionHoldTicks > 0) {
            this.holdMode = HoldMode.PostAction;
            this.postActionHoldTicksLeft = action.postActionHoldTicks;
        } else {
            this.holdMode = HoldMode.None;
        }
    }

    private void updateHoldState() {
        if (this.mc.player == null || this.mc.level == null) {
            return;
        }
        if (this.holdMode == HoldMode.Mining) {
            if (this.miningHoldActive && this.miningHoldFailsafeTicks > 0) {
                --this.miningHoldFailsafeTicks;
            }
            if (this.miningHoldActive && this.miningHoldFailsafeTicks <= 0) {
                this.miningHoldActive = false;
            }
            if (!this.miningHoldActive && this.activeAction == null) {
                this.holdMode = HoldMode.None;
            }
        }
        if (this.holdMode == HoldMode.Container) {
            if (this.mc.gui.screen() instanceof AbstractContainerScreen) {
                this.containerScreenSeen = true;
            } else if (!this.containerScreenSeen) {
                if (this.containerGraceTicksLeft > 0) {
                    --this.containerGraceTicksLeft;
                } else {
                    this.holdMode = HoldMode.None;
                }
            } else {
                this.holdMode = HoldMode.None;
            }
        }
        if (this.holdMode == HoldMode.Collecting) {
            if (((Boolean)this.collectUntilClear.get()).booleanValue()) {
                if (!this.hasNearbyCollectibles()) {
                    this.holdMode = HoldMode.None;
                }
            } else {
                if (this.hasNearbyCollectibles()) {
                    this.collectBurstTicksLeft = Math.max(this.collectBurstTicksLeft, (Integer)this.collectBurstTicks.get());
                }
                if (this.collectBurstTicksLeft > 0) {
                    --this.collectBurstTicksLeft;
                } else {
                    this.holdMode = HoldMode.None;
                }
            }
        }
        if (this.holdMode == HoldMode.PostAction) {
            if (this.postActionHoldTicksLeft > 0) {
                --this.postActionHoldTicksLeft;
            }
            if (this.postActionHoldTicksLeft <= 0) {
                this.holdMode = HoldMode.None;
            }
        }
        if (this.holdMode == HoldMode.None && this.activeAction == null && this.actionQueue.isEmpty() && ((Boolean)this.collectItems.get()).booleanValue() && this.hasNearbyCollectibles()) {
            this.holdMode = HoldMode.Collecting;
            this.collectBurstTicksLeft = Math.max(1, (Integer)this.collectBurstTicks.get());
        }
    }

    private void updateMiningHoldSafety() {
        boolean noInputExpired;
        if (this.holdMode != HoldMode.Mining || this.mc.player == null) {
            return;
        }
        int releaseTicks = Math.max(2, (Integer)this.miningNoInputReleaseTicksSetting.get());
        boolean attackPressed = this.isAttackKeyPressed();
        if (attackPressed) {
            this.miningNoInputReleaseTicks = Math.max(this.miningNoInputReleaseTicks, releaseTicks);
        } else if (this.miningNoInputReleaseTicks > 0) {
            --this.miningNoInputReleaseTicks;
        }
        int silenceTicks = Math.max(4, (Integer)this.miningPacketSilenceTicks.get());
        boolean packetSilenceExpired = this.lastMiningPacketAge >= 0 && this.mc.player.tickCount > this.lastMiningPacketAge + silenceTicks;
        boolean bl = noInputExpired = !attackPressed && this.miningNoInputReleaseTicks <= 0;
        if ((packetSilenceExpired || noInputExpired) && this.activeAction == null) {
            this.miningHoldActive = false;
            this.miningHoldFailsafeTicks = 0;
            this.holdMode = HoldMode.None;
        }
    }

    private boolean isAttackKeyPressed() {
        return this.mc.options != null && this.mc.options.keyAttack != null && this.mc.options.keyAttack.isDown();
    }

    private boolean hasNearbyCollectibles() {
        if (this.mc.player == null || this.mc.level == null) {
            return false;
        }
        double rangeSq = (Double)this.collectRange.get() * (Double)this.collectRange.get();
        for (Entity entity : ((meteordevelopment.meteorclient.mixin.LevelAccessor) this.mc.level).meteor$getEntityLookup().getAll()) {
            if (!(entity instanceof ItemEntity) && !(entity instanceof ExperienceOrb) || entity.isRemoved() || !(entity.distanceToSqr((Entity)this.mc.player) <= rangeSq)) continue;
            return true;
        }
        return false;
    }

    private void sendPermanentNoFall(double realX, double realY, double realZ) {
        boolean needsAssist;
        if (this.mc.player == null || this.mc.getConnection() == null) {
            return;
        }
        boolean vclipFalling = this.vclipAttemptPending && this.pendingVClipTargetY < this.pendingVClipStartY;
        double desyncY = Math.abs(this.currentServerY - realY);
        boolean emergencyAssist = this.mc.player.fallDistance >= 2.5 || this.mc.player.getDeltaMovement().y <= -0.3;
        boolean aggressiveAssist = (Boolean)this.aggressiveNoFall.get() != false && desyncY >= (Double)this.noFallDesyncThreshold.get();
        boolean bl = needsAssist = this.extraNoFallTicks > 0 || this.mc.player.fallDistance > (double)0.8f || this.mc.player.getDeltaMovement().y < -0.08 || this.mc.player.hurtTime > 0 || vclipFalling || aggressiveAssist || emergencyAssist;
        if (needsAssist && (emergencyAssist || this.noFallAssistCooldownTicks <= 0)) {
            boolean horizontalCollision = this.mc.player.horizontalCollision;
            this.withPacketBypass(() -> {
                ServerboundMovePlayerPacket.StatusOnly packet = new ServerboundMovePlayerPacket.StatusOnly(true, horizontalCollision);
                if (!this.trySendPacketNow((Packet<?>)packet)) {
                    this.sendTrackedPosition(this.serverX, this.currentServerY, this.serverZ, true);
                }
            });
            if (emergencyAssist) {
                this.noFallAssistCooldownTicks = 0;
                this.extraNoFallTicks = Math.max(this.extraNoFallTicks, 32);
            } else {
                this.noFallAssistCooldownTicks = Math.max(0, (Integer)this.noFallAssistInterval.get() - 1);
            }
        }
        if (this.extraNoFallTicks > 0) {
            --this.extraNoFallTicks;
        }
        this.mc.player.fallDistance = 0.0;
    }

    private void doAntiKickPulse(double realX, double realY, double realZ) {
        if (!((Boolean)this.antiKickPulse.get()).booleanValue()) {
            return;
        }
        if (this.antiKickTicksLeft > 0) {
            --this.antiKickTicksLeft;
        }
        if (this.antiKickOffTicksLeft > 0) {
            --this.antiKickOffTicksLeft;
        }
        if (this.antiKickTicksLeft <= 0 && this.antiKickOffTicksLeft <= 0) {
            this.antiKickTicksLeft = Math.max(1, (Integer)this.antiKickDelayTicks.get());
            this.antiKickOffTicksLeft = Math.max(1, (Integer)this.antiKickOffTicks.get());
        }

        if (this.antiKickOffTicksLeft > 0 && this.mc.getConnection() != null && this.mc.player != null) {
            double step = Math.max(0.01, Math.min((Double)this.antiKickDownStep.get(), 0.4));
            double pulseY = this.currentServerY - step;
            this.withPacketBypass(() -> {
                this.sendTrackedPosition(this.serverX, pulseY, this.serverZ, true);
            });
        }
    }

    private boolean travelToward(double targetY, TravelMode mode, double x, double z) {
        boolean horizontalOnly;
        double clampedTarget = this.clampWorldY(targetY);
        double dx = x - this.serverX;
        double dz = z - this.serverZ;
        double delta = clampedTarget - this.currentServerY;
        boolean bl = horizontalOnly = Math.abs(delta) <= 0.05;
        if (horizontalOnly && dx * dx + dz * dz <= 9.0E-4) {
            return true;
        }
        if (this.movementCooldownTicks > 0) {
            return false;
        }
        double nextY = clampedTarget;
        if (!horizontalOnly) {
            if (mode == TravelMode.Smooth) {
                double smoothStep = Math.max(0.25, (Double)this.smoothSpeed.get());
                nextY = this.currentServerY + Math.copySign(Math.min(Math.abs(delta), smoothStep), delta);
            } else {
                double paperLimit = (Boolean)this.paperOptimized.get() != false ? Math.max(0.25, (Double)this.paperMaxPacketStep.get()) : 8.0;
                double maxStep = Math.min(8.0, Math.min(this.getVclipMaxStepValue(), this.adaptiveVClipStep));
                double step = Math.max(0.25, Math.min(maxStep, paperLimit));
                if (this.correctionFreezeTicks > 0) {
                    step = Math.min(step, 1.0);
                }
                nextY = this.currentServerY + Math.copySign(Math.min(Math.abs(delta), step), delta);
            }
        }
        double previousServerY = this.currentServerY;
        boolean sent = !horizontalOnly && mode == TravelMode.VClip ? this.sendVClipBurst(x, nextY, z) : this.sendTrackedPosition(x, nextY, z, true);
        if (!sent) {
            this.movementCooldownTicks = Math.max(this.movementCooldownTicks, 1);
            return false;
        }
        if (!horizontalOnly && mode == TravelMode.VClip) {
            this.vclipAttemptPending = true;
            this.pendingVClipStartY = previousServerY;
            this.pendingVClipTargetY = this.currentServerY;
            this.pendingVClipTicks = 3;
        }
        this.movementCooldownTicks = this.getStepCooldown(mode, horizontalOnly);
        return Math.abs(nextY - clampedTarget) <= 0.01;
    }

    private int getStepCooldown(TravelMode mode, boolean horizontalOnly) {
        if (horizontalOnly || mode == TravelMode.Smooth) {
            return Math.max(0, (Integer)this.smoothCooldownTicks.get());
        }
        return Math.max(0, (Integer)this.vclipCooldownTicks.get());
    }

    private TravelMode getSafeTravelMode() {
        TravelMode configured = (TravelMode)this.movementMode.get();
        if (configured == TravelMode.VClip && this.vclipDamageSafetyTicks > 0) {
            return TravelMode.Smooth;
        }
        return configured;
    }

    private void updateVClipAttemptState() {
        if (!this.vclipAttemptPending) {
            return;
        }
        if (this.pendingVClipTicks > 0) {
            --this.pendingVClipTicks;
        }
        if (this.pendingVClipTicks > 0) {
            return;
        }
        this.markVClipAttemptSuccess();
        this.clearPendingVClipAttempt();
    }

    private void evaluateVClipCorrection(Vec3 packetPos) {
        boolean closerToStart;
        boolean bl = closerToStart = Math.abs(packetPos.y - this.pendingVClipStartY) + 0.05 < Math.abs(packetPos.y - this.pendingVClipTargetY);
        if (closerToStart) {
            ++this.vclipFailureCount;
            this.adaptiveVClipStep = Math.max(this.getVclipMinStepValue(), this.adaptiveVClipStep * 0.5);
            int baseBackoff = (Boolean)this.paperOptimized.get() != false ? Math.max((Integer)this.vclipFailureBackoffTicks.get(), (Integer)this.paperFailureBackoffTicks.get()) : (Integer)this.vclipFailureBackoffTicks.get();
            int backoff = baseBackoff + Math.min(20, this.vclipFailureCount * 3);
            this.movementCooldownTicks = Math.max(this.movementCooldownTicks, backoff);
            this.correctionFreezeTicks = Math.max(this.correctionFreezeTicks, Math.min(40, backoff));
        } else {
            this.markVClipAttemptSuccess();
        }
        this.clearPendingVClipAttempt();
    }

    private void markVClipAttemptSuccess() {
        double maxStep = this.getVclipMaxStepValue();
        double growth = Math.max(0.5, maxStep * 0.15);
        this.adaptiveVClipStep = Math.min(maxStep, this.adaptiveVClipStep + growth);
        this.vclipFailureCount = 0;
    }

    private void clearPendingVClipAttempt() {
        this.vclipAttemptPending = false;
        this.pendingVClipStartY = Double.NaN;
        this.pendingVClipTargetY = Double.NaN;
        this.pendingVClipTicks = 0;
    }

    private void resetVClipState() {
        this.adaptiveVClipStep = this.getVclipMaxStepValue();
        this.vclipFailureCount = 0;
        this.clearPendingVClipAttempt();
    }

    private boolean sendVClipBurst(double x, double y, double z) {
        if (this.mc.player == null || this.mc.getConnection() == null) {
            return false;
        }
        int burst = Math.max(1, (Integer)this.vclipBurstPackets.get());
        int confirm = (Boolean)this.paperOptimized.get() != false ? Math.max(0, (Integer)this.paperVclipConfirmPackets.get()) : 0;
        double previousY = this.currentServerY;
        boolean sentPrimary = this.sendTrackedPosition(x, y, z, true);
        if (!sentPrimary) {
            return false;
        }
        if (y < previousY - 0.05) {
            confirm = Math.max(confirm, 1);
        }
        if (this.vclipDamageSafetyTicks > 0) {
            confirm = Math.max(confirm, 2);
        }
        int repeats = Math.max(0, Math.min(4, burst - 1 + confirm));
        for (int i = 0; i < repeats && this.trySendPacketNow((Packet<?>)new ServerboundMovePlayerPacket.StatusOnly(true, this.mc.player.horizontalCollision)); ++i) {
        }
        return true;
    }

    private double getVclipMaxStepValue() {
        return Math.min(8.0, Math.max(1.0, (Double)this.vclipStep.get()));
    }

    private double getVclipMinStepValue() {
        return Math.max(0.5, Math.min((Double)this.vclipMinStep.get(), this.getVclipMaxStepValue()));
    }

    private double getEffectiveMaxYStep() {
        double configured = Math.min(8.0, Math.max(0.5, (Double)this.maxPacketYStep.get()));
        if (((Boolean)this.paperOptimized.get()).booleanValue()) {
            configured = Math.min(configured, Math.max(0.5, (Double)this.paperMaxPacketStep.get()));
        }
        return configured;
    }

    private int getActionSyncPacketCount(double actionY) {
        int configured = Math.max(1, (Integer)this.actionSyncPackets.get());
        double maxStep = Math.max(0.5, this.getEffectiveMaxYStep());
        int required = (int)Math.ceil(Math.abs(this.currentServerY - actionY) / maxStep);
        int desired = Math.max(configured, required);
        desired = Math.min(12, Math.max(1, desired));
        int budget = this.getPacketBudgetRemaining();
        if (budget <= 0) {
            return 0;
        }
        int reserveForAction = (Boolean)this.actionPostSync.get() != false ? 2 : 1;
        int available = Math.max(1, budget - reserveForAction);
        return Math.max(0, Math.min(desired, available));
    }

    private boolean sendTrackedPosition(double x, double y, double z, boolean onGround) {
        double clampedY;
        ServerboundMovePlayerPacket.Pos packet;
        if (this.mc.player == null || this.mc.getConnection() == null) {
            return false;
        }
        double targetY = this.clampWorldY(y);
        double maxYStep = this.getEffectiveMaxYStep();
        double maxXZStep = Math.min(3.0, Math.max(0.25, (Double)this.maxPacketXZStep.get()));
        double dx = x - this.serverX;
        double dz = z - this.serverZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double sendX = x;
        double sendZ = z;
        if (horizontal > maxXZStep && horizontal > 1.0E-4) {
            double scale = maxXZStep / horizontal;
            sendX = this.serverX + dx * scale;
            sendZ = this.serverZ + dz * scale;
        }
        double dy = targetY - this.currentServerY;
        double sendY = targetY;
        if (Math.abs(dy) > maxYStep) {
            sendY = this.currentServerY + Math.copySign(maxYStep, dy);
        }

        if (this.antiKickOffTicksLeft > 0 && this.mc.player != null && !this.mc.player.onGround()) {
            boolean ascendingOrFlat = sendY >= this.currentServerY;
            boolean dropTooSmall = this.currentServerY - sendY < (Double) this.antiKickDownStep.get();
            if (ascendingOrFlat || dropTooSmall) {
                sendY = Math.min(sendY, this.currentServerY - (Double) this.antiKickDownStep.get());
            }
        }
        if (!this.trySendPacketNow((Packet<?>)(packet = new ServerboundMovePlayerPacket.Pos(sendX, clampedY = this.clampWorldY(sendY), sendZ, onGround, this.mc.player.horizontalCollision)))) {
            this.consecutiveSendFailures = Math.min(36, this.consecutiveSendFailures + 1);
            return false;
        }
        this.serverX = sendX;
        this.serverZ = sendZ;
        this.currentServerY = clampedY;
        this.consecutiveSendFailures = 0;
        return true;
    }

    private boolean sendPacket(Packet<?> packet) {
        if (this.mc.getConnection() == null) {
            return false;
        }
        if (this.trySendPacketNow(packet)) {
            return true;
        }
        this.bufferPacket(packet);
        return false;
    }

    private void sendDirectResyncPackets(double x, double y, double z, int count) {
        if (this.mc.player == null || this.mc.getConnection() == null) {
            return;
        }
        double clampedY = this.clampWorldY(y);
        int packets = Math.max(1, count);
        for (int i = 0; i < packets; ++i) {
            ServerboundMovePlayerPacket.Pos packet = new ServerboundMovePlayerPacket.Pos(x, clampedY, z, true, this.mc.player.horizontalCollision);
            this.mc.getConnection().send((Packet)packet);
        }
    }

    private void withPacketBypass(Runnable action) {
        boolean previous = this.sendingPackets;
        this.sendingPackets = true;
        try {
            action.run();
        }
        finally {
            this.sendingPackets = previous;
        }
    }

    private boolean trySendPacketNow(Packet<?> packet) {
        if (this.mc.getConnection() == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        this.trimOldPacketTimes(now);
        if (this.sentPacketTimesMs.size() >= 300) {
            return false;
        }
        if (packet instanceof IServerboundMovePlayerPacket) {
            IServerboundMovePlayerPacket taggedPacket = (IServerboundMovePlayerPacket)packet;
            taggedPacket.meteor$setTag(1337);
        }
        this.mc.getConnection().send(packet);
        this.sentPacketTimesMs.addLast(now);
        return true;
    }

    private void flushBufferedPackets() {
        Packet<?> packet;
        if (this.bufferedPackets.isEmpty()) {
            return;
        }
        if (this.mc.getConnection() == null) {
            this.bufferedPackets.clear();
            return;
        }
        for (int flushed = 0; flushed < 12 && !this.bufferedPackets.isEmpty() && this.trySendPacketNow(packet = this.bufferedPackets.peekFirst()); ++flushed) {
            this.bufferedPackets.pollFirst();
        }
    }

    private void bufferPacket(Packet<?> packet) {
        while (this.bufferedPackets.size() >= 256) {
            this.bufferedPackets.pollFirst();
        }
        this.bufferedPackets.addLast(packet);
    }

    private void trimOldPacketTimes(long now) {
        long age;
        while (!this.sentPacketTimesMs.isEmpty() && (age = now - this.sentPacketTimesMs.peekFirst()) >= 5000L) {
            this.sentPacketTimesMs.pollFirst();
        }
    }

    private int getPacketBudgetRemaining() {
        this.trimOldPacketTimes(System.currentTimeMillis());
        return Math.max(0, 300 - this.sentPacketTimesMs.size());
    }

    private void clearPacketBudgetState() {
        this.bufferedPackets.clear();
        this.sentPacketTimesMs.clear();
    }

    private void hardResyncToReal(double x, double y, double z, boolean clearEscapeOverride) {
        this.clearActionState();
        this.resetHoldState();
        this.resetVClipState();
        if (clearEscapeOverride) {
            this.escapeOverrideActive = false;
            this.escapeBurstPending = false;
            this.escapeBurstRetriesLeft = 0;
            this.escapeBurstRetryTicks = 0;
        }
        this.serverX = x;
        this.serverZ = z;
        this.currentServerY = this.clampWorldY(y);
        this.correctionFreezeTicks = 0;
        this.correctionWindowTicks = 0;
        this.correctionStrikes = 0;
        this.consecutiveSendFailures = 0;
        this.movementCooldownTicks = Math.max(this.movementCooldownTicks, 2);
        this.extraNoFallTicks = Math.max(this.extraNoFallTicks, 16);
        this.noFallAssistCooldownTicks = 0;
    }

    private void handleDeadState(double x, double y, double z) {
        this.hardResyncToReal(x, y, z, true);
        if (this.mc.player != null) this.mc.player.fallDistance = 0.0;
        this.sawDeath = true;
        this.respawnGraceTicks = Math.max(this.respawnGraceTicks, 40);
    }

    private void resetRuntimeState() {
        this.clearActionState();
        this.resetHoldState();
        this.resetVClipState();
        this.clearPacketBudgetState();
        this.movementCooldownTicks = 0;
        this.antiKickTicksLeft = 0;
        this.antiKickOffTicksLeft = 0;
        this.noFallAssistCooldownTicks = 0;
        this.extraNoFallTicks = 0;
        this.correctionWindowTicks = 0;
        this.correctionStrikes = 0;
        this.correctionFreezeTicks = 0;
        this.consecutiveSendFailures = 0;
        this.vclipDamageSafetyTicks = 0;
        this.respawnGraceTicks = 0;
        this.miningHoldFailsafeTicks = 0;
        this.miningNoInputReleaseTicks = 0;
        this.lastMiningPacketAge = -1;
        this.lastCreativeInstantActionAge = -1;
        this.escapeBurstPending = false;
        this.escapeBurstRetriesLeft = 0;
        this.escapeBurstRetryTicks = 0;
        this.sawDeath = false;
        this.lastPlayerEntityId = Integer.MIN_VALUE;
        this.lastPlayerAge = -1;
    }

    private void handleAcceptedCorrection(Vec3 packetPos) {
        this.serverX = packetPos.x;
        this.serverZ = packetPos.z;
        this.currentServerY = this.clampWorldY(packetPos.y);
        this.movementCooldownTicks = Math.max(this.movementCooldownTicks, 1);
    }

    private boolean isCorrectionNearReal(Vec3 packetPos, double realX, double realY, double realZ) {
        double dx = packetPos.x - realX;
        double dz = packetPos.z - realZ;
        double horizSq = dx * dx + dz * dz;
        double dy = Math.abs(packetPos.y - realY);
        return horizSq <= 4.0 && dy <= 3.0;
    }

    private boolean isCorrectionFarFromTracked(Vec3 packetPos) {
        double dx = packetPos.x - this.serverX;
        double dz = packetPos.z - this.serverZ;
        double horizSq = dx * dx + dz * dz;
        double dy = Math.abs(packetPos.y - this.currentServerY);
        return horizSq >= 64.0 || dy >= 12.0;
    }

    private boolean isCorrectionNearTracked(Vec3 packetPos) {
        double dx = packetPos.x - this.serverX;
        double dz = packetPos.z - this.serverZ;
        double horizSq = dx * dx + dz * dz;
        double dy = Math.abs(packetPos.y - this.currentServerY);
        return horizSq <= 16.0 && dy <= 6.0;
    }

    private Vec3 resolveEntityActionTarget(ServerboundInteractPacket packet) {
        double backZ;
        Entity targetEntity = this.resolvePacketEntity(packet);
        Vec3 fallback = this.resolveEntityTarget(targetEntity);
        if (!((Boolean)this.avoidHitFov.get()).booleanValue() || targetEntity == null || this.mc.player == null) {
            return fallback;
        }
        double yawRad = Math.toRadians(targetEntity.getYRot());
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double backX = -forwardX;
        double sideX = backZ = -forwardZ;
        double sideZ = -backX;
        double behind = Math.max(0.0, (Double)this.hitBehindOffset.get());
        double side = Math.max(0.0, (Double)this.hitSideOffset.get());
        double y = this.mc.player.getY();
        Vec3 center = targetEntity.getBoundingBox().getCenter();
        Vec3 candidateRight = new Vec3(center.x + backX * behind + sideX * side, y, center.z + backZ * behind + sideZ * side);
        Vec3 candidateLeft = new Vec3(center.x + backX * behind - sideX * side, y, center.z + backZ * behind - sideZ * side);
        Vec3 candidateBack = new Vec3(center.x + backX * (behind + 0.8), y, center.z + backZ * (behind + 0.8));
        Vec3 best = this.pickBestStealthHitPosition(targetEntity, fallback, candidateRight, candidateLeft, candidateBack);
        return this.isFiniteVec(best) ? best : fallback;
    }

    private Vec3 pickBestStealthHitPosition(Entity targetEntity, Vec3 fallback, Vec3 ... candidates) {
        if (this.mc.player == null || targetEntity == null) {
            return fallback;
        }
        Vec3 best = fallback;
        double bestScore = this.scoreStealthHitPosition(targetEntity, fallback);
        for (Vec3 candidate : candidates) {
            double score = this.scoreStealthHitPosition(targetEntity, candidate);
            if (!(score < bestScore)) continue;
            best = candidate;
            bestScore = score;
        }
        return best;
    }

    private double scoreStealthHitPosition(Entity targetEntity, Vec3 candidate) {
        double toZ;
        if (this.mc.player == null || targetEntity == null) {
            return Double.POSITIVE_INFINITY;
        }
        if (!this.isFiniteVec(candidate)) {
            return Double.POSITIVE_INFINITY;
        }
        double y = this.mc.player.getY();
        double collisionPenalty = this.isTeleportPositionSafe(candidate.x, y, candidate.z) ? 0.0 : 1000.0;
        double toX = candidate.x - targetEntity.getX();
        double horizontalSq = toX * toX + (toZ = candidate.z - targetEntity.getZ()) * toZ;
        if (horizontalSq < 1.0E-6) {
            return collisionPenalty + 1000.0;
        }
        double horizontal = Math.sqrt(horizontalSq);
        double dirX = toX / horizontal;
        double dirZ = toZ / horizontal;
        double yawRad = Math.toRadians(targetEntity.getYRot());
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double frontDot = Math.max(0.0, forwardX * dirX + forwardZ * dirZ);
        double frontPenalty = frontDot * 10.0;
        double travel = Math.sqrt((candidate.x - this.serverX) * (candidate.x - this.serverX) + (candidate.z - this.serverZ) * (candidate.z - this.serverZ));
        double travelPenalty = travel * 0.2;
        return collisionPenalty + frontPenalty + travelPenalty;
    }

    private Vec3 resolveEntityTarget(Entity preferred) {
        if (this.mc.player == null || this.mc.level == null) {
            return this.mc.player != null ? new Vec3(this.mc.player.getX(), this.mc.player.getY(), this.mc.player.getZ()) : Vec3.ZERO;
        }
        if (preferred != null && !preferred.isRemoved()) {
            return preferred.getBoundingBox().getCenter();
        }
        if (this.mc.crosshairPickEntity != null) {
            return this.mc.crosshairPickEntity.getBoundingBox().getCenter();
        }
        Entity closest = null;
        double closestSq = 36.0;
        for (Entity entity : ((meteordevelopment.meteorclient.mixin.LevelAccessor) this.mc.level).meteor$getEntityLookup().getAll()) {
            double distSq;
            if (entity == this.mc.player || entity.isRemoved() || (distSq = entity.distanceToSqr((Entity)this.mc.player)) > closestSq) continue;
            closestSq = distSq;
            closest = entity;
        }
        return closest != null ? closest.getBoundingBox().getCenter() : new Vec3(this.mc.player.getX(), this.mc.player.getY(), this.mc.player.getZ());
    }

    private Vec3 resolveEntityTarget() {
        return this.resolveEntityTarget(null);
    }

    private Entity resolvePacketEntity(ServerboundInteractPacket packet) {
        Field field;
        if (this.mc.level == null || packet == null) {
            return this.mc.crosshairPickEntity;
        }
        Method method = this.getPacketGetEntityMethod();
        if (method != null) {
            try {
                Object result = method.invoke(packet, this.mc.level);
                if (result instanceof Entity) {
                    Entity entity = (Entity)result;
                    return entity;
                }
            }
            catch (Throwable result) {

            }
        }
        if ((field = this.getPacketEntityIdField()) != null) {
            try {
                int id = field.getInt(packet);
                Entity entity = this.mc.level.getEntity(id);
                if (entity != null) {
                    return entity;
                }
            }
            catch (Throwable throwable) {

            }
        }
        return this.mc.crosshairPickEntity;
    }

    private Method getPacketGetEntityMethod() {
        if (this.packetGetEntityLookupFailed) {
            return null;
        }
        if (this.packetGetEntityMethod != null) {
            return this.packetGetEntityMethod;
        }
        try {
            this.packetGetEntityMethod = ServerboundInteractPacket.class.getMethod("getEntity", Level.class);
            this.packetGetEntityMethod.setAccessible(true);
            return this.packetGetEntityMethod;
        }
        catch (Throwable ignored) {
            this.packetGetEntityLookupFailed = true;
            return null;
        }
    }

    private Field getPacketEntityIdField() {
        if (this.packetEntityIdLookupFailed) {
            return null;
        }
        if (this.packetEntityIdField != null) {
            return this.packetEntityIdField;
        }
        try {
            this.packetEntityIdField = ServerboundInteractPacket.class.getDeclaredField("entityId");
            this.packetEntityIdField.setAccessible(true);
            return this.packetEntityIdField;
        }
        catch (Throwable ignored) {
            this.packetEntityIdLookupFailed = true;
            return null;
        }
    }

    private boolean isContainerInteraction(BlockPos pos) {
        if (this.mc.level == null || pos == null) {
            return false;
        }
        return this.mc.level.getBlockState(pos).getMenuProvider((Level)this.mc.level, pos) != null;
    }

    private int getPlacePostActionTicks() {
        return (Boolean)this.confirmPlaceBeforeReturn.get() != false ? Math.max(0, (Integer)this.placeConfirmTicks.get()) : 0;
    }

    private int getHitPostActionTicks() {
        return (Boolean)this.confirmHitBeforeReturn.get() != false ? Math.max(0, (Integer)this.hitConfirmTicks.get()) : 0;
    }

    private double getDesiredAnchorY(double realY) {
        if (this.escapeOverrideActive) {
            return this.clampWorldY(this.escapeOverrideY);
        }
        double target = this.anchorMode.get() == AnchorMode.Fixed ? (double)((Integer)this.anchorY.get()).intValue() : realY + (Double)this.relativeAnchorOffset.get();
        double maxDelta = Math.max(8.0, (Double)this.maxAnchorDelta.get());
        target = this.clamp(target, realY - maxDelta, realY + maxDelta);
        return this.clampWorldY(target);
    }

    private Vec3 resolvePacketPosition(ClientboundPlayerPositionPacket packet) {
        if (this.mc.player == null) {
            return Vec3.ZERO;
        }
        Vec3 change = packet.change().position();
        double x = change.x + (packet.relatives().contains(Relative.X) ? this.mc.player.getX() : 0.0);
        double y = change.y + (packet.relatives().contains(Relative.Y) ? this.mc.player.getY() : 0.0);
        double z = change.z + (packet.relatives().contains(Relative.Z) ? this.mc.player.getZ() : 0.0);
        return new Vec3(x, y, z);
    }

    private boolean hasPlayerIdentityChanged() {
        if (this.mc.player == null) {
            return false;
        }
        if (this.lastPlayerEntityId == Integer.MIN_VALUE) {
            return true;
        }
        if (this.mc.player.getId() != this.lastPlayerEntityId) {
            return true;
        }
        return this.lastPlayerAge >= 0 && this.mc.player.tickCount < this.lastPlayerAge;
    }

    private boolean isPlayerDead() {
        return this.mc.player == null || this.mc.player.isDeadOrDying() || this.mc.player.getHealth() <= 0.0f || this.mc.player.deathTime > 0;
    }

    private void trackPlayerState() {
        if (this.mc.player == null) {
            this.lastPlayerEntityId = Integer.MIN_VALUE;
            this.lastPlayerAge = -1;
            return;
        }
        this.lastPlayerEntityId = this.mc.player.getId();
        this.lastPlayerAge = this.mc.player.tickCount;
    }

    private double clampWorldY(double y) {
        double max;
        if (this.mc.level == null) {
            return this.clamp(y, -4096.0, 4096.0);
        }
        double min = (double)this.mc.level.getMinY() + 1.0;
        if (min > (max = (double)this.mc.level.getMaxY() - 1.0)) {
            return y;
        }
        return this.clamp(y, min, max);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean isTeleportPositionSafe(double x, double y, double z) {
        if (this.mc.player == null || this.mc.level == null) {
            return false;
        }
        double minY = (double)this.mc.level.getMinY() + 1.0;
        double maxY = (double)this.mc.level.getMaxY() - 1.0;
        if (y < minY || y > maxY) {
            return false;
        }
        AABB movedBox = this.mc.player.getBoundingBox().move(x - this.mc.player.getX(), y - this.mc.player.getY(), z - this.mc.player.getZ());
        return !this.mc.level.getBlockCollisions((Entity)this.mc.player, movedBox).iterator().hasNext();
    }

    private boolean shouldTemporarilyBypassSpoofing() {
        if (this.mc.player == null) {
            return false;
        }
        return this.mc.player.isPassenger() || this.mc.player.isFallFlying() || this.mc.player.isAutoSpinAttack() || this.mc.player.isSleeping();
    }

    private void recoverFromSendStall(double x, double y, double z) {
        this.clearActionState();
        this.resetHoldState();
        this.resetVClipState();
        this.clearPacketBudgetState();
        this.hardResyncToReal(x, y, z, false);
        this.movementCooldownTicks = Math.max(this.movementCooldownTicks, 6);
        this.consecutiveSendFailures = 0;
    }

    private Vec3 sanitizeActionTarget(Vec3 target) {
        if (this.isFiniteVec(target)) {
            return target;
        }
        if (this.mc.player != null) {
            return new Vec3(this.mc.player.getX(), this.mc.player.getY(), this.mc.player.getZ());
        }
        return new Vec3(this.serverX, this.currentServerY, this.serverZ);
    }

    private boolean isDuplicateAction(QueuedAction queued) {
        if (this.activeAction != null && this.activeActionTicks <= 8 && this.isSameAction(this.activeAction, queued)) {
            return true;
        }
        QueuedAction tail = this.actionQueue.peekLast();
        return tail != null && this.isSameAction(tail, queued);
    }

    private boolean shouldSkipDuplicateCheck(QueuedAction queued) {
        if (queued == null || !this.isCreativePlayer()) {
            return false;
        }
        return queued.packet instanceof ServerboundUseItemOnPacket;
    }

    private boolean isSameAction(QueuedAction a, QueuedAction b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.packet.getClass() != b.packet.getClass()) {
            return false;
        }
        if (a.packet instanceof ServerboundPlayerActionPacket left && b.packet instanceof ServerboundPlayerActionPacket right) {
            if (left.getAction() != right.getAction()) {
                return false;
            }
        }
        if (a.packet instanceof ServerboundUseItemOnPacket left && b.packet instanceof ServerboundUseItemOnPacket right) {
            if (!left.getHitResult().getBlockPos().equals(right.getHitResult().getBlockPos())) {
                return false;
            }
            if (left.getHand() != right.getHand()) {
                return false;
            }
        }
        if (a.outcome != b.outcome) {
            return false;
        }
        if (a.teleportXZ != b.teleportXZ) {
            return false;
        }
        if (a.entityInteraction != b.entityInteraction) {
            return false;
        }
        if (a.postActionHoldTicks != b.postActionHoldTicks) {
            return false;
        }
        return a.target.distanceToSqr(b.target) <= 0.04;
    }

    private boolean isMiningActionPacket(Packet<?> packet) {
        if (!(packet instanceof ServerboundPlayerActionPacket)) {
            return false;
        }
        ServerboundPlayerActionPacket actionPacket = (ServerboundPlayerActionPacket)packet;
        return this.isMiningAction(actionPacket.getAction());
    }

    private boolean isFiniteVec(Vec3 vec) {
        return vec != null && Double.isFinite(vec.x) && Double.isFinite(vec.y) && Double.isFinite(vec.z);
    }

    private void clearActionState() {
        this.actionQueue.clear();
        this.activeAction = null;
        this.activeActionTicks = 0;
    }

    private void resetHoldState() {
        this.holdMode = HoldMode.None;
        this.miningHoldActive = false;
        this.miningHoldFailsafeTicks = 0;
        this.miningNoInputReleaseTicks = 0;
        this.lastMiningPacketAge = -1;
        this.collectBurstTicksLeft = 0;
        this.containerGraceTicksLeft = 0;
        this.containerScreenSeen = false;
        this.postActionHoldTicksLeft = 0;
    }
}
