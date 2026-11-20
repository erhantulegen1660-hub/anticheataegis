package dev.sentinel.listener;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.UserDisconnectEvent;
import com.github.retrooper.packetevents.event.UserLoginEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPong;
import dev.sentinel.AntiCheatPlugin;
import dev.sentinel.check.Check;
import dev.sentinel.data.PlayerData;

import java.util.logging.Level;

/** Единая точка входа пакетов: ping/pong, учёт игроков и рассылка пакетов в проверки. */
public final class ACPacketListener implements PacketListener

    private final AntiCheatPlugin plugin;

    public ACPacketListener(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onUserLogin(UserLoginEvent event) {
        plugin.dataManager().create(event.getUser());
    }

    @Override
    public void onUserDisconnect(UserDisconnectEvent event) {
        plugin.dataManager().remove(event.getUser().getUUID());
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        PlayerData data = plugin.dataManager().get(event.getUser().getUUID());
        if (data == null) return;

        if (event.getPacketType() == PacketType.Play.Client.PONG) {
            if (data.onPong(new WrapperPlayClientPong(event).getId())) {
                event.setCancelled(true);
                return;
            }
        }

        for (Check check : data.checks()) {
            try {
                check.onReceive(event);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Check " + check.name() + " failed on receive", t);
            }
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        PlayerData data = plugin.dataManager().get(event.getUser().getUUID());
        if (data == null) return;

        for (Check check : data.checks()) {
            try {
                check.onSend(event);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Check " + check.name() + " failed on send", t);
            }
        }
    }
}
