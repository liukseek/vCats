package main.ui.widget;

import main.ui.theme.Theme;

import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

@SuppressWarnings("serial")
public final class FlatTextField extends JTextField {
    private String placeholder = "";

    public FlatTextField(String text) {
        super(text);
        setOpaque(false);
        setBorder(new EmptyBorder(0, 12, 0, 12));
        setFont(Theme.font(13f, Font.PLAIN));
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                repaint();
            }
        });
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
        repaint();
    }

    @Override
    public Color getForeground() {
        return Theme.palette().text();
    }

    @Override
    public Color getCaretColor() {
        return Theme.palette().text();
    }

    @Override
    public Color getDisabledTextColor() {
        return Theme.palette().muted();
    }

    @Override
    public Color getSelectionColor() {
        return Theme.alpha(Theme.palette().accent(), 90);
    }

    @Override
    public Color getSelectedTextColor() {
        return Theme.palette().text();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(super.getPreferredSize().width, Theme.CONTROL_HEIGHT);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, Theme.CONTROL_HEIGHT);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.smooth(g);
        Theme.Palette p = Theme.palette();
        g2.setColor(isEnabled() ? p.input() : Theme.blend(p.input(), p.background(), 0.5));
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.ARC_CONTROL, Theme.ARC_CONTROL);
        g2.setColor(isFocusOwner() ? p.accent() : p.border());
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.ARC_CONTROL, Theme.ARC_CONTROL);
        g2.dispose();

        super.paintComponent(g);

        if (!placeholder.isEmpty() && getText().isEmpty()) {
            Graphics2D h = Theme.smooth(g);
            h.setFont(getFont());
            h.setColor(p.muted());
            FontMetrics fm = h.getFontMetrics();
            Insets in = getInsets();
            h.drawString(placeholder, in.left, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            h.dispose();
        }
    }
}
