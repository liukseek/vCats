package main.ui.widget;

import main.ui.theme.Theme;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;

public final class FlatScrollBarUI extends BasicScrollBarUI {
    @Override
    protected JButton createDecreaseButton(int orientation) {
        return emptyButton();
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return emptyButton();
    }

    private static JButton emptyButton() {
        JButton b = new JButton();
        Dimension zero = new Dimension(0, 0);
        b.setPreferredSize(zero);
        b.setMinimumSize(zero);
        b.setMaximumSize(zero);
        return b;
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle bounds) {
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle bounds) {
        if (bounds.isEmpty() || !scrollbar.isEnabled()) return;
        Graphics2D g2 = Theme.smooth(g);
        g2.setColor(Theme.alpha(Theme.palette().muted(), isThumbRollover() ? 170 : 110));
        g2.fillRoundRect(bounds.x + 3, bounds.y + 2, bounds.width - 6, bounds.height - 4, 8, 8);
        g2.dispose();
    }

    @Override
    public Dimension getPreferredSize(JComponent c) {
        return new Dimension(12, super.getPreferredSize(c).height);
    }
}
