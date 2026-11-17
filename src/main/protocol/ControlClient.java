package main.protocol;

import main.net.Channel;
import main.net.WebSocketChannel;
import main.util.Log;

import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class ControlClient {
    private final Channel channel;
    private final String user;
    private final Consumer<String> events;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "vcast-heartbeat");
        t.setDaemon(true);
        return t;
    });
    private volatile long pongAt = System.nanoTime();
    private volatile String serverName = "";
    private volatile boolean moderator;

    public ControlClient(HttpClient http, String host, String user, Consumer<String> events) throws Exception {
        this.user = user;
        this.events = events;
        this.channel = WebSocketChannel.open(http, host, Protocol.PORT_CONTROL, this::onMessage, () -> events.accept("disconnect"));
    }

    public void start() {
        channel.sendText("/Connected\r\n" + user + "\r\n" + Protocol.DEVICE_TYPE + "\r\n");
        channel.sendText("/GetServerProperty\r\n" + user + "\r\n");
        timer.scheduleAtFixedRate(this::beat, 0, 3, TimeUnit.SECONDS);
    }

    private void beat() {
        long sent = System.nanoTime();
        try {
            channel.sendText("/HeartBeat\r\n" + user + "\r\n");
        } catch (Exception e) {
            return;
        }
        timer.schedule(() -> {
            if (pongAt < sent) {
                Log.info("heartbeat got no reply");
                events.accept("disconnect");
            }
        }, 2, TimeUnit.SECONDS);
    }

    private void onMessage(String raw) {
        String m = raw.trim();
        if (m.equals("success")) {
            Log.info("control: connected");
            return;
        }
        if (m.startsWith("heartBeat")) {
            pongAt = System.nanoTime();
            return;
        }
        if (m.startsWith("server_name=")) {
            Map<String, String> q = new HashMap<>();
            for (String kv : m.split("&")) {
                int i = kv.indexOf('=');
                if (i > 0) q.put(kv.substring(0, i), URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8));
            }
            serverName = q.getOrDefault("server_name", "");
            long f = 0;
            try {
                f = Long.parseLong(q.getOrDefault("server_feature", "0"));
            } catch (NumberFormatException ignored) {
            }
            moderator = ((f >> 9) & 1) == 1 && ((f >> 14) & 1) == 1;
            Log.info("board: '" + serverName + "', moderator=" + moderator + ", broadcast=" + q.get("server_broadcast"));
            return;
        }
        events.accept(m);
    }

    public void close() {
        timer.shutdownNow();
        try {
            channel.sendText("/Disconnect\r\n" + user + "\r\n");
        } catch (Exception ignored) {
        }
        channel.close();
    }
}
