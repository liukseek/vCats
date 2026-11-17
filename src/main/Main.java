package main;

import main.config.Options;
import main.session.CastRunner;
import main.ui.ControlWindow;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public class Main {
    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        if (args.length == 0) {
            ControlWindow.open();
            return;
        }
        Options o = Options.parse(args);
        CastRunner runner = new CastRunner(_ -> {
        });
        Runtime.getRuntime().addShutdownHook(new Thread(runner::shutdown));
        runner.run(o);
        System.exit(0);
    }
}
