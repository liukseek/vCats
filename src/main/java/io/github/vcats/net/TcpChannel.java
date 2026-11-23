package io.github.vcats.net;

import io.github.vcats.util.Log;

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
    private final boolean rtsp;

    private final AtomicBoolean closing = new AtomicBoolean();
    private final AtomicBoolean closeNotified = new AtomicBoolean();

    private TcpChannel(
            String name,
            Socket sock,
            Consumer<String> onMessage,
            Runnable onClose,
            boolean rtsp
    ) {
        this.name = name;
        this.sock = sock;
        this.onMessage = onMessage;
        this.onClose = onClose;
        this.rtsp = rtsp;
    }

    static TcpChannel open(
            String host,
            int port,
            Consumer<String> onMessage,
            Runnable onClose,
            boolean rtsp
    ) throws IOException {

        Socket s = new Socket();

        try {
            s.connect(new InetSocketAddress(host, port), 5000);
            s.setTcpNoDelay(true);

            TcpChannel channel = new TcpChannel(
                    host + ":" + port,
                    s,
                    onMessage,
                    onClose,
                    rtsp
            );

            Thread thread = new Thread(
                    channel::readLoop,
                    "tcp-" + port
            );

            thread.setDaemon(true);
            thread.start();

            return channel;

        } catch (IOException | RuntimeException e) {
            try {
                s.close();
            } catch (IOException ignored) {
            }

            throw e;
        }
    }

    private void readLoop() {
        RtspFramer framer = rtsp ? new RtspFramer() : null;

        try (InputStream in = sock.getInputStream()) {
            byte[] buffer = new byte[8192];
            int n;

            while (!closing.get() && (n = in.read(buffer)) != -1) {
                if (n == 0) {
                    continue;
                }

                if (rtsp) {
                    framer.feed(buffer, n, onMessage);
                } else {
                    // Rappresentazione reversibile dei byte, senza UTF-8.
                    onMessage.accept(
                            new String(
                                    buffer,
                                    0,
                                    n,
                                    StandardCharsets.ISO_8859_1
                            )
                    );
                }
            }

            if (!closing.get() && rtsp) {
                framer.finish();
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
    public synchronized void sendBinary(byte[] data) {
        if (closing.get() || sock.isClosed()) {
            throw new IllegalStateException("TCP channel is closed");
        }

        try {
            OutputStream out = sock.getOutputStream();
            out.write(data);
            out.flush();

        } catch (IOException e) {
            notifyClosed();
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        if (!closing.compareAndSet(false, true)) {
            return;
        }

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
