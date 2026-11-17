package main.net;

import main.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

final class TcpChannel implements Channel {
    private final String name;
    private final Consumer<String> onMessage;
    private final Runnable onClose;
    private final Socket sock;
    private volatile boolean closing;

    private TcpChannel(String name, Socket sock, Consumer<String> onMessage, Runnable onClose) {
        this.name = name;
        this.sock = sock;
        this.onMessage = onMessage;
        this.onClose = onClose;
    }

    static TcpChannel open(String host, int port, Consumer<String> onMessage, Runnable onClose) throws IOException {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), 5000);
        s.setTcpNoDelay(true);
        TcpChannel c = new TcpChannel(host + ":" + port, s, onMessage, onClose);
        Thread t = new Thread(c::readLoop, "tcp-" + port);
        t.setDaemon(true);
        t.start();
        return c;
    }

    private void readLoop() {
        try (InputStream in = sock.getInputStream()) {
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) onMessage.accept(new String(b, 0, n, StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
        if (!closing) {
            Log.info(name + " closed");
            onClose.run();
        }
    }

    @Override
    public synchronized void sendText(String s) {
        sendBinary(s.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public synchronized void sendBinary(byte[] b) {
        try {
            OutputStream o = sock.getOutputStream();
            o.write(b);
            o.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        closing = true;
        try {
            sock.close();
        } catch (IOException ignored) {
        }
    }
}
