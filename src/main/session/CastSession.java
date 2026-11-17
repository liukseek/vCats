package main.session;

import main.config.Options;
import main.media.Ffmpeg;
import main.media.VideoPump;
import main.net.Transport;
import main.protocol.ControlClient;
import main.protocol.EShareClient;
import main.util.Log;

import java.net.http.HttpClient;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CastSession {
    private final Options o;
    private final HttpClient http;
    private final String host;
    private final Transport transport;
    private final CountDownLatch done = new CountDownLatch(1);
    private final AtomicBoolean casting = new AtomicBoolean();
    private ControlClient control;
    private EShareClient eshare;
    private Process ffmpeg;

    public CastSession(Options o, HttpClient http, String host, Transport transport) {
        this.o = o;
        this.http = http;
        this.host = host;
        this.transport = transport;
    }

    public void run() throws Exception {
        control = new ControlClient(http, host, o.name, this::onControlEvent);
        control.start();
        startCast(o.type);
    }

    public void awaitEnd() throws InterruptedException {
        done.await();
    }

    private void onControlEvent(String ev) {
        Log.info("event from board: " + ev);
        switch (ev) {
            case "startMirror":
                new Thread(() -> startCast("invited")).start();
                break;
            case "stopMirror":
            case "disconnect":
                done.countDown();
                break;
            default:
                break;
        }
    }

    private void startCast(String type) {
        if (!casting.compareAndSet(false, true)) return;
        try {
            eshare = new EShareClient(host, o.name, transport);
            eshare.connect(http, done::countDown);
            eshare.setup(type);
            if (type.equals("manual")) Log.info("waiting for the board to accept the screen share...");
            if (!eshare.awaitDecision(60, TimeUnit.SECONDS)) {
                Log.info("screen share refused");
                done.countDown();
                return;
            }
            Log.info("screen share accepted, starting ffmpeg");
            ffmpeg = new ProcessBuilder(Ffmpeg.command(o)).redirectError(ProcessBuilder.Redirect.INHERIT).start();
            new Thread(new VideoPump(ffmpeg, eshare, done::countDown), "vcast-pump").start();
        } catch (Exception e) {
            Log.info("cast error: " + e);
            done.countDown();
        }
    }

    public void shutdown() {
        if (ffmpeg != null) {
            ffmpeg.destroy();
            try {
                if (!ffmpeg.waitFor(2, TimeUnit.SECONDS)) ffmpeg.destroyForcibly();
            } catch (InterruptedException ignored) {
            }
        }
        if (eshare != null) eshare.close();
        if (control != null) control.close();
    }
}
