package dev.sentinel;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
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
    private PacketListenerCommon packetListener;

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

        this.alerts = new AlertManager(this);
        this.violations = new ViolationManager(this);
        this.registry = new CheckRegistry(this);
        this.dataManager = new PlayerDataManager(this);

        this.packetListener = PacketEvents.getAPI()
                .getEventManager()
                .registerListener(new ACPacketListener(this), PacketListenerPriority.LOW);

        PluginCommand cmd = getCommand("sentinel");
        if (cmd != null) {
            ACCommand commandExecutor = new ACCommand(this);
            cmd.setExecutor(commandExecutor);
            cmd.setTabCompleter(commandExecutor);
        }
    }

    @Override
    public void onDisable() {
        if (packetListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
            packetListener = null;
        }
        if (dataManager != null) {
            dataManager.clear();
        }
        PacketEvents.getAPI().terminate();
    }

    public void reloadAll() {
        reloadConfig();
        if (alerts != null) {
            alerts.reload();
        }
        if (violations != null) {
            violations.reload();
        }
        if (registry != null) {
            registry.reload();
        }
    }

    public static AntiCheatPlugin get() {
        return instance;
    }

    public CheckRegistry registry() {
        return registry;
    }

    public AlertManager alerts() {
        return alerts;
    }

    public ViolationManager violations() {
        return violations;
    }

    public PlayerDataManager dataManager() {
        return dataManager;
    }
}
