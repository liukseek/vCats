
package io.github.vcats.ui;

import io.github.vcats.ui.theme.Theme;
import io.github.vcats.ui.widget.FlatScrollBarUI;
import io.github.vcats.util.Log;

import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;

@SuppressWarnings("serial")
final class LogPanel extends JPanel {
    private static final int MAX_LINES = 2000;
    private static final int TRIM_LINES = 500;

    private final JTextArea area = new JTextArea() {
        @Override
        public Color getForeground() {
            return Theme.palette().logText();
        }
    };

    private final JScrollPane scroll = new JScrollPane(area);

    LogPanel() {
        super(new BorderLayout());
        setOpaque(false);
        setBorder(new EmptyBorder(10, 12, 10, 4));
        setPreferredSize(new Dimension(100, 180));

        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUI(new FlatScrollBarUI());
        scroll.getVerticalScrollBar().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        add(scroll, BorderLayout.CENTER);

        Log.addListener(line ->
                SwingUtilities.invokeLater(() -> append(line))
        );
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.smooth(g);
        Theme.Palette p = Theme.palette();

        g2.setColor(p.log());
        g2.fillRoundRect(
                0, 0,
                getWidth() - 1,
                getHeight() - 1,
                Theme.ARC_CARD,
                Theme.ARC_CARD
        );

        g2.setColor(p.border());
        g2.drawRoundRect(
                0, 0,
                getWidth() - 1,
                getHeight() - 1,
                Theme.ARC_CARD,
                Theme.ARC_CARD
        );

        g2.dispose();
    }

    private void append(String line) {
        JScrollBar bar = scroll.getVerticalScrollBar();

        boolean follow = bar.getValue() + bar.getVisibleAmount()
                >= bar.getMaximum() - 4;

        area.append(line + "\n");

        if (area.getLineCount() > MAX_LINES) {
            try {
                area.replaceRange(
                        "",
                        0,
                        area.getLineEndOffset(TRIM_LINES)
                );
            } catch (BadLocationException ignored) {
            }
        }

        if (follow) {
            area.setCaretPosition(area.getDocument().getLength());
        }
    }

    void clear() {
        area.setText("");
    }
}
