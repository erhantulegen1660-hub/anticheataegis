package dev.sentinel.punish;

import dev.sentinel.AntiCheatPlugin;
import dev.sentinel.check.Check;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Таблица наказаний по порогам VL (глобальная + по проверкам). Выполнение — на основном потоке. */
public final class ViolationManager {

    public enum Type { KICK, COMMAND }

    public record Punishment(double vl, Type type, String command) {}

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final AntiCheatPlugin plugin;
    private volatile List<Punishment> defaults = List.of();
    private volatile Map<String, List<Punishment>> perCheck = Map.of();
    private volatile String kickMessage;

    public ViolationManager(AntiCheatPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        var cfg = plugin.getConfig();
        kickMessage = cfg.getString("punishments.kick-message", "<red>Kicked by anti-cheat: <check>");
        defaults = parse(cfg.getMapList("punishments.default"));

        Map<String, List<Punishment>> overrides = new HashMap<>();
        ConfigurationSection checks = cfg.getConfigurationSection("checks");
        if (checks != null) {
            for (String key : checks.getKeys(false)) {
                List<Punishment> list = parse(cfg.getMapList("checks." + key + ".punishments"));
                if (!list.isEmpty()) overrides.put(key.toLowerCase(Locale.ROOT), list);
            }
        }
        perCheck = overrides;
    }

    private List<Punishment> parse(List<Map<?, ?>> raw) {
        List<Punishment> out = new ArrayList<>();
        for (Map<?, ?> m : raw) {
            if (!(m.get("vl") instanceof Number n)) continue;
            Type type;
            try {
                type = Type.valueOf(String.valueOf(m.get("type")).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Unknown punishment type: " + m.get("type"));
                continue;
            }
            String command = m.get("command") instanceof String s ? s : "";
            if (type == Type.COMMAND && command.isBlank()) continue;
            out.add(new Punishment(n.doubleValue(), type, command));
        }
        out.sort(Comparator.comparingDouble(Punishment::vl));
        return List.copyOf(out);
    }

    /** Вызывается из Check.flag() в любом потоке. */
    public void onFlag(Check check, String details) {
        plugin.alerts().alert(check, details);

        List<Punishment> table = perCheck.getOrDefault(check.name().toLowerCase(Locale.ROOT), defaults);
        List<Punishment> due = new ArrayList<>();
        for (int i = 0; i < table.size(); i++) {
            Punishment p = table.get(i);
            if (check.vl() >= p.vl() && check.markPunished(i)) due.add(p);
        }
        if (due.isEmpty() || !plugin.isEnabled()) return;

        // Команды (бан) выполняются раньше кика, чтобы кик не отменил их из-за выхода игрока
        due.sort(Comparator.comparing((Punishment p) -> p.type() == Type.KICK));

        final String vlText = String.format(Locale.ROOT, "%.1f", check.vl());
        final String pingText = Integer.toString(check.data().ping());
        final String safeDetails = details.replaceAll("[^\\p{L}\\p{N} .,:=()/_+-]", "");
        final String playerName = check.data().name();
        final var uuid = check.data().uuid();
        final String checkName = check.name();

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Punishment p : due) {
                switch (p.type()) {
                    case COMMAND -> {
                        String cmd = p.command()
                                .replace("%player%", playerName)
                                .replace("%check%", checkName)
                                .replace("%vl%", vlText)
                                .replace("%ping%", pingText)
                                .replace("%details%", safeDetails);
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                    }
                    case KICK -> {
                        Player player = Bukkit.getPlayer(uuid);
                        if (player == null) break;
                        Component reason = MM.deserialize(kickMessage,
                                Placeholder.unparsed("check", checkName),
                                Placeholder.unparsed("vl", vlText),
                                Placeholder.unparsed("ping", pingText),
                                Placeholder.unparsed("details", safeDetails));
                        player.kick(reason);
                    }
                }
            }
        });
    }
}
