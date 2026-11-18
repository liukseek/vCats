
package main.util;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class Log {

    private static final CopyOnWriteArrayList<Consumer<String>> listeners =
            new CopyOnWriteArrayList<>();

    private Log() {
    }

    public static void info(String message) {
        String line = "[vcast] " + String.valueOf(message);

        System.out.println(line);

        for (Consumer<String> listener : listeners) {
            try {
                listener.accept(line);
            } catch (RuntimeException e) {
                System.err.println(
                        "[vcast] Log listener error: " + e
                );
            }
        }
    }

    public static void addListener(Consumer<String> listener) {
        Objects.requireNonNull(listener, "listener cannot be null");
        listeners.addIfAbsent(listener);
    }

    public static void removeListener(Consumer<String> listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }
}
