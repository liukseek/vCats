
package io.github.vcats.protocol;

import io.github.vcats.net.Channel;
import io.github.vcats.net.ChannelFactory;
import io.github.vcats.net.Transport;
import io.github.vcats.util.Log;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EShareClient {

    private static final Pattern DECISION =
            Pattern.compile("(?im)^x-command-header:\\s*(\\w+)");

    private final String host;
    private final String user;
    private final Transport transport;
    private final ChannelFactory channelFactory;

    private volatile Channel rtsp;
    private volatile Channel video;
    private volatile Channel audio;

    private final Object audioLock = new Object();
    private final Object videoLock = new Object();

    private int cseq;
    private volatile boolean closed;

    private final AtomicBoolean channelLost = new AtomicBoolean();
    private final AtomicBoolean lossNotified = new AtomicBoolean();

    private final CompletableFuture<Boolean> decision =
            new CompletableFuture<>();

    private final ScheduledExecutorService timer =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "eshare-options");
                t.setDaemon(true);
                return t;
            });

    public EShareClient(String host, String user, Transport transport) {
        this(host, user, transport, transport::open);
    }

    public EShareClient(
            String host,
            String user,
            Transport transport,
            ChannelFactory channelFactory
    ) {
        this.host = host;
        this.user = user;
        this.transport = transport;
        this.channelFactory = channelFactory;
    }

    public synchronized void connect(HttpClient http, Runnable onLost)
            throws Exception {

        if (closed) {
            throw new IllegalStateException("EShareClient is closed");
        }

        if (rtsp != null || video != null || audio != null) {
            throw new IllegalStateException("EShareClient already connected");
        }

        Channel openedRtsp = null;
        Channel openedVideo = null;
        Channel openedAudio = null;

        try {
            Log.info("EShare: connecting RTSP channel to "
                    + host + ":" + transport.rtspPort);

            openedRtsp = channelFactory.open(
                    http,
                    host,
                    transport.rtspPort,
                    this::onReply,
                    () -> notifyLost(onLost)
            );

            checkConnection();

            Log.info("EShare: RTSP channel connected");

            Log.info("EShare: connecting VIDEO channel to "
                    + host + ":" + transport.videoPort);

            openedVideo = channelFactory.open(
                    http,
                    host,
                    transport.videoPort,
                    replyLogger("VIDEO"),
                    () -> notifyLost(onLost)
            );

            checkConnection();

            Log.info("EShare: VIDEO channel connected");

            Log.info("EShare: connecting AUDIO channel to "
                    + host + ":" + transport.audioPort);

            openedAudio = channelFactory.open(
                    http,
                    host,
                    transport.audioPort,
                    replyLogger("AUDIO"),
                    () -> notifyLost(onLost)
            );

            checkConnection();

            Log.info("EShare: AUDIO channel connected");

            rtsp = openedRtsp;
            video = openedVideo;
            audio = openedAudio;

            timer.scheduleAtFixedRate(
                    this::options,
                    0,
                    1,
                    TimeUnit.SECONDS
            );

            Log.info("EShare: all channels connected");

        } catch (Exception e) {
            closed = true;
            decision.complete(false);
            timer.shutdownNow();

            closeChannel(openedRtsp);
            closeChannel(openedVideo);
            closeChannel(openedAudio);

            rtsp = null;
            video = null;
            audio = null;

            Log.info("EShare: connection failed while opening media channels");
            Log.info("EShare: target was " + host);
            Log.info("EShare: configured ports: RTSP="
                    + transport.rtspPort
                    + ", VIDEO=" + transport.videoPort
                    + ", AUDIO=" + transport.audioPort);

            throw e;
        }
    }

    /** Logs the first few messages the board sends on a media channel. */
    private static Consumer<String> replyLogger(String channel) {
        AtomicInteger seen = new AtomicInteger();

        return message -> {
            if (seen.incrementAndGet() > 10) {
                return;
            }

            StringBuilder text = new StringBuilder();

            for (int i = 0; i < message.length() && i < 160; i++) {
                char c = message.charAt(i);
                text.append(c >= 32 && c < 127 ? c : '.');
            }

            Log.info("EShare: " + channel + " channel <- "
                    + message.length() + " bytes: " + text);
        };
    }

    private void checkConnection() throws IOException {
        if (closed || channelLost.get()) {
            throw new IOException("EShare connection lost");
        }
    }

    private void notifyLost(Runnable onLost) {
        channelLost.set(true);
        timer.shutdownNow();

        decision.completeExceptionally(
                new IOException("EShare connection lost")
        );

        if (lossNotified.compareAndSet(false, true)) {
            Log.info("EShare: channel connection lost");

            try {
                onLost.run();
            } catch (RuntimeException e) {
                Log.info("EShare: disconnect callback error: " + e);
            }
        }
    }

    public boolean awaitDecision(long timeout, TimeUnit unit)
            throws Exception {
        return decision.get(timeout, unit);
    }

    private void onReply(String reply) {
        if (closed) {
            return;
        }

        if (transport == Transport.TCP) {
            Log.info("RTSP <- "
                    + reply.replace("\r", "").replace("\n", " | "));
        }

        if (transport == Transport.TCP
                && reply.contains("bplist00")
                && !decision.isDone()) {

            Log.info("EShare: SETUP accepted (dataPort "
                    + transport.videoPort + ")");

            decision.complete(true);
        }

        Matcher matcher = DECISION.matcher(reply);

        if (matcher.find()) {
            String value = matcher.group(1).toLowerCase(Locale.ROOT);

            Log.info("EShare: x-command-header=" + value);

            if (value.equals("allowed")) {
                decision.complete(true);
            } else if (value.equals("refused")) {
                decision.complete(false);
            }
        }
    }

    private String common() {
        return "User-Agent: EShare (EShare 1.0)\r\n"
                + "DeviceName: " + user + "\r\n";
    }

    private synchronized void options() {
        if (closed || channelLost.get() || rtsp == null) {
            return;
        }

        String request = "OPTIONS rtsp://" + host + " RTSP/1.0\r\n"
                + "CSeq: " + (cseq++) + "\r\n"
                + "rotation: 0\r\n"
                + common()
                + "Content-Length: 0\r\n\r\n";

        try {
            rtsp.sendBinary(
                    request.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            Log.info("EShare: OPTIONS failed: " + e);
            timer.shutdownNow();
        }
    }

    public synchronized void setup(String type) {
        ensureConnected();

        byte[] plist = Protocol.STREAM_PLIST;

        String head = "SETUP rtsp://" + host + " RTSP/1.0\r\n"
                + "CSeq: " + (cseq++) + "\r\n"
                + common()
                + "x-screencast-type: " + type + "\r\n"
                + "Content-Length: " + plist.length + "\r\n\r\n";

        byte[] header = head.getBytes(StandardCharsets.UTF_8);

        byte[] request = Arrays.copyOf(
                header,
                header.length + plist.length
        );

        System.arraycopy(
                plist,
                0,
                request,
                header.length,
                plist.length
        );

        rtsp.sendBinary(request);

        if (type.equals("invited")) {
            decision.complete(true);
        }
    }

    private synchronized void teardown() {
        if (rtsp == null) {
            return;
        }

        byte[] plist = Protocol.STREAM_PLIST;

        String head = "TEARDOWN rtsp://" + host + " RTSP/1.0\r\n"
                + "CSeq: " + (cseq++) + "\r\n"
                + "User-Agent: EShare (EShare 1.0)\r\n"
                + "Content-Length: " + plist.length + "\r\n\r\n";

        byte[] header = head.getBytes(StandardCharsets.UTF_8);

        byte[] request = Arrays.copyOf(
                header,
                header.length + plist.length
        );

        System.arraycopy(
                plist,
                0,
                request,
                header.length,
                plist.length
        );

        try {
            rtsp.sendBinary(request);
        } catch (Exception e) {
            Log.info("EShare: TEARDOWN failed: " + e);
        }
    }

    private static byte[] header(int payloadLen, int type) {
        long ms = System.currentTimeMillis();

        ByteBuffer buffer = ByteBuffer.allocate(128)
                .order(ByteOrder.LITTLE_ENDIAN);

        buffer.putInt(payloadLen);

        buffer.putShort((short) type)
                .putShort((short) 0)
                .putShort((short) 0);

        buffer.putInt((int) (long)
                ((ms % 1000) / 1000.0 * 4294967296.0));

        buffer.putInt((int) (ms / 1000));

        return buffer.array();
    }

    // Not synchronized on the client: a stalled network write must never
    // keep close() or the OPTIONS keep-alive waiting for the monitor.
    public void sendVideo(byte[] annexB, boolean config) {
        ensureConnected();

        Channel channel = video;

        if (channel == null) {
            throw new IllegalStateException("EShareClient is not connected");
        }

        synchronized (videoLock) {
            channel.sendBinary(header(
                    annexB.length,
                    config ? Protocol.T_CONFIG : Protocol.T_VIDEO
            ));

            channel.sendBinary(annexB);
        }
    }

    public void sendAudio(byte[] pcm) {
        Channel channel = audio;

        if (closed || channelLost.get() || channel == null) {
            throw new IllegalStateException(
                    "EShareClient is not connected"
            );
        }

        synchronized (audioLock) {
            channel.sendBinary(header(pcm.length, Protocol.T_AUDIO));
            channel.sendBinary(pcm);
        }
    }

    private void ensureConnected() {
        if (closed || channelLost.get()
                || rtsp == null
                || video == null
                || audio == null) {

            throw new IllegalStateException(
                    "EShareClient is not connected"
            );
        }
    }

    private void closeChannel(Channel channel) {
        if (channel == null) {
            return;
        }

        try {
            channel.close();
        } catch (Exception e) {
            Log.info("EShare: channel close error: " + e);
        }
    }

    public synchronized void close() {
        if (closed) {
            return;
        }

        closed = true;
        decision.complete(false);
        timer.shutdownNow();

        teardown();

        closeChannel(rtsp);
        closeChannel(video);
        closeChannel(audio);

        rtsp = null;
        video = null;
        audio = null;

        Log.info("EShare: client closed");
    }
}
