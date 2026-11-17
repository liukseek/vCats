package main.ui.widget;

import main.ui.theme.Theme;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;

public final class ChevronIcon implements Icon {
    private static final int SIZE = 12;

    private boolean expanded;

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = Theme.smooth(g);
        g2.setColor(Theme.palette().muted());
        g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int cx = x + SIZE / 2, cy = y + SIZE / 2;
        if (expanded) {
            g2.drawLine(cx - 4, cy + 2, cx, cy - 2);
            g2.drawLine(cx, cy - 2, cx + 4, cy + 2);
        } else {
            g2.drawLine(cx - 4, cy - 2, cx, cy + 2);
            g2.drawLine(cx, cy + 2, cx + 4, cy - 2);
        }
        g2.dispose();
    }

    @Override
    public int getIconWidth() {
        return SIZE;
    }

    @Override
    public int getIconHeight() {
        return SIZE;
    }
}
