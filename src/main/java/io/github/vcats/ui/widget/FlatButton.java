package io.github.vcats.ui.widget;

import io.github.vcats.ui.theme.Theme;

import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;

@SuppressWarnings("serial")
public final class FlatButton extends JButton {
    public enum Variant {PRIMARY, DANGER, GHOST}

    private static final int GAP = 8;

    private final Variant variant;

    public FlatButton(String text, Variant variant) {
        super(text);
        this.variant = variant;
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFont(Theme.font(13f, variant == Variant.GHOST ? Font.PLAIN : Font.BOLD));
        setBorder(new EmptyBorder(0, 18, 0, 18));
        setAlignmentX(LEFT_ALIGNMENT);
    }

    @Override
    public Color getForeground() {
        if (variant == null) return super.getForeground();
        Theme.Palette p = Theme.palette();
        if (!isEnabled()) return p.muted();
        return switch (variant) {
            case PRIMARY -> p.onAccent();
            case DANGER -> Color.WHITE;
            case GHOST -> p.text();
        };
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        Insets in = getInsets();
        Icon icon = getIcon();
        int content = fm.stringWidth(getText());
        if (icon != null) content += icon.getIconWidth() + (getText().isEmpty() ? 0 : GAP);
        return new Dimension(content + in.left + in.right, Theme.CONTROL_HEIGHT);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.smooth(g);
        Theme.Palette p = Theme.palette();
        ButtonModel m = getModel();
        boolean pressed = m.isArmed() && m.isPressed();
        boolean hover = m.isRollover();

        Color fill = fillColor(p, pressed, hover);
        if (fill != null) {
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), Theme.ARC_CONTROL, Theme.ARC_CONTROL);
        }
        if (variant == Variant.GHOST) {
            g2.setColor(p.border());
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.ARC_CONTROL, Theme.ARC_CONTROL);
        }

        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        String text = getText();
        Icon icon = getIcon();
        int iconWidth = icon == null ? 0 : icon.getIconWidth();
        int total = iconWidth + (icon != null && !text.isEmpty() ? GAP : 0) + fm.stringWidth(text);
        int x = (getWidth() - total) / 2;
        if (icon != null) {
            icon.paintIcon(this, g2, x, (getHeight() - icon.getIconHeight()) / 2);
            x += iconWidth + GAP;
        }
        g2.setColor(getForeground());
        g2.drawString(text, x, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }

    private Color fillColor(Theme.Palette p, boolean pressed, boolean hover) {
        if (!isEnabled()) return variant == Variant.GHOST ? null : p.border();
        return switch (variant) {
            case PRIMARY -> pressed ? p.accentPressed() : hover ? p.accentHover() : p.accent();
            case DANGER -> Theme.blend(p.danger(), Color.BLACK, pressed ? 0.25 : hover ? 0.12 : 0);
            case GHOST -> pressed || hover ? p.hover() : null;
        };
    }
}
