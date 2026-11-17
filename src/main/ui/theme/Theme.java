package main.ui.theme;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

public final class Theme {
    public record Palette(Color background, Color surface, Color input, Color border, Color text, Color muted,
                          Color accent, Color accentHover, Color accentPressed, Color onAccent,
                          Color danger, Color success, Color warning, Color log, Color logText, Color hover) {
    }

    public static final int ARC_CARD = 20;
    public static final int ARC_CONTROL = 14;
    public static final int CONTROL_HEIGHT = 36;

    private static final Palette LIGHT = new Palette(
            rgb(0xF4F5F7), rgb(0xFFFFFF), rgb(0xFFFFFF), rgb(0xD8DCE2), rgb(0x1F2328), rgb(0x667085),
            rgb(0x2F6FEB), rgb(0x2561D6), rgb(0x1D52BA), rgb(0xFFFFFF),
            rgb(0xD1242F), rgb(0x1A7F37), rgb(0xB7791F), rgb(0xF6F8FA), rgb(0x24292F), rgb(0xECEEF2));

    private static final Palette DARK = new Palette(
            rgb(0x0F1115), rgb(0x171A21), rgb(0x0F1115), rgb(0x2A2F3A), rgb(0xE6EDF3), rgb(0x8B949E),
            rgb(0x4C8DFF), rgb(0x6AA0FF), rgb(0x3A78E0), rgb(0x0B0D12),
            rgb(0xF85149), rgb(0x3FB950), rgb(0xD29922), rgb(0x0B0D12), rgb(0xC9D1D9), rgb(0x232833));

    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile boolean dark = systemPrefersDark();

    private Theme() {
    }

    public static Palette palette() {
        return dark ? DARK : LIGHT;
    }

    public static boolean isDark() {
        return dark;
    }

    public static void setDark(boolean value) {
        dark = value;
        for (Runnable r : LISTENERS) r.run();
    }

    public static void toggle() {
        setDark(!dark);
    }

    public static void addListener(Runnable listener) {
        LISTENERS.add(listener);
    }

    public static Font font(float size, int style) {
        Font base = UIManager.getFont("Label.font");
        if (base == null) base = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        return base.deriveFont(style, size);
    }

    public static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g2;
    }

    public static Color alpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    public static Color blend(Color a, Color b, double t) {
        return new Color(
                (int) Math.round(a.getRed() * (1 - t) + b.getRed() * t),
                (int) Math.round(a.getGreen() * (1 - t) + b.getGreen() * t),
                (int) Math.round(a.getBlue() * (1 - t) + b.getBlue() * t));
    }

    private static Color rgb(int value) {
        return new Color(value);
    }

    private static boolean systemPrefersDark() {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win"))
                return run("reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "/v", "AppsUseLightTheme").contains("0x0");
            if (os.contains("mac"))
                return run("defaults", "read", "-g", "AppleInterfaceStyle").toLowerCase().contains("dark");
            return run("gsettings", "get", "org.gnome.desktop.interface", "color-scheme").toLowerCase().contains("dark");
        } catch (Exception e) {
            return false;
        }
    }

    private static String run(String... command) throws Exception {
        Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(2, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return "";
        }
        return out;
    }
}
