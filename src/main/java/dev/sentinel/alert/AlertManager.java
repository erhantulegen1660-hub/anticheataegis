package dev.sentinel.alert;

import dev.sentinel.AntiCheatPlugin;
import dev.sentinel.check.Check;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Алерты получают только онлайн-игроки из списка операторов (alerts.operators). */
public final class AlertManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final AntiCheatPlugin plugin;
    private final Set<String> operators = ConcurrentHashMap.newKeySet();
    private final Set<UUID> disabled = ConcurrentHashMap.newKeySet();
    private volatile boolean onlyListed;
    private volatile String format;

    public AlertManager(AntiCheatPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        var cfg = plugin.getConfig();
        onlyListed = cfg.getBoolean("alerts.only-listed-operators", true);
        format = cfg.getString("alerts.format", "<red>[AC]</red> <player> <check> <details> VL <vl> <ping>ms");
        operators.clear();
        for (String s : cfg.getStringList("alerts.operators")) operators.add(s.toLowerCase(Locale.ROOT));
    }

    public boolean isOperator(String name) { return operators.contains(name.toLowerCase(Locale.ROOT)); }

    public List<String> operators() {
        List<String> list = new ArrayList<>(operators);
        Collections.sort(list);
        return list;
    }

    public boolean addOperator(String name) {
        boolean added = operators.add(name.toLowerCase(Locale.ROOT));
        if (added) save();
        return added;
    }

    public boolean removeOperator(String name) {
        boolean removed = operators.remove(name.toLowerCase(Locale.ROOT));
        if (removed) save();
        return removed;
    }

    private void save() {
        plugin.getConfig().set("alerts.operators", operators());
        plugin.saveConfig();
    }

    /** @return новое состояние алертов для игрока. */
    public boolean toggle(Player player) {
        if (disabled.remove(player.getUniqueId())) return true;
        disabled.add(player.getUniqueId());
        return false;
    }

    private boolean eligible(Player p) {
        if (disabled.contains(p.getUniqueId())) return false;
        if (isOperator(p.getName())) return true;
        return !onlyListed && p.hasPermission("anticheat.alerts");
    }

    /** Можно вызывать из любого потока: отправка идёт через основной поток. */
    public void alert(Check check, String details) {
        Component msg = MM.deserialize(format,
                Placeholder.unparsed("player", check.data().name()),
                Placeholder.unparsed("check", check.name()),
                Placeholder.unparsed("details", details),
                Placeholder.unparsed("vl", String.format(Locale.ROOT, "%.1f", check.vl())),
                Placeholder.unparsed("ping", Integer.toString(check.data().ping())));

        plugin.getLogger().info("[flag] " + check.data().name() + " " + check.name() + " (" + details + ") VL "
                + String.format(Locale.ROOT, "%.1f", check.vl()));

        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (eligible(p)) p.sendMessage(msg);
            }
        });
    }
}
