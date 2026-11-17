package main.ui.widget;

import main.ui.theme.Theme;

import javax.swing.JToggleButton;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;

@SuppressWarnings("serial")
public final class ToggleSwitch extends JToggleButton {
    private static final int TRACK_WIDTH = 38;
    private static final int TRACK_HEIGHT = 22;
    private static final int GAP = 10;

    public ToggleSwitch(String text) {
        super(text);
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFont(Theme.font(13f, Font.PLAIN));
        setAlignmentX(LEFT_ALIGNMENT);
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        return new Dimension(TRACK_WIDTH + GAP + fm.stringWidth(getText()), 28);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.smooth(g);
        Theme.Palette p = Theme.palette();
        if (!isEnabled()) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));

        int y = (getHeight() - TRACK_HEIGHT) / 2;
        g2.setColor(isSelected() ? p.accent() : Theme.alpha(p.muted(), 110));
        g2.fillRoundRect(0, y, TRACK_WIDTH, TRACK_HEIGHT, TRACK_HEIGHT, TRACK_HEIGHT);

        int thumb = TRACK_HEIGHT - 6;
        int tx = isSelected() ? TRACK_WIDTH - thumb - 3 : 3;
        g2.setColor(isSelected() ? p.onAccent() : Color.WHITE);
        g2.fillOval(tx, y + 3, thumb, thumb);

        g2.setFont(getFont());
        g2.setColor(p.text());
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(getText(), TRACK_WIDTH + GAP, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }
}
