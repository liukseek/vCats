package main.audio;

import main.config.Options;
import main.util.AppPaths;
import main.util.Log;
import main.util.Processes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AudioSource {
    private static final long LIST_TIMEOUT = 8;

    private static final String MAC_HELPER = "native/macos/vcats-audio";

    private static final Pattern WINDOWS_LOOPBACK = Pattern.compile(
            "(?i)stereo\\s*mix|mix\\s*stereo|stereomix|missaggio|mixage|mezcla"
                    + "|what\\s*u\\s*hear|loopback|cable\\s*output"
                    + "|virtual-audio-capturer|voicemeeter|vb-audio"
    );

    private static final Pattern MAC_LOOPBACK = Pattern.compile(
            "(?i)blackhole|soundflower|loopback|vb-cable|ishowu"
                    + "|multi-output|aggregate"
    );

    private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");
    private static final Pattern MAC_ENTRY = Pattern.compile("\\[(\\d+)]\\s+(.+)$");

    private AudioSource() {
    }

    private static String os() {
        return System.getProperty("os.name").toLowerCase();
    }

    static List<String> command(Options o) throws IOException {
        if (o.audioDevice != null) {
            return fromDevice(o, o.audioDevice);
        }

        if (o.test) {
            Log.info("audio source: generated test tone");
            return testTone(o);
        }

        String os = os();

        if (os.contains("mac")) {
            return macOs(o);
        }

        if (os.contains("win")) {
            return windows(o);
        }

        return linux(o);
    }

    private static List<String> ffmpeg(Options o, String... inputArguments) {
        List<String> c = new ArrayList<>(List.of(
                o.ffmpeg,
                "-hide_banner",
                "-loglevel", "error"
        ));

        c.addAll(List.of(inputArguments));

        // Live devices deliver audio in irregular bursts. Without this the
        // gaps and the clock drift are written straight to the output as
        // clicks and crackles. aresample pads gaps with silence and nudges
        // the stream by a few samples to keep a steady 48 kHz clock.
        if (!List.of(inputArguments).contains("-af")) {
            c.addAll(List.of("-af", "aresample=async=1000:first_pts=0"));
        }

        c.addAll(List.of(
                "-flush_packets", "1",
                "-vn",
                "-f", "s16le",
                "-ar", "" + AudioCapture.SAMPLE_RATE,
                "-ac", "" + AudioCapture.CHANNELS,
                "pipe:1"
        ));

        return c;
    }

    private static List<String> testTone(Options o) {
        return ffmpeg(
                o,
                "-re",
                "-f", "lavfi",
                "-i", "sine=frequency=440:sample_rate=" + AudioCapture.SAMPLE_RATE,
                "-af", "volume=0.1"
        );
    }

    private static List<String> fromDevice(Options o, String device) {
        String os = os();

        Log.info("audio source: " + device);

        if (os.contains("mac")) {
            return ffmpeg(
                    o,
                    "-f", "avfoundation",
                    "-use_wallclock_as_timestamps", "1",
                    "-thread_queue_size", "1024",
                    "-i", ":" + device
            );
        }

        if (os.contains("win")) {
            return ffmpeg(
                    o,
                    "-f", "dshow",
                    "-use_wallclock_as_timestamps", "1",
                    "-audio_buffer_size", "100",
                    "-thread_queue_size", "1024",
                    "-i", "audio=" + device
            );
        }

        return ffmpeg(
                o,
                "-f", "pulse",
                "-use_wallclock_as_timestamps", "1",
                "-thread_queue_size", "1024",
                "-fragment_size", "" + AudioCapture.FRAME_BYTES,
                "-i", device
        );
    }

    private static List<String> windows(Options o) throws IOException {
        String device = windowsLoopbackDevice(o.ffmpeg);

        if (device == null) {
            throw new IOException(
                    "no system audio capture device found. "
                            + "Enable \"Stereo Mix\" in Sound settings "
                            + "(Recording tab, show disabled devices) or install "
                            + "a virtual audio cable, then run again "
                            + "(use --audio-device NAME to choose a device)"
            );
        }

        return fromDevice(o, device);
    }

    private static List<String> linux(Options o) {
        return fromDevice(o, linuxMonitor());
    }

    private static List<String> macOs(Options o) throws IOException {
        Path helper = AppPaths.find(MAC_HELPER);

        if (helper != null && Files.isExecutable(helper)) {
            Log.info("audio source: ScreenCaptureKit helper " + helper);
            return List.of(helper.toAbsolutePath().toString());
        }

        String index = macLoopbackIndex(o.ffmpeg);

        if (index == null) {
            throw new IOException(
                    "no system audio capture available. "
                            + "Build the ScreenCaptureKit helper "
                            + "(see the README) or install a loopback device "
                            + "such as BlackHole "
                            + "(use --audio-device INDEX to choose a device)"
            );
        }

        return fromDevice(o, index);
    }

    private static String linuxMonitor() {
        try {
            Processes.Result result = Processes.run(
                    List.of("pactl", "get-default-sink"),
                    3
            );

            String sink = result.output().trim();

            if (result.exitCode() == 0
                    && !sink.isEmpty()
                    && sink.indexOf('\n') < 0) {
                return sink + ".monitor";
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

        } catch (IOException ignored) {
        }

        return "@DEFAULT_MONITOR@";
    }

    private static String windowsLoopbackDevice(String ffmpeg) {
        try {
            Processes.Result result = Processes.run(
                    List.of(
                            ffmpeg,
                            "-hide_banner",
                            "-list_devices", "true",
                            "-f", "dshow",
                            "-i", "dummy"
                    ),
                    LIST_TIMEOUT
            );

            for (String name : dshowAudioDevices(result.output())) {
                if (WINDOWS_LOOPBACK.matcher(name).find()) {
                    return name;
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

        } catch (IOException e) {
            Log.info("could not list audio devices: " + e.getMessage());
        }

        return null;
    }

    static List<String> dshowAudioDevices(String output) {
        List<String> devices = new ArrayList<>();
        boolean audioSection = false;

        for (String line : output.split("\\R")) {
            if (line.contains("DirectShow audio devices")) {
                audioSection = true;
                continue;
            }

            if (line.contains("DirectShow video devices")) {
                audioSection = false;
                continue;
            }

            if (line.contains("Alternative name")) {
                continue;
            }

            Matcher matcher = QUOTED.matcher(line);

            if (!matcher.find()) {
                continue;
            }

            boolean tagged = line.contains("(audio)");
            boolean video = line.contains("(video)");

            if (tagged || (audioSection && !video)) {
                devices.add(matcher.group(1));
            }
        }

        return devices;
    }

    private static String macLoopbackIndex(String ffmpeg) {
        try {
            Processes.Result result = Processes.run(
                    List.of(
                            ffmpeg,
                            "-hide_banner",
                            "-f", "avfoundation",
                            "-list_devices", "true",
                            "-i", ""
                    ),
                    LIST_TIMEOUT
            );

            boolean audioSection = false;

            for (String line : result.output().split("\\R")) {
                if (line.contains("AVFoundation audio devices")) {
                    audioSection = true;
                    continue;
                }

                if (line.contains("AVFoundation video devices")) {
                    audioSection = false;
                    continue;
                }

                if (!audioSection) {
                    continue;
                }

                Matcher matcher = MAC_ENTRY.matcher(line.trim());

                if (matcher.find()
                        && MAC_LOOPBACK.matcher(matcher.group(2)).find()) {
                    return matcher.group(1);
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

        } catch (IOException e) {
            Log.info("could not list audio devices: " + e.getMessage());
        }

        return null;
    }
}
