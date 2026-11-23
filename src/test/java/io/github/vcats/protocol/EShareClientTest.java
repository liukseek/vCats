package io.github.vcats.protocol;

import io.github.vcats.net.Channel;
import io.github.vcats.net.ChannelFactory;
import io.github.vcats.net.Transport;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EShareClientTest {

    private static final class FakeChannel implements Channel {

        final List<byte[]> sent = new CopyOnWriteArrayList<>();

        Runnable closeCallback;
        int closeCount;

        FakeChannel(Runnable closeCallback) {
            this.closeCallback = closeCallback;
        }

        @Override
        public void sendText(String message) {
            sent.add(message.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void sendBinary(byte[] data) {
            sent.add(data.clone());
        }

        @Override
        public void close() {
            closeCount++;
        }

        void loseConnection() {
            closeCallback.run();
        }

        boolean contains(String text) {
            return sent.stream().anyMatch(data ->
                    new String(data, StandardCharsets.UTF_8)
                            .contains(text)
            );
        }
    }

    private static final class Fixture {

        final List<FakeChannel> channels = new ArrayList<>();

        final EShareClient client;

        Fixture() {
            ChannelFactory factory = (http, host, port, onMessage, onClose) -> {
                FakeChannel channel = new FakeChannel(onClose);
                channels.add(channel);

                if (channels.size() == 1) {
                    rtspReply = onMessage;
                }

                return channel;
            };

            client = new EShareClient(
                    "127.0.0.1",
                    "vCats Test",
                    Transport.TCP,
                    factory
            );
        }

        java.util.function.Consumer<String> rtspReply;

        void connect() throws Exception {
            client.connect(HttpClient.newHttpClient(), () -> {});
        }

        FakeChannel rtsp() {
            return channels.get(0);
        }

        FakeChannel video() {
            return channels.get(1);
        }

        FakeChannel audio() {
            return channels.get(2);
        }
    }

    @Test
    void connectOpensThreeChannels() throws Exception {
        Fixture fixture = new Fixture();

        try {
            fixture.connect();

            assertEquals(3, fixture.channels.size());
        } finally {
            fixture.client.close();
        }
    }

    @Test
    void setupSendsRequestAndAcceptsAllowedResponse() throws Exception {
        Fixture fixture = new Fixture();

        try {
            fixture.connect();

            fixture.client.setup("manual");

            assertTrue(fixture.rtsp().contains("SETUP"));
            assertTrue(fixture.rtsp().contains("x-screencast-type: manual"));

            fixture.rtspReply.accept(
                    "RTSP/1.0 200 OK\r\n"
                    + "x-command-header: allowed\r\n\r\n"
            );

            assertTrue(
                    fixture.client.awaitDecision(1, TimeUnit.SECONDS)
            );
        } finally {
            fixture.client.close();
        }
    }

    @Test
    void refusedResponseReturnsFalse() throws Exception {
        Fixture fixture = new Fixture();

        try {
            fixture.connect();
            fixture.client.setup("manual");

            fixture.rtspReply.accept(
                    "RTSP/1.0 403 Forbidden\r\n"
                    + "x-command-header: refused\r\n\r\n"
            );

            assertFalse(
                    fixture.client.awaitDecision(1, TimeUnit.SECONDS)
            );
        } finally {
            fixture.client.close();
        }
    }

    @Test
    void invitedModeAcceptsImmediately() throws Exception {
        Fixture fixture = new Fixture();

        try {
            fixture.connect();
            fixture.client.setup("invited");

            assertTrue(
                    fixture.client.awaitDecision(1, TimeUnit.SECONDS)
            );
        } finally {
            fixture.client.close();
        }
    }

    @Test
    void sendVideoSendsHeaderAndPayload() throws Exception {
        Fixture fixture = new Fixture();

        try {
            fixture.connect();

            byte[] frame = {
                    0, 0, 0, 1, 0x65, 1, 2, 3
            };

            fixture.client.sendVideo(frame, false);

            assertTrue(fixture.video().sent.size() >= 2);

            byte[] header = fixture.video().sent.get(0);
            byte[] payload = fixture.video().sent.get(1);

            assertEquals(128, header.length);

            ByteBuffer buffer = ByteBuffer.wrap(header)
                    .order(ByteOrder.LITTLE_ENDIAN);

            assertEquals(frame.length, buffer.getInt());
            assertEquals(Protocol.T_VIDEO, buffer.getShort());

            assertArrayEquals(frame, payload);
        } finally {
            fixture.client.close();
        }
    }

    @Test
    void sendConfigurationUsesConfigPacketType() throws Exception {
        Fixture fixture = new Fixture();

        try {
            fixture.connect();

            byte[] config = {
                    0, 0, 0, 1, 0x67, 1, 2
            };

            fixture.client.sendVideo(config, true);

            byte[] header = fixture.video().sent.get(0);

            ByteBuffer buffer = ByteBuffer.wrap(header)
                    .order(ByteOrder.LITTLE_ENDIAN);

            assertEquals(config.length, buffer.getInt());
            assertEquals(Protocol.T_CONFIG, buffer.getShort());
        } finally {
            fixture.client.close();
        }
    }

    @Test
    void closeClosesAllThreeChannels() throws Exception {
        Fixture fixture = new Fixture();

        fixture.connect();
        fixture.client.close();

        for (FakeChannel channel : fixture.channels) {
            assertEquals(1, channel.closeCount);
        }

        assertTrue(fixture.rtsp().contains("TEARDOWN"));
    }

    @Test
    void connectionLossNotifiesOnlyOnce() throws Exception {
        Fixture fixture = new Fixture();
        AtomicInteger notifications = new AtomicInteger();

        fixture.client.connect(
                HttpClient.newHttpClient(),
                notifications::incrementAndGet
        );

        fixture.audio().loseConnection();
        fixture.video().loseConnection();

        assertEquals(1, notifications.get());

        assertThrows(
                ExecutionException.class,
                () -> fixture.client.awaitDecision(1, TimeUnit.SECONDS)
        );

        fixture.client.close();
    }

    @Test
    void cannotSendVideoAfterConnectionLoss() throws Exception {
        Fixture fixture = new Fixture();

        fixture.connect();
        fixture.audio().loseConnection();

        assertThrows(
                IllegalStateException.class,
                () -> fixture.client.sendVideo(new byte[]{1, 2, 3}, false)
        );

        fixture.client.close();
    }
}
