package orbiter.systems.combat;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

import java.util.ArrayList;
import java.util.List;

public class CombatEngine {
    private static final CombatEngine INSTANCE = new CombatEngine();

    public static CombatEngine get() {
        return INSTANCE;
    }

    private final Minecraft mc = Minecraft.getInstance();
    private final ActionArbiter arbiter = ActionArbiter.get();
    private final RotationEngine rotations = new RotationEngine(mc);

    private final List<CombatRequest> submitted = new ArrayList<>();
    private boolean initialized;

    private CombatEngine() {
    }

    public void init() {
        if (initialized) return;
        initialized = true;
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    public ActionArbiter arbiter() {
        return arbiter;
    }

    public boolean isFrozen() {
        return arbiter.isFrozen();
    }

    public void freeze(Module owner) {
        arbiter.freeze(owner);
    }

    public void unfreeze(Module owner) {
        arbiter.unfreeze(owner);
    }

    public void submit(CombatRequest request) {
        if (request == null || request.owner() == null) return;
        submitted.add(request);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) {
            submitted.clear();
            rotations.stop();
            SilentRotationHandler.clear();
            return;
        }

        CombatRequest request = arbiter.pickWinner(submitted);
        submitted.clear();

        if (request == null || request.targetPoint() == null) {
            if (rotations.busy()) rotations.stop();
            SilentRotationHandler.clear();
            return;
        }

        double yaw = RotationEngine.yawTo(mc, request.targetPoint());
        Double pitchOverride = request.pitchOverride();
        double pitch = pitchOverride != null ? pitchOverride : RotationEngine.pitchTo(mc, request.targetPoint());
        rotations.begin(request, mc.player.getYRot(), mc.player.getXRot(), yaw, pitch);
        rotations.step(mc.level.getGameTime());
        rotations.apply();

        if (request.mode() == CombatRequest.Mode.Silent && SilentRotationHandler.isActive() && mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(
                mc.player.getYRot(), mc.player.getXRot(), mc.player.onGround(), mc.player.horizontalCollision));
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        arbiter.reset();
        submitted.clear();
        rotations.stop();
        SilentRotationHandler.clear();
    }
}
