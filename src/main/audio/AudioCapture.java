package main.audio;

import main.config.Options;
import main.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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

    private List<Process> processes = List.of();

    public synchronized void start(Options options) throws IOException {
        if (running.get()) {
            return;
        }

        List<ProcessBuilder> builders = new ArrayList<>();

        for (List<String> command : AudioSource.pipeline(options)) {
            builders.add(new ProcessBuilder(command)
                    .redirectError(ProcessBuilder.Redirect.INHERIT));
        }

        processes = builders.size() == 1
                ? List.of(builders.get(0).start())
                : ProcessBuilder.startPipeline(builders);

        running.set(true);

        Log.info("audio capture started: PCM "
                + SAMPLE_RATE + " Hz, " + CHANNELS + " channels, 16-bit");
    }

    public synchronized InputStream getInputStream() {
        if (processes.isEmpty()) {
            throw new IllegalStateException(
                    "Audio capture has not started"
            );
        }

        return processes.getLast().getInputStream();
    }

    public synchronized boolean isRunning() {
        return running.get()
                && !processes.isEmpty()
                && processes.getLast().isAlive();
    }

    @Override
    public synchronized void close() {
        running.set(false);

        if (processes.isEmpty()) {
            return;
        }

        processes.forEach(Process::destroy);

        for (Process process : processes) {
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }

        processes = List.of();

        Log.info("audio capture stopped");
    }
}
