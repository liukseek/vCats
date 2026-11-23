package io.github.vcats.ui.theme;

import io.github.vcats.util.AppPaths;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Logo {
    private static final String RESOURCE = "/assets/logo.png";
    private static final int[] ICON_SIZES = {16, 32, 48, 64, 128};

    private static boolean loaded;
    private static BufferedImage mark;

    private Logo() {
    }

    public static synchronized BufferedImage mark() {
        if (!loaded) {
            loaded = true;
            BufferedImage full = read();
            mark = full == null ? null : cropMark(full);
        }
        return mark;
    }

    public static List<Image> windowIcons() {
        List<Image> icons = new ArrayList<>();
        BufferedImage m = mark();
        if (m != null) for (int size : ICON_SIZES) icons.add(scale(m, size));
        return icons;
    }

    public static BufferedImage scale(BufferedImage source, int size) {
        BufferedImage current = source;
        int side = source.getWidth();
        while (side / 2 >= size) {
            side /= 2;
            current = resize(current, side);
        }
        return resize(current, size);
    }

    private static BufferedImage read() {
        try (InputStream in = Logo.class.getResourceAsStream(RESOURCE)) {
            if (in != null) return ImageIO.read(in);
        } catch (IOException ignored) {
        }
        try {
            Path file = AppPaths.find("assets/logo.png");
            if (file != null) return ImageIO.read(file.toFile());
        } catch (IOException ignored) {
        }
        return null;
    }

    private static BufferedImage cropMark(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int maxGap = Math.max(4, h / 40);
        int top = -1, bottom = -1, gap = 0;
        for (int y = 0; y < h; y++) {
            if (rowHasInk(img, y)) {
                if (top < 0) top = y;
                bottom = y;
                gap = 0;
            } else if (top >= 0 && ++gap > maxGap) {
                break;
            }
        }
        if (top < 0) return img;

        int left = w, right = -1;
        for (int x = 0; x < w; x++) {
            for (int y = top; y <= bottom; y++) {
                if (isInk(img.getRGB(x, y))) {
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    break;
                }
            }
        }
        if (right < left) return img;

        int cw = right - left + 1, ch = bottom - top + 1;
        int margin = (int) (Math.max(cw, ch) * 0.04);
        int side = Math.max(cw, ch) + 2 * margin;
        BufferedImage out = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, side, side);
        g.drawImage(img.getSubimage(left, top, cw, ch), (side - cw) / 2, (side - ch) / 2, null);
        g.dispose();
        return out;
    }

    private static boolean rowHasInk(BufferedImage img, int y) {
        for (int x = 0; x < img.getWidth(); x++) if (isInk(img.getRGB(x, y))) return true;
        return false;
    }

    private static boolean isInk(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        return Math.min(r, Math.min(g, b)) < 240;
    }

    private static BufferedImage resize(BufferedImage source, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(source, 0, 0, size, size, null);
        g.dispose();
        return out;
    }
}
