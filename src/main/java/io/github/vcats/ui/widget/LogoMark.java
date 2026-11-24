package io.github.vcats.ui.widget;

import io.github.vcats.ui.theme.Logo;
import io.github.vcats.ui.theme.Theme;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

@SuppressWarnings("serial")
public final class LogoMark extends JComponent {
    private static final int SIZE = 56;
    private static final int PADDING = 4;

    private final transient BufferedImage image;

    public LogoMark() {
        BufferedImage mark = Logo.mark();
        image = mark == null ? null : Logo.scale(mark, SIZE * 2);
        setOpaque(false);
        setVisible(image != null);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(SIZE, SIZE);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (image == null) return;
        Graphics2D g2 = Theme.smooth(g);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setColor(Color.WHITE);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.ARC_CONTROL, Theme.ARC_CONTROL);
        g2.setColor(Theme.palette().border());
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.ARC_CONTROL, Theme.ARC_CONTROL);
        g2.drawImage(image, PADDING, PADDING, getWidth() - 2 * PADDING, getHeight() - 2 * PADDING, null);
        g2.dispose();
    }
}
