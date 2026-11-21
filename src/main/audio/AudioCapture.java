package main.audio;

import main.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AudioCapture implements AutoCloseable {

    private final AtomicBoolean running = new AtomicBoolean(false);

    private Process process;

    public synchronized void start() throws IOException {
        if (running.get()) {
            return;
        }

        String os = System.getProperty("os.name")
                .toLowerCase(Locale.ROOT);

        String arch = System.getProperty("os.arch")
                .toLowerCase(Locale.ROOT);

        if (!os.contains("mac")) {
            throw new UnsupportedOperationException(
                    "System audio capture not supported on this OS yet"
            );
        }

        if (!arch.equals("aarch64") && !arch.equals("arm64")) {
            throw new UnsupportedOperationException(
                    "Unsupported macOS architecture: " + arch
            );
        }

        Path executable = Path.of(
                "native", "macos", "vcats-audio"
        ).toAbsolutePath();

        if (!Files.isExecutable(executable)) {
            throw new IOException(
                    "Audio helper not found or not executable: "
                            + executable
            );
        }

        process = new ProcessBuilder(executable.toString())
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();

        running.set(true);

        Log.info("System audio capture started");
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

        Log.info("System audio capture stopped");
    }
}
