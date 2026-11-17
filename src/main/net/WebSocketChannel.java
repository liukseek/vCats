package main.net;

import main.util.Log;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class WebSocketChannel implements Channel, WebSocket.Listener {
    private final String name;
    private final Consumer<String> onMessage;
    private final Runnable onClose;
    private volatile WebSocket ws;
    private final StringBuilder text = new StringBuilder();
    private final ByteArrayOutputStream bin = new ByteArrayOutputStream();

    private WebSocketChannel(String name, Consumer<String> onMessage, Runnable onClose) {
        this.name = name;
        this.onMessage = onMessage;
        this.onClose = onClose;
    }

    public static WebSocketChannel open(HttpClient http, String host, int port, Consumer<String> onMessage, Runnable onClose) throws Exception {
        WebSocketChannel c = new WebSocketChannel(host + ":" + port, onMessage, onClose);
        c.ws = http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5)).buildAsync(URI.create("ws://" + host + ":" + port), c).get(8, TimeUnit.SECONDS);
        return c;
    }

    @Override
    public synchronized void sendText(String s) {
        ws.sendText(s, true).join();
    }

    @Override
    public synchronized void sendBinary(byte[] b) {
        ws.sendBinary(ByteBuffer.wrap(b), true).join();
    }

    @Override
    public void close() {
        try {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
        } catch (Exception ignored) {
        }
    }

    @Override
    public CompletionStage<?> onText(WebSocket w, CharSequence data, boolean last) {
        text.append(data);
        if (last) {
            String m = text.toString();
            text.setLength(0);
            onMessage.accept(m);
        }
        w.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket w, ByteBuffer data, boolean last) {
        byte[] chunk = new byte[data.remaining()];
        data.get(chunk);
        bin.write(chunk, 0, chunk.length);
        if (last) {
            String m = bin.toString(StandardCharsets.UTF_8);
            bin.reset();
            onMessage.accept(m);
        }
        w.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket w, int code, String reason) {
        Log.info(name + " closed (" + code + ")");
        onClose.run();
        return null;
    }

    @Override
    public void onError(WebSocket w, Throwable t) {
        Log.info(name + " error: " + t);
        onClose.run();
    }
}
