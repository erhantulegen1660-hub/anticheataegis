package dev.sentinel.data;

import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing;
import dev.sentinel.check.Check;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Состояние игрока. Поля движения/ротаций пишутся только из Netty-потока этого игрока,
 * для чтения из других потоков (команда /ac info) используются volatile/synchronized.
 */
public final class PlayerData {

    public record PosSnapshot(double x, double y, double z, float yaw, float pitch, boolean onGround, long tick) {}

    private static final int HISTORY_SIZE = 40;
    private static final int PING_BASE = -1_000_000;
    private static final long PING_TIMEOUT_NANOS = 30_000_000_000L;

    private final User user;
    private final UUID uuid;
    private final String name;
    private final List<Check> checks = new CopyOnWriteArrayList<>();

    private final Deque<PosSnapshot> history = new ArrayDeque<>(HISTORY_SIZE + 1);
    private final Map<Integer, Long> pendingPings = new ConcurrentHashMap<>();
    private final AtomicInteger pingCounter = new AtomicInteger();

    private volatile int ping;
    private volatile double smoothPing;
    private volatile long clientTick;

    public PlayerData(User user) {
        this.user = user;
        this.uuid = user.getUUID();
        this.name = user.getName();
    }

    // ---------- Проверки ----------
    public void addCheck(Check check) { checks.add(check); }
    public List<Check> checks() { return checks; }

    // ---------- История позиций ----------
    public void pushPosition(double x, double y, double z, float yaw, float pitch, boolean onGround) {
        PosSnapshot snap = new PosSnapshot(x, y, z, yaw, pitch, onGround, ++clientTick);
        synchronized (history) {
            history.addLast(snap);
            while (history.size() > HISTORY_SIZE) history.removeFirst();
        }
    }

    public PosSnapshot lastPosition() {
        synchronized (history) { return history.peekLast(); }
    }

    /** Копия истории от старой к новой. */
    public List<PosSnapshot> positions() {
        synchronized (history) { return new ArrayList<>(history); }
    }

    // ---------- Ping через PING/PONG ----------
    public void sendPing() {
        if (user.getConnectionState() != ConnectionState.PLAY) return;
        long now = System.nanoTime();
        int id = PING_BASE - (pingCounter.getAndIncrement() & 0xFFFF);
        pendingPings.put(id, now);
        if (pendingPings.size() > 200) {
            pendingPings.values().removeIf(t -> now - t > PING_TIMEOUT_NANOS);
        }
        user.sendPacket(new WrapperPlayServerPing(id));
    }

    /** @return true, если это наш пинг (пакет можно не пропускать дальше). */
    public boolean onPong(int id) {
        Long sent = pendingPings.remove(id);
        if (sent == null) return false;
        int rtt = (int) ((System.nanoTime() - sent) / 1_000_000L);
        ping = rtt;
        smoothPing = smoothPing == 0 ? rtt : smoothPing * 0.7 + rtt * 0.3;
        return true;
    }

    public int ping() { return ping; }
    public double smoothPing() { return smoothPing; }
    public int pingTicks() { return (int) Math.ceil(smoothPing / 50.0); }

    public User user() { return user; }
    public UUID uuid() { return uuid; }
    public String name() { return name; }
    public long clientTick() { return clientTick; }
}
