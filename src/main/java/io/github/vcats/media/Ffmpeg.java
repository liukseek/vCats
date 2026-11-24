package io.github.vcats.media;

import io.github.vcats.config.Bitrate;
import io.github.vcats.config.Options;
import io.github.vcats.util.AppPaths;
import io.github.vcats.util.Log;
import io.github.vcats.util.MacPermissions;
import io.github.vcats.util.Processes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Ffmpeg {
    private static final String TEST_IMAGE = "assets/test-kittens.jpg";

    private static final long CHECK_TIMEOUT = 5;
    private static final long DEVICE_TIMEOUT = 5;

    private Ffmpeg() {
    }

    private static String os() {
        return System.getProperty("os.name").toLowerCase();
    }

    public static void check(Options o)
            throws IOException, InterruptedException {

        Processes.Result result;

        try {
            result = Processes.run(
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

        if (o.test || o.input != null) {
            return;
        }

        String os = os();

        if (os.contains("mac")) {
            MacPermissions.ensureScreenCapture();

        } else if (isLinux() && isWayland()) {
            String problem = WaylandCapture.unavailableReason();

            if (problem != null) {
                throw new IOException(problem);
            }
        }
    }

    private static boolean isLinux() {
        String os = os();
        return !os.contains("win") && !os.contains("mac");
    }

    static boolean isWayland() {
        return "wayland".equalsIgnoreCase(System.getenv("XDG_SESSION_TYPE"))
                || System.getenv("WAYLAND_DISPLAY") != null;
    }

    /**
     * On Wayland the screen can only be captured through the desktop portal
     * (the system "share your screen" dialog), which ffmpeg cannot talk to.
     * {@link WaylandCapture} negotiates it and serves raw frames to ffmpeg.
     */
    static boolean usePortal(Options o) {
        return !o.test
                && o.input == null
                && isLinux()
                && isWayland();
    }

    /** Everything started for one share: capture helper(s) and the encoder. */
    public static final class Capture implements AutoCloseable {
        private final List<Process> processes;
        private final WaylandCapture wayland;

        Capture(List<Process> processes, WaylandCapture wayland) {
            this.processes = processes;
            this.wayland = wayland;
        }

        /** The ffmpeg process whose stdout is the H.264 stream. */
        public Process encoder() {
            return processes.getLast();
        }

        @Override
        public void close() {
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

            if (wayland != null) {
                wayland.close();
            }
        }
    }

    /** Starts the capture and the ffmpeg encoder. */
    public static Capture start(Options o) throws IOException {
        WaylandCapture wayland = null;
        String rawInput = null;

        if (usePortal(o)) {
            wayland = WaylandCapture.start(even(o.width), even(o.height), o.fps);
            rawInput = "tcp://127.0.0.1:" + wayland.port();
        }

        try {
            Process encoder = new ProcessBuilder(command(o, rawInput))
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();

            return new Capture(List.of(encoder), wayland);

        } catch (IOException | RuntimeException e) {
            if (wayland != null) {
                wayland.close();
            }

            throw e;
        }
    }

    public static List<String> command(Options o) {
        return command(o, null);
    }

    static List<String> command(Options o, String rawInput) {
        List<String> c = new ArrayList<>(
                List.of(
                        o.ffmpeg,
                        "-hide_banner",
                        "-loglevel",
                        "error"
                )
        );

        String os = os();

        int width = even(o.width);
        int height = even(o.height);

        // Live captures (avfoundation especially) can deliver far more frames
        // than asked for; the h264 muxer keeps every one, so the board gets
        // 100+ fps and cannot keep up. Dropping to the target rate first also
        // saves the scaling work on frames that would be thrown away.
        String filter = "fps=" + o.fps + "," + fitFilter(width, height);

        Path image = o.test ? testImage() : null;

        if (image != null) {
            Log.info("test source: " + image);

            c.addAll(List.of(
                    "-re",
                    "-loop", "1",
                    "-framerate", "" + o.fps,
                    "-i", image.toAbsolutePath().toString()
            ));

            filter = "scale="
                    + Math.round(width * 1.12)
                    + ":" + Math.round(height * 1.12)
                    + ":force_original_aspect_ratio=increase,"
                    + "crop=" + width + ":" + height
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
                            + width + "x" + height
                            + ":rate=" + o.fps
            ));

        } else if (o.input != null) {
            c.addAll(parseInput(o.input));

        } else if (rawInput != null) {
            c.addAll(List.of(
                    "-f", "rawvideo",
                    "-pix_fmt", "yuv420p",
                    "-video_size", width + "x" + height,
                    "-framerate", "" + o.fps,
                    "-i", rawInput
            ));

        } else if (os.contains("win")) {
            c.addAll(List.of(
                    "-f", "gdigrab",
                    "-framerate", "" + o.fps,
                    "-draw_mouse", "1",
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

        long kbps = Bitrate.parseKbps(o.bitrate);

        c.addAll(List.of(
                "-vf", filter,
                "-an",
                "-c:v", "libx264",
                "-preset", "ultrafast",
                "-tune", "zerolatency",
                "-profile:v", "baseline",
                "-b:v", kbps + "k",
                "-maxrate", kbps + "k",
                "-bufsize", (kbps * 2) + "k",
                "-g", "" + (o.fps * 2),
                "-x264-params",
                "aud=1:repeat-headers=1:bframes=0",
                "-f", "h264",
                "pipe:1"
        ));

        return c;
    }

    static String fitFilter(int width, int height) {
        return "scale=" + width + ":" + height
                + ":force_original_aspect_ratio=decrease"
                + ",format=yuv420p"
                + ",pad=" + width + ":" + height
                + ":(ow-iw)/2:(oh-ih)/2"
                + ",setsar=1";
    }

    private static int even(int value) {
        return Math.max(2, value & ~1);
    }

    private static Path testImage() {
        Path found = AppPaths.find(TEST_IMAGE);

        if (found != null) {
            return found;
        }

        try (InputStream in = Ffmpeg.class.getResourceAsStream("/" + TEST_IMAGE)) {
            if (in == null) {
                return null;
            }

            Path temp = Files.createTempFile("vcats-test-", ".jpg");
            temp.toFile().deleteOnExit();
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);

            return temp;

        } catch (IOException e) {
            Log.info("could not extract the test image: " + e.getMessage());
            return null;
        }
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
            Processes.Result result = Processes.run(
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
