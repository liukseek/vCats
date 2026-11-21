
package main.config;

import java.net.InetAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Options {

    private static final Pattern SIZE_PATTERN =
            Pattern.compile("(\\d+)\\s*[xX]\\s*(\\d+)");

    public String target;
    public String name = hostName();
    public String type = "manual";
    public String ffmpeg = "ffmpeg";
    public String input;
    public String bitrate = "6M";
    public String transport = "auto";

    public int width = 1920;
    public int height = 1080;
    public int fps = 30;

    public boolean test;
    public boolean audio;

    public Options() {
    }

    public static Options parse(String[] args) {
        Options options = new Options();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            switch (arg) {
                case "--name":
                    options.name = requireValue(args, ++i, arg);
                    break;

                case "--type":
                    options.type = requireValue(args, ++i, arg);
                    break;

                case "--ffmpeg":
                    options.ffmpeg = requireValue(args, ++i, arg);
                    break;

                case "--input":
                    options.input = requireValue(args, ++i, arg);
                    break;

                case "--bitrate":
                    options.bitrate = requireValue(args, ++i, arg);
                    break;

                case "--fps":
                    options.fps = parseFps(requireValue(args, ++i, arg));
                    break;

                case "--size":
                    parseSize(options, requireValue(args, ++i, arg));
                    break;

                case "--audio":
                    options.audio = true;
                    break;
                case "--test":
                    options.test = true;
                    break;

                case "--raw":
                    options.transport = "raw";
                    break;

                case "--ws":
                    options.transport = "ws";
                    break;

                default:
                    if (arg.startsWith("--")) {
                        throw new IllegalArgumentException(
                                "Unknown option: " + arg
                        );
                    }

                    if (options.target == null) {
                        options.target = arg;
                    } else {
                        throw new IllegalArgumentException(
                                "Multiple targets specified: " + arg
                        );
                    }
            }
        }

        if (options.target == null || options.target.isBlank()) {
            throw new IllegalArgumentException(
                    "usage: java main.Main <PIN|IP> [options]"
            );
        }

        return options;
    }

    private static String requireValue(
            String[] args,
            int index,
            String option
    ) {
        if (index >= args.length || args[index].startsWith("--")) {
            throw new IllegalArgumentException(
                    "Missing value for " + option
            );
        }

        String value = args[index];

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    "Empty value for " + option
            );
        }

        return value;
    }

    private static int parseFps(String value) {
        try {
            int fps = Integer.parseInt(value);

            if (fps < 1 || fps > 60) {
                throw new IllegalArgumentException(
                        "FPS must be between 1 and 60"
                );
            }

            return fps;

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid FPS value: " + value,
                    e
            );
        }
    }

    private static void parseSize(Options options, String value) {
        Matcher matcher = SIZE_PATTERN.matcher(value);

        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Invalid resolution: " + value
                            + ". Expected WIDTHxHEIGHT"
            );
        }

        try {
            int width = Integer.parseInt(matcher.group(1));
            int height = Integer.parseInt(matcher.group(2));

            if (width < 1 || height < 1) {
                throw new IllegalArgumentException(
                        "Resolution dimensions must be positive"
                );
            }

            options.width = width;
            options.height = height;

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid resolution: " + value,
                    e
            );
        }
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "java-client";
        }
    }
}
