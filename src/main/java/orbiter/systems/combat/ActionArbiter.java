package orbiter.systems.combat;

import meteordevelopment.meteorclient.systems.modules.Module;

import java.util.List;

public class ActionArbiter {
    private static final ActionArbiter INSTANCE = new ActionArbiter();

    public static ActionArbiter get() {
        return INSTANCE;
    }

    private Module freezeOwner;

    private ActionArbiter() {
    }

    public void freeze(Module owner) {
        freezeOwner = owner;
    }

    public void unfreeze(Module owner) {
        if (freezeOwner == owner) freezeOwner = null;
    }

    public boolean isFrozen() {
        return freezeOwner != null;
    }

    public boolean mayAct(Module module) {
        return freezeOwner == null || freezeOwner == module;
    }

    public CombatRequest pickWinner(List<CombatRequest> submitted) {
        CombatRequest top = null;

        for (CombatRequest request : submitted) {
            if (freezeOwner != null && request.owner() != freezeOwner) continue;
            if (!request.owner().isActive()) continue;
            if (top == null || request.priority() > top.priority()) top = request;
        }

        return top;
    }

    public void reset() {
        freezeOwner = null;
    }
}
