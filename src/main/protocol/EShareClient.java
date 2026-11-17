package main.protocol;

import main.net.Channel;
import main.net.Transport;
import main.util.Log;

import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EShareClient {
    private static final Pattern DECISION = Pattern.compile("(?im)^x-command-header:\\s*(\\w+)");

    private final String host, user;
    private final Transport transport;
    private Channel rtsp, video, audio;
    private int cseq = 0;
    private final CompletableFuture<Boolean> decision = new CompletableFuture<>();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "eshare-options");
        t.setDaemon(true);
        return t;
    });

    public EShareClient(String host, String user, Transport transport) {
        this.host = host;
        this.user = user;
        this.transport = transport;
    }

    public void connect(HttpClient http, Runnable onLost) throws Exception {
        rtsp = transport.open(http, host, transport.rtspPort, this::onReply, onLost);
        video = transport.open(http, host, transport.videoPort, m -> {
        }, onLost);
        audio = transport.open(http, host, transport.audioPort, m -> {
        }, onLost);
        timer.scheduleAtFixedRate(this::options, 0, 1, TimeUnit.SECONDS);
    }

    public boolean awaitDecision(long timeout, TimeUnit unit) throws Exception {
        return decision.get(timeout, unit);
    }

    private void onReply(String reply) {
        if (transport == Transport.TCP) Log.info("RTSP <- " + reply.replace("\r", "").replace("\n", " | "));
        if (transport == Transport.TCP && reply.contains("bplist00") && !decision.isDone()) {
            Log.info("EShare: SETUP accepted (dataPort " + transport.videoPort + ")");
            decision.complete(true);
        }
        Matcher m = DECISION.matcher(reply);
        if (m.find()) {
            String v = m.group(1).toLowerCase();
            Log.info("EShare: x-command-header=" + v);
            if (v.equals("allowed")) decision.complete(true);
            else if (v.equals("refused")) decision.complete(false);
        }
    }

    private String common() {
        return "User-Agent: EShare (EShare 1.0)\r\nDeviceName: " + user + "\r\n";
    }

    private synchronized void options() {
        String s = "OPTIONS rtsp://" + host + " RTSP/1.0\r\nCSeq: " + (cseq++) + "\r\nrotation: 0\r\n" + common() + "Content-Length: 0\r\n\r\n";
        try {
            rtsp.sendBinary(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    public synchronized void setup(String type) {
        byte[] plist = Protocol.STREAM_PLIST;
        String head = "SETUP rtsp://" + host + " RTSP/1.0\r\nCSeq: " + (cseq++) + "\r\n" + common() + "x-screencast-type: " + type + "\r\nContent-Length: " + plist.length + "\r\n\r\n";
        byte[] h = head.getBytes(StandardCharsets.UTF_8);
        byte[] all = Arrays.copyOf(h, h.length + plist.length);
        System.arraycopy(plist, 0, all, h.length, plist.length);
        rtsp.sendBinary(all);
        if (type.equals("invited")) decision.complete(true);
    }

    private synchronized void teardown() {
        byte[] plist = Protocol.STREAM_PLIST;
        String head = "TEARDOWN rtsp://" + host + " RTSP/1.0\r\nCSeq: " + (cseq++) + "\r\n" + "User-Agent: EShare (EShare 1.0)\r\nContent-Length: " + plist.length + "\r\n\r\n";
        try {
            rtsp.sendBinary(head.getBytes(StandardCharsets.UTF_8));
            rtsp.sendBinary(plist);
        } catch (Exception ignored) {
        }
    }

    private static byte[] header(int payloadLen, int type) {
        long ms = System.currentTimeMillis();
        ByteBuffer b = ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(payloadLen);
        b.putShort((short) type).putShort((short) 0).putShort((short) 0);
        b.putInt((int) (long) ((ms % 1000) / 1000.0 * 4294967296.0));
        b.putInt((int) (ms / 1000));
        return b.array();
    }

    public void sendVideo(byte[] annexB, boolean config) {
        video.sendBinary(header(annexB.length, config ? Protocol.T_CONFIG : Protocol.T_VIDEO));
        video.sendBinary(annexB);
    }

    public void close() {
        timer.shutdownNow();
        teardown();
        for (Channel c : new Channel[]{rtsp, video, audio}) if (c != null) c.close();
    }
}
