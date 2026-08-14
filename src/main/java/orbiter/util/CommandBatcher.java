package orbiter.util;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public final class CommandBatcher {
    public record Step(String owner, String dedupeKey, int delayTicks, String command) {}

    private static final int HARD_MAX_QUEUE = 4096;
    private final ArrayDeque<Step> queue = new ArrayDeque<>();
    private final Set<String> dedupeKeys = new HashSet<>();
    private int budgetPerTick;
    private int delay;

    public CommandBatcher(int budgetPerTick) {
        this.budgetPerTick = Math.max(1, Math.min(64, budgetPerTick));
    }

    public boolean offer(Step step) {
        if (step == null || step.command() == null || step.command().isBlank() || queue.size() >= HARD_MAX_QUEUE) return false;
        if (step.dedupeKey() != null && !dedupeKeys.add(step.dedupeKey())) return false;
        queue.addLast(step);
        return true;
    }

    public int drain(Consumer<String> sender) {
        if (sender == null || delay > 0) { if (delay > 0) delay--; return 0; }
        int sent = 0;
        while (sent < budgetPerTick && !queue.isEmpty()) {
            Step step = queue.removeFirst();
            if (step.dedupeKey() != null) dedupeKeys.remove(step.dedupeKey());
            sender.accept(step.command());
            sent++;
            delay = Math.max(0, step.delayTicks());
            if (delay > 0) break;
        }
        return sent;
    }

    public int cancelOwner(String owner) {
        if (owner == null) return 0;
        int removed = 0;
        var iterator = queue.iterator();
        while (iterator.hasNext()) {
            Step step = iterator.next();
            if (owner.equals(step.owner())) { iterator.remove(); if (step.dedupeKey() != null) dedupeKeys.remove(step.dedupeKey()); removed++; }
        }
        return removed;
    }

    public void clear() { queue.clear(); dedupeKeys.clear(); delay = 0; }
    public int size() { return queue.size(); }
    public void setBudgetPerTick(int budget) { budgetPerTick = Math.max(1, Math.min(64, budget)); }
    public static int hardMaxQueue() { return HARD_MAX_QUEUE; }
}
