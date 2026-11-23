package io.github.vcats.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public final class Processes {
    public record Result(int exitCode, String output) {
    }

    private Processes() {
    }

    public static Result run(List<String> command, long timeoutSeconds)
            throws IOException, InterruptedException {

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

        CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
            try (var in = process.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        });

        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor();

            throw new IOException(
                    "process timed out after " + timeoutSeconds + " seconds"
            );
        }

        try {
            return new Result(process.exitValue(), output.get());

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();

            if (cause instanceof CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }

            if (cause instanceof IOException io) {
                throw io;
            }

            throw new IOException("could not read process output", cause);
        }
    }
}
