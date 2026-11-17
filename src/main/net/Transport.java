package main.net;

import main.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.http.HttpClient;
import java.util.function.Consumer;

public enum Transport {
    WEBSOCKET(51042, 51032, 51033),
    TCP(51040, 51030, 51020);

    public final int rtspPort, videoPort, audioPort;

    Transport(int rtspPort, int videoPort, int audioPort) {
        this.rtspPort = rtspPort;
        this.videoPort = videoPort;
        this.audioPort = audioPort;
    }

    public Channel open(HttpClient http, String host, int port, Consumer<String> onMessage, Runnable onClose) throws Exception {
        return this == TCP ? TcpChannel.open(host, port, onMessage, onClose) : WebSocketChannel.open(http, host, port, onMessage, onClose);
    }

    public static Transport choose(String requested, String host) {
        Transport t = switch (requested) {
            case "raw" -> TCP;
            case "ws" -> WEBSOCKET;
            default -> !tcpOpen(host, WEBSOCKET.rtspPort) && tcpOpen(host, TCP.rtspPort) ? TCP : WEBSOCKET;
        };
        Log.info("cast transport: " + (t == TCP ? "native TCP" : "WebSocket") + " (ports " + t.rtspPort + "/" + t.videoPort + "/" + t.audioPort + ")");
        return t;
    }

    private static boolean tcpOpen(String host, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 1500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
