
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
    private final AtomicBoolean stopped = new AtomicBoolean();

    private volatile ControlClient control;
    private volatile EShareClient eshare;
    private volatile Process ffmpeg;

    public CastSession(Options o, HttpClient http, String host, Transport transport) {
        this.o = o;
        this.http = http;
        this.host = host;
        this.transport = transport;
    }

    public void run() throws Exception {
        if (stopped.get()) return;

        control = new ControlClient(http, host, o.name, this::onControlEvent);
        control.start();

        if (!stopped.get()) {
            startCast(o.type);
        }
    }

    public void awaitEnd() throws InterruptedException {
        done.await();
    }

    private void onControlEvent(String ev) {
        Log.info("event from board: " + ev);

        switch (ev) {
            case "startMirror":
                new Thread(() -> startCast("invited"), "vcast-start").start();
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
        if (stopped.get() || !casting.compareAndSet(false, true)) return;

        try {
            EShareClient client = new EShareClient(host, o.name, transport);
            eshare = client;

            client.connect(http, done::countDown);

            if (stopped.get()) return;

            client.setup(type);

            if (type.equals("manual")) {
                Log.info("waiting for the board to accept the screen share...");
            }

            if (!client.awaitDecision(60, TimeUnit.SECONDS)) {
                Log.info("screen share refused");
                done.countDown();
                return;
            }

            if (stopped.get()) return;

            Log.info("screen share accepted, starting ffmpeg");

            Process process = new ProcessBuilder(Ffmpeg.command(o))
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();

            ffmpeg = process;

            if (stopped.get()) {
                process.destroyForcibly();
                return;
            }

            Thread pump = new Thread(
                    new VideoPump(process, client, done::countDown),
                    "vcast-pump"
            );

            pump.setDaemon(true);
            pump.start();

        } catch (Exception e) {
            Log.info("cast error: " + e);
            done.countDown();
        }
    }

    public void shutdown() {
        if (!stopped.compareAndSet(false, true)) return;

        done.countDown();

        Process process = ffmpeg;
        if (process != null) {
            process.destroy();

            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }

        EShareClient client = eshare;
        if (client != null) client.close();

        ControlClient controlClient = control;
        if (controlClient != null) controlClient.close();
    }
}
