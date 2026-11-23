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

    private static final int MAX_MESSAGE_SIZE = 4 * 1024 * 1024;
    private static final long SEND_TIMEOUT_SECONDS = 10;

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

        WebSocketChannel channel = new WebSocketChannel(
                host + ":" + port,
                onMessage,
                onClose
        );

        channel.ws = http.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(
                        URI.create("ws://" + host + ":" + port),
                        channel
                )
                .get(8, TimeUnit.SECONDS);

        return channel;
    }

    @Override
    public void onOpen(WebSocket socket) {
        ws = socket;
        socket.request(1);
        Log.info(name + " connected");
    }

    @Override
    public synchronized void sendText(String message) {
        ensureOpen();

        try {
            ws.sendText(message, true).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw sendFailed(e);
        }
    }

    @Override
    public synchronized void sendBinary(byte[] data) {
        ensureOpen();

        try {
            ws.sendBinary(ByteBuffer.wrap(data), true)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw sendFailed(e);
        }
    }

    private RuntimeException sendFailed(Exception e) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }

        fail(e);

        return e instanceof RuntimeException r
                ? r
                : new IllegalStateException("WebSocket send failed: " + e, e);
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
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        clearBuffers();

        WebSocket socket = ws;

        if (socket == null) {
            notifyClosed();
            return;
        }

        try {
            socket.sendClose(
                    WebSocket.NORMAL_CLOSURE,
                    "bye"
            ).orTimeout(2, TimeUnit.SECONDS).whenComplete((result, error) -> {
                if (error != null) {
                    socket.abort();
                }

                notifyClosed();
            });

        } catch (RuntimeException e) {
            socket.abort();
            notifyClosed();
        }
    }

    @Override
    public CompletionStage<?> onText(
            WebSocket socket,
            CharSequence data,
            boolean last
    ) {
        String message = null;

        try {
            if (closed.get()) {
                return null;
            }

            synchronized (bufferLock) {
                if (closed.get()) {
                    return null;
                }

                if ((long) text.length() + data.length() > MAX_MESSAGE_SIZE) {
                    throw new IllegalStateException(
                            "WebSocket text message exceeds size limit"
                    );
                }

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
            Log.info(name + " text message error: " + e);
            fail(e);

        } finally {
            if (!closed.get()) {
                socket.request(1);
            }
        }

        return null;
    }

    @Override
    public CompletionStage<?> onBinary(
            WebSocket socket,
            ByteBuffer data,
            boolean last
    ) {
        String message = null;

        try {
            if (closed.get()) {
                return null;
            }

            synchronized (bufferLock) {
                if (closed.get()) {
                    return null;
                }

                if ((long) bin.size() + data.remaining() > MAX_MESSAGE_SIZE) {
                    throw new IllegalStateException(
                            "WebSocket binary message exceeds size limit"
                    );
                }

                byte[] chunk = new byte[data.remaining()];
                data.get(chunk);

                bin.write(chunk, 0, chunk.length);

                if (last) {
                    // Conversione reversibile: un byte per carattere.
                    message = bin.toString(StandardCharsets.ISO_8859_1);
                    bin.reset();
                }
            }

            if (message != null && !closed.get()) {
                onMessage.accept(message);
            }

        } catch (RuntimeException e) {
            Log.info(name + " binary message error: " + e);
            fail(e);

        } finally {
            if (!closed.get()) {
                socket.request(1);
            }
        }

        return null;
    }

    @Override
    public CompletionStage<?> onClose(
            WebSocket socket,
            int code,
            String reason
    ) {
        Log.info(name + " closed (" + code + ")");
        notifyClosed();
        return null;
    }

    @Override
    public void onError(WebSocket socket, Throwable error) {
        Log.info(name + " error: " + error);
        fail(error);
    }

    private void fail(Throwable error) {
        Log.info(name + " connection failure: " + error);

        closed.set(true);
        clearBuffers();

        WebSocket socket = ws;

        if (socket != null) {
            socket.abort();
        }

        notifyClosed();
    }

    private void clearBuffers() {
        synchronized (bufferLock) {
            text.setLength(0);
            bin.reset();
        }
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
