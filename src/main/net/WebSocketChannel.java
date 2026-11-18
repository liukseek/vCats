
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class WebSocketChannel implements Channel, WebSocket.Listener {
    private final String name;
    private final Consumer<String> onMessage;
    private final Runnable onClose;

    private volatile WebSocket ws;

    private final StringBuilder text = new StringBuilder();
    private final ByteArrayOutputStream bin = new ByteArrayOutputStream();
    private final Object bufferLock = new Object();

    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean closeNotified = new AtomicBoolean();

    private WebSocketChannel(
            String name,
            Consumer<String> onMessage,
            Runnable onClose
    ) {
        this.name = name;
        this.onMessage = onMessage;
        this.onClose = onClose;
    }

    public static WebSocketChannel open(
            HttpClient http,
            String host,
            int port,
            Consumer<String> onMessage,
            Runnable onClose
    ) throws Exception {
        WebSocketChannel c = new WebSocketChannel(
                host + ":" + port,
                onMessage,
                onClose
        );

        c.ws = http.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(
                        URI.create("ws://" + host + ":" + port),
                        c
                )
                .get(8, TimeUnit.SECONDS);

        return c;
    }

    @Override
    public synchronized void sendText(String s) {
        ensureOpen();

        try {
            ws.sendText(s, true).join();
        } catch (RuntimeException e) {
            notifyClosed();
            throw e;
        }
    }

    @Override
    public synchronized void sendBinary(byte[] b) {
        ensureOpen();

        try {
            ws.sendBinary(ByteBuffer.wrap(b), true).join();
        } catch (RuntimeException e) {
            notifyClosed();
            throw e;
        }
    }

    private void ensureOpen() {
        if (closed.get() || ws == null) {
            throw new IllegalStateException(
                    "WebSocket channel is closed"
            );
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;

        WebSocket socket = ws;

        if (socket == null) {
            notifyClosed();
            return;
        }

        try {
            socket.sendClose(
                    WebSocket.NORMAL_CLOSURE,
                    "bye"
            ).whenComplete((result, error) -> {
                if (error != null) {
                    notifyClosed();
                }
            });

        } catch (RuntimeException e) {
            notifyClosed();
        }
    }

    @Override
    public CompletionStage<?> onText(
            WebSocket w,
            CharSequence data,
            boolean last
    ) {
        String message = null;

        try {
            if (closed.get()) return null;

            synchronized (bufferLock) {
                if (closed.get()) return null;

                text.append(data);

                if (last) {
                    message = text.toString();
                    text.setLength(0);
                }
            }

            if (message != null && !closed.get()) {
                onMessage.accept(message);
            }

        } catch (RuntimeException e) {
            Log.info(name + " message error: " + e);

        } finally {
            w.request(1);
        }

        return null;
    }

    @Override
    public CompletionStage<?> onBinary(
            WebSocket w,
            ByteBuffer data,
            boolean last
    ) {
        String message = null;

        try {
            if (closed.get()) return null;

            byte[] chunk = new byte[data.remaining()];
            data.get(chunk);

            synchronized (bufferLock) {
                if (closed.get()) return null;

                bin.write(chunk, 0, chunk.length);

                if (last) {
                    message = bin.toString(StandardCharsets.UTF_8);
                    bin.reset();
                }
            }

            if (message != null && !closed.get()) {
                onMessage.accept(message);
            }

        } catch (RuntimeException e) {
            Log.info(name + " binary message error: " + e);

        } finally {
            w.request(1);
        }

        return null;
    }

    @Override
    public CompletionStage<?> onClose(
            WebSocket w,
            int code,
            String reason
    ) {
        Log.info(name + " closed (" + code + ")");
        notifyClosed();
        return null;
    }

    @Override
    public void onError(WebSocket w, Throwable t) {
        Log.info(name + " error: " + t);
        notifyClosed();
    }

    private void notifyClosed() {
        closed.set(true);

        if (closeNotified.compareAndSet(false, true)) {
            try {
                onClose.run();
            } catch (RuntimeException e) {
                Log.info(name + " close callback error: " + e);
            }
        }
    }
}
