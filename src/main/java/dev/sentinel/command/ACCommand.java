package dev.sentinel.command;

import dev.sentinel.AntiCheatPlugin;
import dev.sentinel.check.Check;
import dev.sentinel.data.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Доступ: консоль или игроки из alerts.operators. */
public final class ACCommand implements TabExecutor {

    private static final List<String> ROOT = List.of("alerts", "operator", "info", "reset", "reload");

    private final AntiCheatPlugin plugin;

    public ACCommand(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    private static void msg(CommandSender s, String text, NamedTextColor color) {
        s.sendMessage(Component.text(text, color));
    }

    private boolean allowed(CommandSender s) {
        return !(s instanceof Player p) || plugin.alerts().isOperator(p.getName());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!allowed(sender)) {
            msg(sender, "Нет доступа.", NamedTextColor.RED);
            return true;
        }
        if (args.length == 0) {
            msg(sender, "/ac <alerts|operator add|remove|list <ник>|info <игрок>|reset <игрок>|reload>", NamedTextColor.GRAY);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "alerts" -> {
                if (!(sender instanceof Player p)) {
                    msg(sender, "Только для игроков.", NamedTextColor.RED);
                    return true;
                }
                boolean on = plugin.alerts().toggle(p);
                msg(sender, "Алерты " + (on ? "включены" : "выключены"), on ? NamedTextColor.GREEN : NamedTextColor.YELLOW);
            }
            case "operator" -> operator(sender, args);
            case "info" -> info(sender, args);
            case "reset" -> {
                if (args.length < 2) { msg(sender, "/ac reset <игрок>", NamedTextColor.GRAY); return true; }
                PlayerData d = plugin.dataManager().byName(args[1]);
                if (d == null) { msg(sender, "Игрок не найден.", NamedTextColor.RED); return true; }
                d.checks().forEach(Check::reset);
                msg(sender, "Нарушения " + d.name() + " сброшены.", NamedTextColor.GREEN);
            }
            case "reload" -> {
                plugin.reloadAll();
                msg(sender, "Конфиг перезагружен (проверки для уже онлайн-игроков обновятся после перезахода).", NamedTextColor.GREEN);
            }
            default -> msg(sender, "Неизвестная подкоманда.", NamedTextColor.RED);
        }
        return true;
    }

    private void operator(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
            msg(sender, "Операторы: " + String.join(", ", plugin.alerts().operators()), NamedTextColor.GRAY);
            return;
        }
        if (args.length < 3) {
            msg(sender, "/ac operator add|remove <ник> | list", NamedTextColor.GRAY);
            return;
        }
        String nick = args[2];
        if (!nick.matches("[A-Za-z0-9_.]{1,16}")) {
            msg(sender, "Некорректный ник.", NamedTextColor.RED);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> msg(sender, plugin.alerts().addOperator(nick) ? "Добавлен: " + nick : "Уже в списке.",
                    NamedTextColor.GREEN);
            case "remove" -> {
                if (sender instanceof Player p && p.getName().equalsIgnoreCase(nick)) {
                    msg(sender, "Нельзя удалить самого себя.", NamedTextColor.RED);
                    return;
                }
                msg(sender, plugin.alerts().removeOperator(nick) ? "Удалён: " + nick : "Не найден в списке.",
                        NamedTextColor.YELLOW);
            }
            default -> msg(sender, "/ac operator add|remove <ник>", NamedTextColor.GRAY);
        }
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) { msg(sender, "/ac info <игрок>", NamedTextColor.GRAY); return; }
        PlayerData d = plugin.dataManager().byName(args[1]);
        if (d == null) { msg(sender, "Игрок не найден.", NamedTextColor.RED); return; }
        msg(sender, String.format(Locale.ROOT, "%s — ping %d ms (сглаж. %.0f ms)", d.name(), d.ping(), d.smoothPing()),
                NamedTextColor.AQUA);
        for (Check c : d.checks()) {
            msg(sender, String.format(Locale.ROOT, " %s: VL %.1f | buffer %.2f", c.name(), c.vl(), c.buffer()),
                    c.vl() > 0 ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!allowed(sender)) return List.of();
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : ROOT) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("operator")) {
            for (String s : List.of("add", "remove", "list")) if (s.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("reset"))) {
            plugin.dataManager().all().forEach(d -> {
                if (d.name().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(d.name());
            });
        } else if (args.length == 3 && args[0].equalsIgnoreCase("operator") && args[1].equalsIgnoreCase("remove")) {
            plugin.alerts().operators().forEach(o -> { if (o.startsWith(args[2].toLowerCase(Locale.ROOT))) out.add(o); });
        }
        return out;
    }
}
