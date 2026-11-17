package main.ui.widget;

import main.ui.theme.Theme;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;

@SuppressWarnings("serial")
public final class StatusPill extends JComponent {
    public enum Level {IDLE, BUSY, OK, ERROR}

    private static final int HEIGHT = 28;
    private static final int DOT = 8;

    private String text = "Idle";
    private Level level = Level.IDLE;

    public StatusPill() {
        setFont(Theme.font(12f, Font.BOLD));
        setOpaque(false);
    }

    public void show(Level level, String text) {
        this.level = level;
        this.text = text;
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        return new Dimension(14 + DOT + 8 + fm.stringWidth(text) + 14, HEIGHT);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.smooth(g);
        Theme.Palette p = Theme.palette();
        Color c = switch (level) {
            case IDLE -> p.muted();
            case BUSY -> p.warning();
            case OK -> p.success();
            case ERROR -> p.danger();
        };
        g2.setColor(Theme.alpha(c, 36));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
        g2.setColor(c);
        g2.fillOval(14, (getHeight() - DOT) / 2, DOT, DOT);
        g2.setFont(getFont());
        g2.setColor(p.text());
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, 14 + DOT + 8, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }
}
