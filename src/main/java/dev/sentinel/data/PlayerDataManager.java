package dev.sentinel.data;

import com.github.retrooper.packetevents.protocol.player.User;
import dev.sentinel.AntiCheatPlugin;
import dev.sentinel.check.Check;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerDataManager {

    private final AntiCheatPlugin plugin;
    private final Map<UUID, PlayerData> map = new ConcurrentHashMap<>();
    private BukkitTask decayTask;
    private BukkitTask pingTask;

    public PlayerDataManager(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    public PlayerData create(User user) {
        if (user.getUUID() == null) return null;
        PlayerData data = new PlayerData(user);
        plugin.registry().attach(data);
        map.put(data.uuid(), data);
        return data;
    }

    public PlayerData get(UUID uuid) { return uuid == null ? null : map.get(uuid); }

    public void remove(UUID uuid) { if (uuid != null) map.remove(uuid); }

    public PlayerData byName(String name) {
        for (PlayerData d : map.values()) {
            if (d.name().equalsIgnoreCase(name)) return d;
        }
        return null;
    }

    public Collection<PlayerData> all() { return map.values(); }

    public void startTasks() {
        var cfg = plugin.getConfig();
        long decayPeriod = Math.max(1, cfg.getLong("vl-decay.interval-seconds", 20)) * 20L;
        long pingPeriod = Math.max(5, cfg.getLong("ping.interval-ticks", 20));

        decayTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            double amount = plugin.getConfig().getDouble("vl-decay.amount", 0.5);
            long clean = plugin.getConfig().getLong("vl-decay.clean-seconds", 15) * 1000L;
            for (PlayerData d : map.values()) {
                for (Check c : d.checks()) c.decay(amount, clean);
            }
        }, decayPeriod, decayPeriod);

        pingTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (PlayerData d : map.values()) d.sendPing();
        }, pingPeriod, pingPeriod);
    }

    public void shutdown() {
        if (decayTask != null) decayTask.cancel();
        if (pingTask != null) pingTask.cancel();
        map.clear();
    }
}
