
package io.github.vcats.protocol;

import io.github.vcats.net.Channel;
import io.github.vcats.net.WebSocketChannel;
import io.github.vcats.util.Log;

import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class ControlClient {
    private final Channel channel;
    private final String user;
    private final Consumer<String> events;

    private final ScheduledExecutorService timer =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "vcast-heartbeat");
                t.setDaemon(true);
                return t;
            });

    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean disconnected = new AtomicBoolean();
    private final AtomicBoolean started = new AtomicBoolean();

    private volatile long pongAt = System.nanoTime();

    public ControlClient(
            HttpClient http,
            String host,
            String user,
            Consumer<String> events
    ) throws Exception {
        this.user = user;
        this.events = events;

        this.channel = WebSocketChannel.open(
                http,
                host,
                Protocol.PORT_CONTROL,
                this::onMessage,
                this::onDisconnect
        );
    }

    public void start() {
        if (closed.get() || !started.compareAndSet(false, true)) {
            return;
        }

        try {
            channel.sendText(
                    "/Connected\r\n"
                            + user + "\r\n"
                            + Protocol.DEVICE_TYPE + "\r\n"
            );

            channel.sendText(
                    "/GetServerProperty\r\n"
                            + user + "\r\n"
            );

            if (!closed.get()) {
                timer.scheduleAtFixedRate(
                        this::beat,
                        0,
                        3,
                        TimeUnit.SECONDS
                );
            }

        } catch (Exception e) {
            Log.info("control start error: " + e);
            onDisconnect();
        }
    }

    private void beat() {
        if (closed.get() || disconnected.get()) return;

        long sent = System.nanoTime();

        try {
            channel.sendText(
                    "/HeartBeat\r\n" + user + "\r\n"
            );

            timer.schedule(() -> {
                if (!closed.get()
                        && !disconnected.get()
                        && pongAt < sent) {
                    Log.info("heartbeat got no reply");
                    onDisconnect();
                }
            }, 2, TimeUnit.SECONDS);

        } catch (RejectedExecutionException ignored) {
            // Timer already stopped.

        } catch (Exception e) {
            Log.info("heartbeat error: " + e);
            onDisconnect();
        }
    }

    private void onDisconnect() {
        if (closed.get()) return;

        if (disconnected.compareAndSet(false, true)) {
            Log.info("control connection lost");
            timer.shutdownNow();
            notifyEvent("disconnect");
        }
    }

    private void onMessage(String raw) {
        if (closed.get() || disconnected.get()) return;

        String message = raw.trim();

        if (message.equals("success")) {
            Log.info("control: connected");
            return;
        }

        if (message.startsWith("heartBeat")) {
            pongAt = System.nanoTime();
            return;
        }

        Map<String, String> properties = parseProperties(message);

        if (properties.containsKey("server_name")) {
            String serverName = properties.getOrDefault("server_name", "");

            long features = 0;

            try {
                features = Long.parseLong(
                        properties.getOrDefault("server_feature", "0")
                );
            } catch (NumberFormatException ignored) {
            }

            boolean moderator = ((features >> 9) & 1) == 1
                    && ((features >> 14) & 1) == 1;

            Log.info(
                    "board: '" + serverName
                            + "', moderator=" + moderator
                            + ", broadcast="
                            + properties.get("server_broadcast")
            );

            return;
        }

        notifyEvent(message);
    }

    private Map<String, String> parseProperties(String message) {
        Map<String, String> properties = new HashMap<>();

        for (String pair : message.split("&")) {
            int separator = pair.indexOf('=');

            if (separator <= 0) continue;

            try {
                String key = URLDecoder.decode(
                        pair.substring(0, separator),
                        StandardCharsets.UTF_8
                );

                String value = URLDecoder.decode(
                        pair.substring(separator + 1),
                        StandardCharsets.UTF_8
                );

                properties.put(key, value);

            } catch (IllegalArgumentException e) {
                Log.info("control: invalid property encoding");
            }
        }

        return properties;
    }

    private void notifyEvent(String event) {
        try {
            events.accept(event);
        } catch (RuntimeException e) {
            Log.info("control event callback error: " + e);
        }
    }

    public void close() {
        if (!closed.compareAndSet(false, true)) return;

        timer.shutdownNow();

        try {
            channel.sendText(
                    "/Disconnect\r\n" + user + "\r\n"
            );
        } catch (Exception ignored) {
        }

        channel.close();

        Log.info("control client closed");
    }
}
