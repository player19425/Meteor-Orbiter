package orbiter.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.math.Box;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class MultiCommand extends Command {

    private static final String HELP_TEXT = """
        §6[.multicommand Help]
        §7Usage: §r.multicommand {selector} command [; {selector} command ...]

        §7Selectors:
        §r  {all}           • every player on the server
        §r  {team:name}     • players in a specific scoreboard team
        §r  {near:blocks}   • players within X blocks
        §r  {random}        • 1 random player from the group
        §r  {random:N}      • N random players from the group
        §r  {nearest}       • single nearest player
        §r  {furthest}      • single furthest player
        §r  {team:Red,near:20} • AND-chaining (comma separated)

        §7Player placeholder §3(required)§7:
        §r  Use §b{player}§r in the command template at the exact position
        §r  where a player name would normally appear in the vanilla command.
        §r  The selector target is substituted at that position • not elsewhere.

        §7Examples (correct placement):
        §r  .multicommand {all} give {player} diamond
        §r  .multicommand {near:20} tp {player} 0 100 0
        §r  .multicommand {team:Red} say Team Red rocks!
        §r  .multicommand {random:3} kick {player} Bye ; {all} say Cleanup done.
        §r  .multicommand {all} tp {player} ~ 100 ~
        §r  .multicommand {nearest} tp {player} 0 64 0

        §7§cError: §rIf no §b{player}§r placeholder is found, the command is
        §r  skipped with a warning • the placeholder is required so the
        §r  player name is always injected at the correct argument position.
        """;

    public MultiCommand() {
        super("multicommand", "Run commands targeting multiple players via selectors.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("help")
            .executes(context -> {
                for (String line : HELP_TEXT.split("\n")) {
                    info(line);
                }
                return SINGLE_SUCCESS;
            }));

        builder.then(argument("args", StringArgumentType.greedyString())
            .suggests(this::suggestInputs)
            .executes(context -> {
                if (mc.player == null || mc.world == null) {
                    error("Not connected to a server.");
                    return SINGLE_SUCCESS;
                }

                String raw = StringArgumentType.getString(context, "args").trim();
                if (raw.isEmpty()) {
                    info("Usage: .multicommand {selector} command [; {selector} command ...]");
                    info("Use .multicommand help for detailed usage.");
                    return SINGLE_SUCCESS;
                }

                executeMulti(raw);
                return SINGLE_SUCCESS;
            }));

        builder.executes(context -> {
            info("Usage: .multicommand {selector} command [; {selector} command ...]");
            info("Use .multicommand help for detailed usage.");
            return SINGLE_SUCCESS;
        });
    }

    private void executeMulti(String raw) {
        String[] segments = raw.split(";");
        int totalCommands = 0;

        for (String segment : segments) {
            segment = segment.trim();
            if (segment.isEmpty()) continue;

            int selectorEnd = findSelectorEnd(segment);
            if (selectorEnd == -1) {
                error("Invalid segment (no selector found): " + segment);
                continue;
            }

            String selectorRaw = segment.substring(0, selectorEnd).trim();
            String commandTemplate = segment.substring(selectorEnd).trim();

            if (commandTemplate.isEmpty()) {
                error("Missing command after selector: " + selectorRaw);
                continue;
            }

            List<String> targets = resolveSelector(selectorRaw);
            if (targets.isEmpty()) {
                info("No targets matched for selector: " + selectorRaw);
                continue;
            }

            for (String playerName : targets) {
                String finalCommand = buildCommand(commandTemplate, playerName);
                if (finalCommand == null) {
                    error("No {player} placeholder in command: " + commandTemplate);
                    error("The {player} placeholder is required so the player name is placed at the correct argument position.");
                    break;
                }
                sendCommand(finalCommand);
                totalCommands++;
            }
        }

        info("Sent §6" + totalCommands + "§r command(s).");
    }

    private int findSelectorEnd(String segment) {
        if (!segment.startsWith("{")) return -1;
        int depth = 0;
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i + 1;
            }
        }
        return -1;
    }

    private String buildCommand(String template, String playerName) {
        if (template.contains("{player}")) {
            return template.replace("{player}", playerName);
        }

        return null;
    }

    private void sendCommand(String command) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        if (command.startsWith("/")) command = command.substring(1);
        mc.getNetworkHandler().sendChatCommand(command);
    }

    private List<String> resolveSelector(String raw) {
        String content = raw.trim();
        if (content.startsWith("{") && content.endsWith("}")) {
            content = content.substring(1, content.length() - 1);
        }

        String[] parts = content.split(",");
        Set<String> result = null;

        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;
            Set<String> current = resolveSingleSelector(part);
            if (result == null) {
                result = new LinkedHashSet<>(current);
            } else {
                result.retainAll(current);
            }
        }

        if (result == null) result = Collections.emptySet();
        return new ArrayList<>(result);
    }

    private Set<String> resolveSingleSelector(String selector) {
        if (selector.equalsIgnoreCase("all")) {
            return getAllPlayers();
        }
        if (selector.equalsIgnoreCase("nearest") || selector.equalsIgnoreCase("closest")) {
            return getNearestPlayer();
        }
        if (selector.equalsIgnoreCase("furthest") || selector.equalsIgnoreCase("farthest")) {
            return getFurthestPlayer();
        }
        if (selector.toLowerCase().startsWith("team:")) {
            String teamName = selector.substring(5).trim();
            return getTeamPlayers(teamName);
        }
        if (selector.toLowerCase().startsWith("near:")) {
            try {
                double radius = Double.parseDouble(selector.substring(5).trim());
                return getNearbyPlayers(radius);
            } catch (NumberFormatException e) {
                return Collections.emptySet();
            }
        }
        if (selector.toLowerCase().startsWith("random")) {
            int count = 1;
            if (selector.contains(":")) {
                try {
                    count = Integer.parseInt(selector.substring(selector.indexOf(':') + 1).trim());
                    count = Math.max(1, count);
                } catch (NumberFormatException ignored) {
                }
            }
            return getRandomPlayers(count);
        }

        if (mc.world != null && mc.world.getPlayers().stream().anyMatch(p -> p.getName().getString().equalsIgnoreCase(selector))) {
            return Set.of(selector);
        }
        return Collections.emptySet();
    }

    private Set<String> getAllPlayers() {
        if (mc.world == null) return Collections.emptySet();
        return mc.world.getPlayers().stream()
            .map(p -> p.getName().getString())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> getTeamPlayers(String teamName) {
        if (mc.world == null || mc.world.getScoreboard() == null) return Collections.emptySet();
        Team team = mc.world.getScoreboard().getTeam(teamName);
        if (team == null) return Collections.emptySet();
        return mc.world.getPlayers().stream()
            .filter(p -> team.getPlayerList().contains(p.getName().getString()))
            .map(p -> p.getName().getString())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> getNearbyPlayers(double radius) {
        if (mc.player == null || mc.world == null) return Collections.emptySet();
        Box box = mc.player.getBoundingBox().expand(radius);
        return mc.world.getEntitiesByClass(PlayerEntity.class, box, p -> p != mc.player).stream()
            .map(p -> p.getName().getString())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> getRandomPlayers(int count) {
        List<String> all = new ArrayList<>(getAllPlayers());
        if (all.isEmpty()) return Collections.emptySet();
        Collections.shuffle(all);
        return new LinkedHashSet<>(all.subList(0, Math.min(count, all.size())));
    }

    private Set<String> getNearestPlayer() {
        if (mc.player == null || mc.world == null) return Collections.emptySet();
        return mc.world.getEntitiesByClass(PlayerEntity.class,
                mc.player.getBoundingBox().expand(2048), p -> p != mc.player).stream()
            .min((a, b) -> Double.compare(a.distanceTo(mc.player), b.distanceTo(mc.player)))
            .map(p -> Set.of(p.getName().getString()))
            .orElse(Collections.emptySet());
    }

    private Set<String> getFurthestPlayer() {
        if (mc.player == null || mc.world == null) return Collections.emptySet();
        return mc.world.getEntitiesByClass(PlayerEntity.class,
                mc.player.getBoundingBox().expand(2048), p -> p != mc.player).stream()
            .max((a, b) -> Double.compare(a.distanceTo(mc.player), b.distanceTo(mc.player)))
            .map(p -> Set.of(p.getName().getString()))
            .orElse(Collections.emptySet());
    }

    private CompletableFuture<Suggestions> suggestInputs(com.mojang.brigadier.context.CommandContext<CommandSource> ctx, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();

        if (remaining.endsWith("{") || remaining.isEmpty() || remaining.endsWith(";")) {
            builder.suggest("{all}");
            builder.suggest("{near:}");
            builder.suggest("{team:}");
            builder.suggest("{random}");
            builder.suggest("{random:3}");
            builder.suggest("{nearest}");
            builder.suggest("{furthest}");
        }

        int teamIdx = remaining.lastIndexOf("{team:");
        if (teamIdx != -1 && !remaining.substring(teamIdx).contains("}")) {
            String prefix = remaining.substring(teamIdx + 6);
            suggestTeams(builder, prefix);
        }

        if (remaining.contains(" ") && !remaining.contains("{player}")) {
            builder.suggest(remaining + " {player}");
        }

        return builder.buildFuture();
    }

    private void suggestTeams(SuggestionsBuilder builder, String prefix) {
        if (mc.world == null || mc.world.getScoreboard() == null) return;
        for (Team team : mc.world.getScoreboard().getTeams()) {
            String name = team.getName();
            if (name.toLowerCase().startsWith(prefix.toLowerCase())) {
                builder.suggest("{team:" + name + "}");
            }
        }
    }
}
