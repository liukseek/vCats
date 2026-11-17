package main.media;

import main.config.Options;
import main.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Ffmpeg {
    private Ffmpeg() {
    }

    private static String os() {
        return System.getProperty("os.name").toLowerCase();
    }

    public static void check(Options o) throws IOException, InterruptedException {
        try {
            Process p = new ProcessBuilder(o.ffmpeg, "-version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            if (p.waitFor() != 0) throw new IOException("exit code " + p.exitValue());
        } catch (IOException e) {
            throw new IOException("ffmpeg is not executable ('" + o.ffmpeg + "'): " + e.getMessage() + ". Install it with: " + installHint());
        }
        String os = os();
        if (!o.test && o.input == null && !os.contains("win") && !os.contains("mac") && "wayland".equalsIgnoreCase(System.getenv("XDG_SESSION_TYPE")))
            Log.info("WARNING: Wayland session, x11grab may capture only black or XWayland windows; use --input or an X11 session");
    }

    public static List<String> command(Options o) {
        List<String> c = new ArrayList<>(List.of(o.ffmpeg, "-hide_banner", "-loglevel", "error"));
        String os = os();
        if (o.test)
            c.addAll(List.of("-re", "-f", "lavfi", "-i", "testsrc2=size=" + o.width + "x" + o.height + ":rate=" + o.fps));
        else if (o.input != null) c.addAll(Arrays.asList(o.input.trim().split("\\s+")));
        else if (os.contains("win")) c.addAll(List.of("-f", "gdigrab", "-framerate", "" + o.fps, "-i", "desktop"));
        else if (os.contains("mac"))
            c.addAll(List.of("-f", "avfoundation", "-framerate", "" + o.fps, "-capture_cursor", "1", "-i", macScreenIndex(o.ffmpeg) + ":none"));
        else
            c.addAll(List.of("-f", "x11grab", "-framerate", "" + o.fps, "-draw_mouse", "1", "-i", System.getenv().getOrDefault("DISPLAY", ":0.0")));
        c.addAll(List.of("-vf", "scale=" + o.width + ":" + o.height + ",format=yuv420p", "-an", "-c:v", "libx264", "-preset", "ultrafast", "-tune", "zerolatency", "-profile:v", "baseline", "-b:v", o.bitrate, "-maxrate", o.bitrate, "-bufsize", o.bitrate, "-g", "" + (o.fps * 2), "-x264-params", "aud=1:repeat-headers=1:bframes=0", "-f", "h264", "pipe:1"));
        return c;
    }

    private static String installHint() {
        String os = os();
        if (os.contains("win")) return "winget install Gyan.FFmpeg   (then reopen the terminal/IDE)";
        if (os.contains("mac")) return "brew install ffmpeg";
        return "sudo apt install ffmpeg   (or your distribution's package manager)";
    }

    private static String macScreenIndex(String ffmpeg) {
        try {
            Process p = new ProcessBuilder(ffmpeg, "-hide_banner", "-f", "avfoundation", "-list_devices", "true", "-i", "").redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor(5, TimeUnit.SECONDS);
            Matcher m = Pattern.compile("\\[(\\d+)\\] Capture screen 0").matcher(out);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) {
        }
        return "1";
    }
}
