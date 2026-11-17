package main.ui.widget;

import main.ui.theme.Theme;

import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

@SuppressWarnings("serial")
public final class Card extends JPanel {
    public Card() {
        setOpaque(false);
        setBorder(new EmptyBorder(14, 16, 16, 16));
        setAlignmentX(LEFT_ALIGNMENT);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.smooth(g);
        Theme.Palette p = Theme.palette();
        g2.setColor(p.surface());
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.ARC_CARD, Theme.ARC_CARD);
        g2.setColor(p.border());
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.ARC_CARD, Theme.ARC_CARD);
        g2.dispose();
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
}
