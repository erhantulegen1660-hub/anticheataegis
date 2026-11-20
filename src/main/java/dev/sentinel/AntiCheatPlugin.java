package dev.sentinel;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import dev.sentinel.alert.AlertManager;
import dev.sentinel.check.CheckRegistry;
import dev.sentinel.command.ACCommand;
import dev.sentinel.data.PlayerDataManager;
import dev.sentinel.listener.ACPacketListener;
import dev.sentinel.punish.ViolationManager;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class AntiCheatPlugin extends JavaPlugin {

    private static AntiCheatPlugin instance;

    private CheckRegistry registry;
    private AlertManager alerts;
    private ViolationManager violations;
    private PlayerDataManager dataManager;
    private ACPacketListener packetListener;

    @Override
    public void onLoad() {
        instance = this;
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings()
                .reEncodeByDefault(false)
                .checkForUpdates(false)
                .bStats(false);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        registry = new CheckRegistry(this);
        alerts = new AlertManager(this);
        violations = new ViolationManager(this);
        dataManager = new PlayerDataManager(this);

        PluginCommand cmd = getCommand("ac");
        if (cmd != null) {
            ACCommand handler = new ACCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        PacketEvents.getAPI().init();
        packetListener = new ACPacketListener(this);
        PacketEvents.getAPI().getEventManager().registerListener(packetListener, PacketListenerPriority.LOW);

        dataManager.startTasks();
        getLogger().info("SentinelAC enabled.");
    }

    @Override
    public void onDisable() {
        if (dataManager != null) dataManager.shutdown();
        if (PacketEvents.getAPI() != null) {
            if (packetListener != null) PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
            PacketEvents.getAPI().terminate();
        }
        instance = null;
    }

    public void reloadAll() {
        reloadConfig();
        alerts.reload();
        violations.reload();
    }

    public static AntiCheatPlugin get() { return instance; }
    public CheckRegistry registry() { return registry; }
    public AlertManager alerts() { return alerts; }
    public ViolationManager violations() { return violations; }
    public PlayerDataManager dataManager() { return dataManager; }
}
