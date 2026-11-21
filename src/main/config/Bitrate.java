package main.config;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Bitrate {
    public static final long MIN_KBPS = 100;
    public static final long MAX_KBPS = 100_000;

    private static final Pattern FORMAT =
            Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([kKmM]?)");

    private Bitrate() {
    }

    public static long parseKbps(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Missing bitrate");
        }

        Matcher matcher = FORMAT.matcher(value.trim());

        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Invalid bitrate: " + value
                            + ". Use a value such as 6M or 4500k"
                            + " (a bare number means kbit/s)"
            );
        }

        double number = Double.parseDouble(matcher.group(1));
        String suffix = matcher.group(2);

        double kbps = suffix.equalsIgnoreCase("m") ? number * 1000 : number;
        long rounded = Math.round(kbps);

        if (rounded < MIN_KBPS || rounded > MAX_KBPS) {
            throw new IllegalArgumentException(
                    "Bitrate out of range: " + value
                            + ". It must be between " + MIN_KBPS + "k and "
                            + (MAX_KBPS / 1000) + "M"
            );
        }

        return rounded;
    }
}
