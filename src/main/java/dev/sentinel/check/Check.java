package dev.sentinel.check;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import dev.sentinel.AntiCheatPlugin;
import dev.sentinel.data.PlayerData;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Базовый класс проверки. Один экземпляр на игрока. */
public abstract class Check {

    protected final PlayerData data;
    private final String name;

    private volatile double vl;
    private volatile double buffer;
    private volatile long lastFlagMillis;
    private final Set<Integer> executedPunishments = ConcurrentHashMap.newKeySet();

    protected Check(PlayerData data, String name) {
        this.data = data;
        this.name = name;
    }

    /** Входящий пакет (Netty-поток игрока). */
    public void onReceive(PacketReceiveEvent event) {}

    /** Исходящий пакет (любой поток, из которого сервер шлёт пакет). */
    public void onSend(PacketSendEvent event) {}

    /**
     * Буфер: при нарушении buffer += add, иначе buffer -= decay (не ниже 0).
     * Возвращает true, когда буфер достиг threshold (и сбрасывает его) — тогда нужно давать VL.
     */
    protected final boolean buffer(boolean violation, double add, double decay, double threshold) {
        if (violation) {
            buffer += add;
            if (buffer >= threshold) {
                buffer = 0;
                return true;
            }
        } else {
            buffer = Math.max(0, buffer - decay);
        }
        return false;
    }

    protected final void resetBuffer() { buffer = 0; }

    protected final void flag(String details) { flag(1.0, details); }

    protected final void flag(double amount, String details) {
        vl += amount;
        lastFlagMillis = System.currentTimeMillis();
        AntiCheatPlugin plugin = AntiCheatPlugin.get();
        if (plugin != null) plugin.violations().onFlag(this, details);
    }

    /** Спад VL при чистой игре. */
    public final void decay(double amount, long cleanMillis) {
        if (vl <= 0) return;
        if (System.currentTimeMillis() - lastFlagMillis < cleanMillis) return;
        vl = Math.max(0, vl - amount);
        if (vl == 0) executedPunishments.clear();
    }

    public final void reset() {
        vl = 0;
        buffer = 0;
        executedPunishments.clear();
    }

    /** true, если наказание с этим индексом ещё не выполнялось. */
    public final boolean markPunished(int index) { return executedPunishments.add(index); }

    public final String name() { return name; }
    public final double vl() { return vl; }
    public final double buffer() { return buffer; }
    public final PlayerData data() { return data; }
}
