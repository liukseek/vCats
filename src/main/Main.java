
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

        // Richiesta di aiuto
        if (containsHelp(args)) {
            printHelp();
            return;
        }

        // Nessun argomento: prova ad aprire la GUI
        if (args.length == 0) {

            if (GraphicsEnvironment.isHeadless()) {
                System.out.println(
                        "vCats: ambiente senza interfaccia grafica."
                );
                System.out.println(
                        "Avvia il programma specificando il PIN o l'IP della ViewBoard."
                );
                System.out.println();
                printHelp();
                return;
            }

            ControlWindow.open();
            return;
        }

        // Modalità terminale
        final Options options;

        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Errore: " + e.getMessage());
            System.err.println();
            printHelp();
            return;
        }

        CastRunner runner = new CastRunner(phase -> {
            System.out.println("[vCats] Stato: " + phase);
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
        System.out.println("Utilizzo:");
        System.out.println(
                "  java -jar vcats.jar <PIN|IP> [opzioni]"
        );
        System.out.println();
        System.out.println("Opzioni disponibili:");
        System.out.println(
                "  --name <nome>       Nome del dispositivo"
        );
        System.out.println(
                "  --type <tipo>       Tipo di connessione (manual/invited)"
        );
        System.out.println(
                "  --ffmpeg <percorso> Percorso dell'eseguibile FFmpeg"
        );
        System.out.println(
                "  --input <sorgente>  Sorgente multimediale"
        );
        System.out.println(
                "  --bitrate <valore>  Bitrate video (default: 6M)"
        );
        System.out.println(
                "  --fps <numero>      Frame rate (1-60)"
        );
        System.out.println(
                "  --size <WxH>        Risoluzione (default: 1920x1080)"
        );
        System.out.println(
                "  --raw               Usa il trasporto TCP"
        );
        System.out.println(
                "  --ws                Usa il trasporto WebSocket"
        );
        System.out.println(
                "  --test              Modalità test"
        );
        System.out.println(
                "  --audio             Abilita la condivisione audio (in sviluppo)"
        );
        System.out.println(
                "  --help, -h          Mostra questo messaggio"
        );
        System.out.println();
        System.out.println("Esempi:");
        System.out.println(
                "  java -jar vcats.jar 192.168.1.100"
        );
        System.out.println(
                "  java -jar vcats.jar 192.168.1.100 --fps 60 --size 1280x720"
        );
        System.out.println(
                "  java -jar vcats.jar 123456 --name vCats --ws"
        );
    }
}
