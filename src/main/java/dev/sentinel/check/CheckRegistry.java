package dev.sentinel.check;

import dev.sentinel.AntiCheatPlugin;
import dev.sentinel.data.PlayerData;

import java.util.LinkedHashMap;
import java.util.Map;

/** Реестр фабрик проверок. Включение/выключение берётся из config.yml (checks.<имя>.enabled). */
public final class CheckRegistry {

    @FunctionalInterface
    public interface Factory {
        Check create(PlayerData data);
    }

    private final AntiCheatPlugin plugin;
    private final Map<String, Factory> factories = new LinkedHashMap<>();

    public CheckRegistry(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void register(String name, Factory factory) {
        factories.put(name.toLowerCase(), factory);
    }

    /** Создаёт включённые проверки для игрока и привязывает их к PlayerData. */
    public synchronized void attach(PlayerData data) {
        for (Map.Entry<String, Factory> e : factories.entrySet()) {
            if (plugin.getConfig().getBoolean("checks." + e.getKey() + ".enabled", true)) {
                data.addCheck(e.getValue().create(data));
            }
        }
    }
}
