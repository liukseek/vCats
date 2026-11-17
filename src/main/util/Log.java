package main.util;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class Log {

    private static final CopyOnWriteArrayList<Consumer<String>> listeners =
            new CopyOnWriteArrayList<>();

    private Log() {
    }

    public static void info(String message) {
        String line = "[vcast] " + message;

        System.out.println(line);

        for (Consumer<String> listener : listeners) {
            try {
                listener.accept(line);
            } catch (RuntimeException e) {
                System.err.println("[vcast] Log listener error: " + e);
            }
        }
    }

    public static void addListener(Consumer<String> listener) {
        listeners.add(listener);
    }

    public static void removeListener(Consumer<String> listener) {
        listeners.remove(listener);
    }
}