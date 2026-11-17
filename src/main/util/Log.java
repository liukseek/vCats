package main.util;

public final class Log {
    private Log() {
    }

    public static void info(String s) {
        System.out.println("[vcast] " + s);
    }
}
