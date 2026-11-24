
package io.github.vcats.net;

import io.github.vcats.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.http.HttpClient;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public enum Transport {
    WEBSOCKET(51042, 51032, 51033),
    TCP(51040, 51030, 51020);

    public final int rtspPort;
    public final int videoPort;
    public final int audioPort;

    private static final int CONNECT_TIMEOUT = 1500;

    Transport(int rtspPort, int videoPort, int audioPort) {
        this.rtspPort = rtspPort;
        this.videoPort = videoPort;
        this.audioPort = audioPort;
    }

    public Channel open(
            HttpClient http,
            String host,
            int port,
            Consumer<String> onMessage,
            Runnable onClose
    ) throws Exception {
        return this == TCP
                ? TcpChannel.open(
                        host,
                        port,
                        onMessage,
                        onClose,
                        port == rtspPort
                )
                : WebSocketChannel.open(
                        http,
                        host,
                        port,
                        onMessage,
                        onClose
                );
    }

    public static Transport choose(String requested, String host) {
        Transport selected = switch (requested) {
            case "raw" -> TCP;
            case "ws" -> WEBSOCKET;
            case "auto" -> chooseAvailable(host);
            default -> throw new IllegalArgumentException(
                    "Unknown transport: " + requested
            );
        };

        Log.info(
                "cast transport: "
                        + (selected == TCP ? "native TCP" : "WebSocket")
                        + " (ports "
                        + selected.rtspPort + "/"
                        + selected.videoPort + "/"
                        + selected.audioPort + ")"
        );

        return selected;
    }

    private static Transport chooseAvailable(String host) {
        if (portsOpen(host, WEBSOCKET)) {
            return WEBSOCKET;
        }

        if (portsOpen(host, TCP)) {
            return TCP;
        }

        Log.info(
                "WARNING: no complete transport detected, "
                        + "defaulting to WebSocket"
        );

        return WEBSOCKET;
    }

    private static boolean portsOpen(String host, Transport transport) {
        var rtsp = CompletableFuture.supplyAsync(
                () -> tcpOpen(host, transport.rtspPort));
        var video = CompletableFuture.supplyAsync(
                () -> tcpOpen(host, transport.videoPort));
        var audio = CompletableFuture.supplyAsync(
                () -> tcpOpen(host, transport.audioPort));

        return rtsp.join() && video.join() && audio.join();
    }

    private static boolean tcpOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(
                    new InetSocketAddress(host, port),
                    CONNECT_TIMEOUT
            );

            return true;

        } catch (IOException e) {
            return false;
        }
    }
}
