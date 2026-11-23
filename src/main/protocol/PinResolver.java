
package main.protocol;

import main.util.Log;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PinResolver {
    private static final String ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private PinResolver() {
    }

    public static String resolveHost(HttpClient http, String target)
            throws Exception {

        if (validIp(target)) return target;

        if (target == null || !target.matches("^[A-Za-z0-9]{6}$")) {
            throw new IllegalArgumentException(
                    "an IP address or a 6-character PIN is required"
            );
        }

        List<String> cands = new ArrayList<>();

        for (int o : localFirstOctets()) {
            String ip = pinToAddress(target, o);

            if (validIp(ip) && !cands.contains(ip)) {
                cands.add(ip);
            }
        }

        if (cands.isEmpty()) {
            throw new IOException("no candidate IP derived from the PIN");
        }

        Log.info("PIN candidates: " + cands);

        return probe(
                http,
                cands,
                Protocol.PORT_CONTROL,
                Duration.ofSeconds(3)
        );
    }

    static long pinCompute(String s, int base) {
        long r = 0;

        for (int n = 0; n < s.length(); n++) {
            int i = ALPHABET.indexOf(
                    Character.toUpperCase(s.charAt(n))
            );

            if (i >= 0) {
                r = r * base + i;
            }
        }

        return r;
    }

    static String pinToAddress(String pin, int firstOctet) {
        long n = pinCompute(pin.toUpperCase(), 32);

        long i = n % 1000 / 100;
        long s = n % 10000 / 1000;

        n = (n / 10000) * 10000
                + i * 1000
                + s * 100
                + n % 100;

        long c = 1073741823L - n;
        long a = c % (256L * 256 * 256);

        long u = a / (256 * 256);
        long p = a % (256 * 256) / 256;
        long h = a % 256;

        return firstOctet + "." + u + "." + p + "." + h;
    }

    static boolean validIp(String ip) {
        if (ip == null) return false;

        Matcher m = Pattern.compile(
                "^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$"
        ).matcher(ip);

        if (!m.matches()) return false;

        for (int k = 1; k <= 4; k++) {
            if (Integer.parseInt(m.group(k)) > 255) {
                return false;
            }
        }

        return true;
    }

    private static Set<Integer> localFirstOctets()
            throws SocketException {

        Set<Integer> out = new LinkedHashSet<>();

        for (NetworkInterface ni :
                Collections.list(NetworkInterface.getNetworkInterfaces())) {

            if (!ni.isUp() || ni.isLoopback()) continue;

            for (InetAddress ia :
                    Collections.list(ni.getInetAddresses())) {

                if (!(ia instanceof Inet4Address)) continue;

                String ip = ia.getHostAddress();

                if (ip.startsWith("100.115.92")) continue;

                out.add(
                        Integer.parseInt(
                                ip.substring(0, ip.indexOf('.'))
                        )
                );
            }
        }

        return out;
    }

    private static String probe(
            HttpClient http,
            List<String> hosts,
            int port,
            Duration timeout
    ) throws Exception {

        CompletableFuture<String> winner = new CompletableFuture<>();

        List<CompletableFuture<WebSocket>> attempts =
                new ArrayList<>();

        AtomicInteger failed = new AtomicInteger();

        for (String h : hosts) {
            CompletableFuture<WebSocket> f =
                    http.newWebSocketBuilder()
                            .connectTimeout(timeout)
                            .buildAsync(
                                    URI.create("ws://" + h + ":" + port),
                                    new WebSocket.Listener() {
                                    }
                            );

            attempts.add(f);

            f.whenComplete((ws, err) -> {
                if (err == null) {
                    winner.complete(h);

                } else if (failed.incrementAndGet() == hosts.size()) {
                    winner.completeExceptionally(
                            new IOException(
                                    "no reachable candidate: " + hosts
                            )
                    );
                }
            });
        }

        try {
            return winner.get(
                    timeout.toMillis() + 1000,
                    TimeUnit.MILLISECONDS
            );

        } finally {
            for (CompletableFuture<WebSocket> f : attempts) {
                f.thenAccept(ws ->
                        ws.sendClose(
                                WebSocket.NORMAL_CLOSURE,
                                "probe"
                        )
                );
            }
        }
    }
}
