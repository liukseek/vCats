
package main.net;

import main.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

final class TcpChannel implements Channel {
    private final String name;
    private final Consumer<String> onMessage;
    private final Runnable onClose;
    private final Socket sock;

    private final AtomicBoolean closing = new AtomicBoolean();
    private final AtomicBoolean closeNotified = new AtomicBoolean();

    private TcpChannel(
            String name,
            Socket sock,
            Consumer<String> onMessage,
            Runnable onClose
    ) {
        this.name = name;
        this.sock = sock;
        this.onMessage = onMessage;
        this.onClose = onClose;
    }

    static TcpChannel open(
            String host,
            int port,
            Consumer<String> onMessage,
            Runnable onClose
    ) throws IOException {
        Socket s = new Socket();

        try {
            s.connect(new InetSocketAddress(host, port), 5000);
            s.setTcpNoDelay(true);

            TcpChannel c = new TcpChannel(
                    host + ":" + port,
                    s,
                    onMessage,
                    onClose
            );

            Thread t = new Thread(
                    c::readLoop,
                    "tcp-" + port
            );

            t.setDaemon(true);
            t.start();

            return c;

        } catch (IOException e) {
            try {
                s.close();
            } catch (IOException ignored) {
            }

            throw e;
        }
    }

    private void readLoop() {
        try (InputStream in = sock.getInputStream()) {
            byte[] buffer = new byte[8192];
            int n;

            while (!closing.get() && (n = in.read(buffer)) != -1) {
                if (n == 0) continue;

                onMessage.accept(
                        new String(
                                buffer,
                                0,
                                n,
                                StandardCharsets.UTF_8
                        )
                );
            }

        } catch (IOException e) {
            if (!closing.get()) {
                Log.info(name + " read error: " + e);
            }

        } catch (RuntimeException e) {
            Log.info(name + " message error: " + e);

        } finally {
            notifyClosed();
        }
    }

    @Override
    public synchronized void sendText(String s) {
        sendBinary(s.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public synchronized void sendBinary(byte[] b) {
        if (closing.get() || sock.isClosed()) {
            throw new IllegalStateException(
                    "TCP channel is closed"
            );
        }

        try {
            OutputStream out = sock.getOutputStream();
            out.write(b);
            out.flush();

        } catch (IOException e) {
            notifyClosed();
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        if (!closing.compareAndSet(false, true)) return;

        try {
            sock.close();
        } catch (IOException e) {
            Log.info(name + " close error: " + e);
        }

        notifyClosed();
    }

    private void notifyClosed() {
        closing.set(true);

        if (closeNotified.compareAndSet(false, true)) {
            try {
                onClose.run();
            } catch (RuntimeException e) {
                Log.info(name + " close callback error: " + e);
            }
        }
    }
}
