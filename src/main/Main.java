package main;

import main.config.Options;
import main.session.CastRunner;
import main.ui.ControlWindow;

import java.awt.GraphicsEnvironment;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {

        System.setOut(new PrintStream(
                new FileOutputStream(FileDescriptor.out),
                true,
                StandardCharsets.UTF_8
        ));

        if (containsHelp(args)) {
            printHelp();
            return;
        }

        if (args.length == 0) {

            if (GraphicsEnvironment.isHeadless()) {
                System.out.println(
                        "vCats: no graphical environment available."
                );
                System.out.println(
                        "Start the program with the PIN or IP address of the ViewBoard."
                );
                System.out.println();
                printHelp();
                return;
            }

            ControlWindow.open();
            return;
        }

        final Options options;

        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println();
            printHelp();
            return;
        }

        CastRunner runner = new CastRunner(phase -> {
            System.out.println("[vCats] State: " + phase);
        });

        Runtime.getRuntime().addShutdownHook(
                new Thread(runner::shutdown, "vcats-shutdown")
        );

        runner.run(options);
    }

    private static boolean containsHelp(String[] args) {
        for (String arg : args) {
            if (arg.equals("--help") || arg.equals("-h")) {
                return true;
            }
        }

        return false;
    }

    private static void printHelp() {

        System.out.println("vCats - ViewBoard Cast Sender");
        System.out.println();
        System.out.println("Usage:");
        System.out.println(
                "  java -jar vcats.jar <PIN|IP> [options]"
        );
        System.out.println();
        System.out.println("Options:");
        System.out.println(
                "  --name <name>          Device name shown on the receiver"
        );
        System.out.println(
                "  --type <type>          Connection type (manual/invited)"
        );
        System.out.println(
                "  --ffmpeg <path>        Path to the ffmpeg executable"
        );
        System.out.println(
                "  --input <arguments>    Custom ffmpeg input arguments"
        );
        System.out.println(
                "  --bitrate <value>      Video bitrate, e.g. 6M or 4500k (default: 6M)"
        );
        System.out.println(
                "  --fps <number>         Frame rate (1-60)"
        );
        System.out.println(
                "  --size <WxH>           Resolution (default: 1920x1080)"
        );
        System.out.println(
                "  --raw                  Use the plain TCP transport"
        );
        System.out.println(
                "  --ws                   Use the WebSocket transport"
        );
        System.out.println(
                "  --test                 Test mode (kitten image, test tone with --audio)"
        );
        System.out.println(
                "  --audio                Share system audio (experimental)"
        );
        System.out.println(
                "  --audio-device <name>  Audio capture device; implies --audio"
        );
        System.out.println(
                "  --help, -h             Show this message"
        );
        System.out.println();
        System.out.println("Examples:");
        System.out.println(
                "  java -jar vcats.jar 192.168.1.100"
        );
        System.out.println(
                "  java -jar vcats.jar 192.168.1.100 --fps 60 --size 1280x720"
        );
        System.out.println(
                "  java -jar vcats.jar 192.168.1.100 --audio --bitrate 8M"
        );
        System.out.println(
                "  java -jar vcats.jar 123456 --name vCats --ws"
        );
    }
}
