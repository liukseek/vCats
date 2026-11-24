
package io.github.vcats.session;

import io.github.vcats.audio.AudioCapture;
import io.github.vcats.audio.AudioFormat;
import io.github.vcats.audio.AudioPump;
import io.github.vcats.config.Options;
import io.github.vcats.media.Ffmpeg;
import io.github.vcats.media.VideoPump;
import io.github.vcats.net.Channel;
import io.github.vcats.net.Transport;
import io.github.vcats.protocol.ControlClient;
import io.github.vcats.protocol.EShareClient;
import io.github.vcats.util.Log;

import java.net.http.HttpClient;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

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
    private volatile Ffmpeg.Capture capture;
    private volatile AudioCapture audioCapture;
    private volatile Channel rawAudio;

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

            Ffmpeg.Capture started = Ffmpeg.start(o);
            capture = started;

            if (stopped.get()) {
                started.close();
                return;
            }

            Process process = started.encoder();

            Thread pump = new Thread(
                    new VideoPump(process, client, done::countDown),
                    "vcast-pump"
            );

            pump.setDaemon(true);
            pump.start();

            if (o.audioCycle) {
                startAudioCycle(client);

            } else if (o.audio) {
                startAudio(client);
            }

        } catch (Exception e) {
            Log.info("cast error: " + e);
            done.countDown();
        }
    }

    /**
     * Audio check for boards whose expected format is unknown: plays a
     * 440 Hz test tone in each format in turn. Whichever one is heard on the
     * board is the one to use with --audio-format.
     */
    private void startAudioCycle(EShareClient client) {
        Thread cycle = new Thread(() -> {
            AudioFormat[] formats = AudioFormat.values();

            for (int i = 0; i < formats.length && !stopped.get(); i++) {
                AudioFormat format = formats[i];
                AudioCapture trial = new AudioCapture();

                try {
                    trial.start(o.toneTrial(format));

                } catch (Exception e) {
                    Log.info("audio test: cannot start " + format.id + ": " + e);
                    continue;
                }

                audioCapture = trial;

                Consumer<byte[]> output;

                try {
                    output = audioOutput(format, client);

                } catch (Exception e) {
                    Log.info("audio test: cannot open the output for "
                            + format.id + ": " + e);
                    trial.close();
                    continue;
                }

                Thread pump = new Thread(
                        new AudioPump(
                                trial.getInputStream(),
                                format,
                                output,
                                () -> { }
                        ),
                        "vcast-audio-test"
                );
                pump.setDaemon(true);
                pump.start();

                Log.info("AUDIO TEST " + (i + 1) + "/" + formats.length
                        + ": format '" + format.id + "' for 12 seconds, "
                        + "listen for a steady tone on the board");

                try {
                    for (int s = 0; s < 12 && !stopped.get(); s++) {
                        Thread.sleep(1000);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    trial.close();
                    return;
                }

                trial.close();
            }

            Log.info("AUDIO TEST finished. Use --audio-format with the "
                    + "format whose tone you heard.");
        }, "vcast-audio-cycle");

        cycle.setDaemon(true);
        cycle.start();
    }

    /**
     * Where the audio packets go: plain TCP to the board's audio port for the
     * raw formats, otherwise the EShare audio channel with its 128-byte header.
     */
    private Consumer<byte[]> audioOutput(AudioFormat format, EShareClient client)
            throws Exception {

        if (format.rawPort == 0) {
            return client::sendAudio;
        }

        Channel previous = rawAudio;

        if (previous != null) {
            previous.close();
        }

        Channel channel = Transport.TCP.open(
                http,
                host,
                format.rawPort,
                reply -> { },
                () -> Log.info("audio connection closed")
        );

        rawAudio = channel;
        Log.info("audio: raw " + format.sampleRate + " Hz, " + format.channels
                + " ch, 16-bit to " + host + ":" + format.rawPort);

        return channel::sendBinary;
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

        Consumer<byte[]> output;

        try {
            output = audioOutput(o.audioFormat, client);

        } catch (Exception e) {
            Log.info("audio disabled: " + e.getMessage());
            capture.close();
            return;
        }

        Thread pump = new Thread(
                new AudioPump(
                        capture.getInputStream(),
                        o.audioFormat,
                        output,
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

        Ffmpeg.Capture running = capture;
        if (running != null) running.close();

        AudioCapture capture = audioCapture;
        if (capture != null) capture.close();

        Channel audioChannel = rawAudio;
        if (audioChannel != null) audioChannel.close();

        EShareClient client = eshare;
        if (client != null) client.close();

        ControlClient controlClient = control;
        if (controlClient != null) controlClient.close();
    }
}
