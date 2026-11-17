package main;

import main.config.Options;
import main.media.Ffmpeg;
import main.net.Transport;
import main.protocol.PinResolver;
import main.session.CastSession;
import main.util.Log;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

public class Main {
    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        Options o = Options.parse(args);
        Ffmpeg.check(o);
        HttpClient http = HttpClient.newHttpClient();
        String host = PinResolver.resolveHost(http, o.target);
        Log.info("board found: " + host);
        Transport transport = Transport.choose(o.transport, host);
        CastSession session = new CastSession(o, http, host, transport);
        Runtime.getRuntime().addShutdownHook(new Thread(session::shutdown));
        session.run();
        session.awaitEnd();
        session.shutdown();
        System.exit(0);
    }
}
