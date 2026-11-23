
package main.session;

import main.audio.AudioCapture;
import main.audio.AudioPump;
import main.config.Options;
import main.media.Ffmpeg;
import main.media.VideoPump;
import main.net.Transport;
import main.protocol.ControlClient;
import main.protocol.EShareClient;
import main.util.Log;

import java.net.http.HttpClient;
import java.util.List;
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
    private volatile List<Process> capture = List.of();
    private volatile AudioCapture audioCapture;

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

            if (stopped.get()) {
                client.close();
                return;
            }

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

            List<Process> processes = Ffmpeg.start(o);
            capture = processes;

            if (stopped.get()) {
                processes.forEach(Process::destroyForcibly);
                return;
            }

            Process process = processes.getLast();

            Thread pump = new Thread(
                    new VideoPump(process, client, done::countDown),
                    "vcast-pump"
            );

            pump.setDaemon(true);
            pump.start();

            if (o.audio) {
                startAudio(client);
            }

        } catch (Exception e) {
            Log.info("cast error: " + e);
            done.countDown();
        }
    }

    private void startAudio(EShareClient client) {
        AudioCapture capture = new AudioCapture();

        try {
            capture.start(o);

        } catch (Exception e) {
            Log.info("audio disabled: " + e.getMessage());
            capture.close();
            return;
        }

        audioCapture = capture;

        if (stopped.get()) {
            capture.close();
            return;
        }

        Thread pump = new Thread(
                new AudioPump(
                        capture.getInputStream(),
                        client::sendAudio,
                        () -> Log.info("audio stream ended")
                ),
                "vcast-audio"
        );

        pump.setDaemon(true);
        pump.start();
    }

    public void shutdown() {
        if (!stopped.compareAndSet(false, true)) return;

        done.countDown();

        for (Process process : capture) {
            process.destroy();
        }

        for (Process process : capture) {
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }

        AudioCapture capture = audioCapture;
        if (capture != null) capture.close();

        EShareClient client = eshare;
        if (client != null) client.close();

        ControlClient controlClient = control;
        if (controlClient != null) controlClient.close();
    }
}
