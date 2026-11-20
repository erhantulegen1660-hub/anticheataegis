package dev.sentinel;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import dev.sentinel.listener.ACPacketListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class AntiCheatPlugin extends JavaPlugin {

    private PacketListenerCommon packetListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.packetListener = PacketEvents.getAPI()
                .getEventManager()
                .registerListener(new ACPacketListener(this), PacketListenerPriority.LOW);
    }

    @Override
    public void onDisable() {
        if (packetListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
            packetListener = null;
        }
    }
}
