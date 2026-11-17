
package main.session;

import main.config.Options;
import main.media.Ffmpeg;
import main.net.Transport;
import main.protocol.PinResolver;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class CastRunner {
    private final Consumer<Phase> phaseListener;
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private volatile CastSession session;

    public CastRunner(Consumer<Phase> phaseListener) {
        this.phaseListener = phaseListener;
    }

    private void setPhase(Phase phase) {
        phaseListener.accept(phase);
    }

    public void run(Options options) throws Exception {
        try {
            if (stopped.get()) return;

            Ffmpeg.check(options);

            setPhase(Phase.RESOLVING);
            String host = PinResolver.resolveHost(http, options.target);

            if (stopped.get()) return;

            setPhase(Phase.CONNECTING);
            Transport transport = Transport.choose(options.transport, host);

            CastSession current = new CastSession(
                    options, http, host, transport
            );

            session = current;

            if (stopped.get()) {
                current.shutdown();
                return;
            }

            setPhase(Phase.WAITING);
            current.run();

            if (stopped.get()) return;

            setPhase(Phase.CASTING);
            current.awaitEnd();

        } finally {
            shutdown();
            setPhase(Phase.STOPPED);
        }
    }

    public void stop() {
        stopped.set(true);

        CastSession current = session;
        if (current != null) {
            current.shutdown();
        }
    }

    public void shutdown() {
        stopped.set(true);

        CastSession current = session;
        if (current != null) {
            current.shutdown();
        }
    }
}
