package main.net;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class WebSocketChannelTest {

    private static final String GUID =
            "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    @Test
    void receivesTextMessage() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();

        try (Server server = new Server(socket ->
                sendFrame(
                        socket.getOutputStream(),
                        0x1,
                        true,
                        "hello".getBytes(StandardCharsets.UTF_8)
                ))) {

            WebSocketChannel channel = connect(
                    server,
                    value -> {
                        result.set(value);
                        received.countDown();
                    },
                    () -> {}
            );

            try {
                assertTrue(received.await(3, TimeUnit.SECONDS));
                assertEquals("hello", result.get());
            } finally {
                channel.close();
            }
        }
    }

    @Test
    void receivesFragmentedTextMessage() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();

        try (Server server = new Server(socket -> {
            OutputStream out = socket.getOutputStream();

            sendFrame(
                    out,
                    0x1,
                    false,
                    "hel".getBytes(StandardCharsets.UTF_8)
            );

            sendFrame(
                    out,
                    0x0,
                    true,
                    "lo".getBytes(StandardCharsets.UTF_8)
            );
        })) {

            WebSocketChannel channel = connect(
                    server,
                    value -> {
                        result.set(value);
                        received.countDown();
                    },
                    () -> {}
            );

            try {
                assertTrue(
                        received.await(5, TimeUnit.SECONDS),
                        "Il messaggio WebSocket frammentato non è stato ricevuto"
                );

                assertEquals("hello", result.get());
            } finally {
                channel.close();
            }
        }
    }

    @Test
    void receivesBinaryMessageWithoutChangingBytes()
            throws Exception {

        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();

        byte[] original = {
                0x00, 0x01, 0x7F, (byte) 0x80, (byte) 0xFF
        };

        try (Server server = new Server(socket ->
                sendFrame(
                        socket.getOutputStream(),
                        0x2,
                        true,
                        original
                ))) {

            WebSocketChannel channel = connect(
                    server,
                    value -> {
                        result.set(value);
                        received.countDown();
                    },
                    () -> {}
            );

            try {
                assertTrue(received.await(3, TimeUnit.SECONDS));

                assertArrayEquals(
                        original,
                        result.get().getBytes(StandardCharsets.ISO_8859_1)
                );
            } finally {
                channel.close();
            }
        }
    }

    private static WebSocketChannel connect(
            Server server,
            Consumer<String> callback,
            Runnable onClose
    ) throws Exception {

        return WebSocketChannel.open(
                HttpClient.newHttpClient(),
                "127.0.0.1",
                server.port(),
                callback,
                onClose
        );
    }

    private static void sendFrame(
            OutputStream out,
            int opcode,
            boolean fin,
            byte[] payload
    ) throws Exception {

        out.write((fin ? 0x80 : 0) | opcode);

        if (payload.length <= 125) {
            out.write(payload.length);
        } else {
            throw new IllegalArgumentException("Frame too large");
        }

        out.write(payload);
        out.flush();
    }

    @FunctionalInterface
    private interface Handler {
        void handle(Socket socket) throws Exception;
    }

    private static final class Server implements AutoCloseable {

        private final ServerSocket server;
        private final Thread thread;

        Server(Handler handler) throws Exception {
            server = new ServerSocket(0);

            thread = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(5000);

                    handshake(socket);
                    handler.handle(socket);

                    // Mantiene la connessione disponibile dopo l'invio.
                    Thread.sleep(1000);

                } catch (Exception e) {
                    if (!server.isClosed()) {
                        e.printStackTrace();
                    }
                }
            }, "websocket-test-server");

            thread.setDaemon(true);
            thread.start();
        }

        int port() {
            return server.getLocalPort();
        }

        private static void handshake(Socket socket) throws Exception {
            InputStream in = socket.getInputStream();
            StringBuilder request = new StringBuilder();

            int state = 0;
            int value;

            while ((value = in.read()) != -1) {
                request.append((char) value);

                if (request.length() > 16384) {
                    throw new IllegalStateException(
                            "Handshake too large"
                    );
                }

                if (state == 0 && value == '\r') {
                    state = 1;
                } else if (state == 1 && value == '\n') {
                    state = 2;
                } else if (state == 2 && value == '\r') {
                    state = 3;
                } else if (state == 3 && value == '\n') {
                    break;
                } else {
                    state = 0;
                }
            }

            String key = null;

            for (String line : request.toString().split("\r\n")) {
                if (line.toLowerCase().startsWith("sec-websocket-key:")) {
                    key = line.substring(
                            line.indexOf(':') + 1
                    ).trim();
                }
            }

            if (key == null) {
                throw new IllegalStateException(
                        "Missing WebSocket key"
                );
            }

            byte[] digest = java.security.MessageDigest
                    .getInstance("SHA-1")
                    .digest(
                            (key + GUID).getBytes(
                                    StandardCharsets.ISO_8859_1
                            )
                    );

            String accept = java.util.Base64
                    .getEncoder()
                    .encodeToString(digest);

            String response =
                    "HTTP/1.1 101 Switching Protocols\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + accept
                    + "\r\n\r\n";

            socket.getOutputStream().write(
                    response.getBytes(StandardCharsets.ISO_8859_1)
            );

            socket.getOutputStream().flush();
        }

        @Override
        public void close() throws Exception {
            server.close();
            thread.join(1500);
        }
    }
}
