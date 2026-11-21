package main.audio;

import main.config.Options;
import main.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AudioCapture implements AutoCloseable {
    public static final int SAMPLE_RATE = 48_000;
    public static final int CHANNELS = 2;
    public static final int BYTES_PER_SAMPLE = 2;
    public static final int FRAME_MS = 20;
    public static final int FRAME_BYTES =
            SAMPLE_RATE / 1000 * FRAME_MS * CHANNELS * BYTES_PER_SAMPLE;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private Process process;

    public synchronized void start(Options options) throws IOException {
        if (running.get()) {
            return;
        }

        List<String> command = AudioSource.command(options);

        process = new ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();

        running.set(true);

        Log.info("audio capture started: PCM "
                + SAMPLE_RATE + " Hz, " + CHANNELS + " channels, 16-bit");
    }

    public synchronized InputStream getInputStream() {
        if (process == null) {
            throw new IllegalStateException(
                    "Audio capture has not started"
            );
        }

        return process.getInputStream();
    }

    public synchronized boolean isRunning() {
        return running.get()
                && process != null
                && process.isAlive();
    }

    @Override
    public synchronized void close() {
        running.set(false);

        if (process == null) {
            return;
        }

        process.destroy();

        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }

        process = null;

        Log.info("audio capture stopped");
    }
}
