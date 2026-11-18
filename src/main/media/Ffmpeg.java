
package main.media;

import main.config.Options;
import main.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Ffmpeg {
    private static final Path TEST_IMAGE =
            Path.of("assets", "test-kittens.jpg");

    private static final long CHECK_TIMEOUT = 5;
    private static final long DEVICE_TIMEOUT = 5;

    private Ffmpeg() {
    }

    private static String os() {
        return System.getProperty("os.name").toLowerCase();
    }

    private record ProcessResult(int exitCode, String output) {
    }

    private static ProcessResult execute(
            List<String> command,
            long timeout
    ) throws IOException, InterruptedException {

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

        CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
            try (var in = process.getInputStream()) {
                return new String(
                        in.readAllBytes(),
                        StandardCharsets.UTF_8
                );
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        });

        if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor();

            throw new IOException(
                    "process timed out after " + timeout + " seconds"
            );
        }

        try {
            return new ProcessResult(
                    process.exitValue(),
                    output.get()
            );

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();

            if (cause instanceof CompletionException
                    && cause.getCause() != null) {
                cause = cause.getCause();
            }

            if (cause instanceof IOException io) {
                throw io;
            }

            throw new IOException(
                    "could not read process output",
                    cause
            );
        }
    }

    public static void check(Options o)
            throws IOException, InterruptedException {

        ProcessResult result;

        try {
            result = execute(
                    List.of(o.ffmpeg, "-version"),
                    CHECK_TIMEOUT
            );

        } catch (IOException e) {
            throw new IOException(
                    "ffmpeg is not executable ('"
                            + o.ffmpeg + "'): "
                            + e.getMessage()
                            + ". Install it with: "
                            + installHint(),
                    e
            );
        }

        if (result.exitCode() != 0) {
            throw new IOException(
                    "ffmpeg check failed (exit code "
                            + result.exitCode() + ")"
            );
        }

        String os = os();

        if (!o.test
                && o.input == null
                && !os.contains("win")
                && !os.contains("mac")
                && "wayland".equalsIgnoreCase(
                        System.getenv("XDG_SESSION_TYPE")
                )) {

            Log.info(
                    "WARNING: Wayland session, x11grab may capture "
                            + "only black or XWayland windows; "
                            + "use --input or an X11 session"
            );
        }
    }

    public static List<String> command(Options o) {
        List<String> c = new ArrayList<>(
                List.of(
                        o.ffmpeg,
                        "-hide_banner",
                        "-loglevel",
                        "error"
                )
        );

        String os = os();

        String filter =
                "scale=" + o.width + ":" + o.height
                        + ",format=yuv420p";

        if (o.test && Files.isRegularFile(TEST_IMAGE)) {
            Log.info("test source: " + TEST_IMAGE);

            c.addAll(List.of(
                    "-re",
                    "-loop", "1",
                    "-framerate", "" + o.fps,
                    "-i", TEST_IMAGE.toAbsolutePath().toString()
            ));

            filter = "scale="
                    + Math.round(o.width * 1.12)
                    + ":" + Math.round(o.height * 1.12)
                    + ":force_original_aspect_ratio=increase,"
                    + "crop=" + o.width + ":" + o.height
                    + ":x='(iw-ow)/2*(1+sin(t/2))'"
                    + ":y='(ih-oh)*(0.1+0.15*(1-cos(t/3)))'"
                    + ",format=yuv420p";

        } else if (o.test) {
            Log.info(
                    "test image " + TEST_IMAGE
                            + " not found, using the generated pattern"
            );

            c.addAll(List.of(
                    "-re",
                    "-f", "lavfi",
                    "-i", "testsrc2=size="
                            + o.width + "x" + o.height
                            + ":rate=" + o.fps
            ));

        } else if (o.input != null) {
            c.addAll(parseInput(o.input));

        } else if (os.contains("win")) {
            c.addAll(List.of(
                    "-f", "gdigrab",
                    "-framerate", "" + o.fps,
                    "-i", "desktop"
            ));

        } else if (os.contains("mac")) {
            c.addAll(List.of(
                    "-f", "avfoundation",
                    "-framerate", "" + o.fps,
                    "-capture_cursor", "1",
                    "-i", macScreenIndex(o.ffmpeg) + ":none"
            ));

        } else {
            c.addAll(List.of(
                    "-f", "x11grab",
                    "-framerate", "" + o.fps,
                    "-draw_mouse", "1",
                    "-i", System.getenv().getOrDefault(
                            "DISPLAY", ":0.0"
                    )
            ));
        }

        c.addAll(List.of(
                "-vf", filter,
                "-an",
                "-c:v", "libx264",
                "-preset", "ultrafast",
                "-tune", "zerolatency",
                "-profile:v", "baseline",
                "-b:v", o.bitrate,
                "-maxrate", o.bitrate,
                "-bufsize", o.bitrate,
                "-g", "" + (o.fps * 2),
                "-x264-params",
                "aud=1:repeat-headers=1:bframes=0",
                "-f", "h264",
                "pipe:1"
        ));

        return c;
    }

    private static List<String> parseInput(String input) {
        List<String> arguments = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        char quote = 0;
        boolean escaped = false;
        boolean started = false;

        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);

            if (escaped) {
                current.append(ch);
                escaped = false;
                continue;
            }

            if (ch == '\\' && quote != '\'') {
                if (i + 1 < input.length()) {
                    char next = input.charAt(i + 1);

                    if (next == quote
                            || next == '\\'
                            || (quote == 0 && Character.isWhitespace(next))) {
                        escaped = true;
                        continue;
                    }
                }

                current.append(ch);
                started = true;
                continue;
            }

            if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                } else {
                    current.append(ch);
                }

                continue;
            }

            if (ch == '"' || ch == '\'') {
                quote = ch;
                started = true;
                continue;
            }

            if (Character.isWhitespace(ch)) {
                if (started) {
                    arguments.add(current.toString());
                    current.setLength(0);
                    started = false;
                }

                continue;
            }

            current.append(ch);
            started = true;
        }

        if (escaped) {
            current.append('\\');
        }

        if (quote != 0) {
            throw new IllegalArgumentException(
                    "Unclosed quote in --input"
            );
        }

        if (started) {
            arguments.add(current.toString());
        }

        if (arguments.isEmpty()) {
            throw new IllegalArgumentException(
                    "Empty FFmpeg input arguments"
            );
        }

        return arguments;
    }

    private static String installHint() {
        String os = os();

        if (os.contains("win")) {
            return "winget install Gyan.FFmpeg "
                    + "(then reopen the terminal/IDE)";
        }

        if (os.contains("mac")) {
            return "brew install ffmpeg";
        }

        return "sudo apt install ffmpeg "
                + "(or your distribution's package manager)";
    }

    private static String macScreenIndex(String ffmpeg) {
        try {
            ProcessResult result = execute(
                    List.of(
                            ffmpeg,
                            "-hide_banner",
                            "-f", "avfoundation",
                            "-list_devices", "true",
                            "-i", ""
                    ),
                    DEVICE_TIMEOUT
            );

            Matcher m = Pattern.compile(
                    "\\[(\\d+)\\] Capture screen 0"
            ).matcher(result.output());

            if (m.find()) {
                return m.group(1);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

        } catch (Exception e) {
            Log.info(
                    "could not detect macOS screen device: "
                            + e.getMessage()
            );
        }

        return "1";
    }
}
