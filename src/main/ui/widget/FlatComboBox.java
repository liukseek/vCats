package main.ui.widget;

import main.ui.theme.Theme;

import javax.swing.ComboBoxEditor;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

@SuppressWarnings("serial")
public final class FlatComboBox<E> extends JComboBox<E> {
    public FlatComboBox(E[] items, boolean editable) {
        super(items);
        setOpaque(false);
        setFont(Theme.font(13f, Font.PLAIN));
        setBorder(new EmptyBorder(0, 10, 0, 0));
        setRenderer(new Renderer());
        setEditable(editable);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setAlignmentX(LEFT_ALIGNMENT);

        FocusAdapter repaint = new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                FlatComboBox.this.repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                FlatComboBox.this.repaint();
            }
        };
        addFocusListener(repaint);
        if (getEditor() != null && getEditor().getEditorComponent() != null)
            getEditor().getEditorComponent().addFocusListener(repaint);
        addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                FlatComboBox.this.repaint();
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                FlatComboBox.this.repaint();
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
                FlatComboBox.this.repaint();
            }
        });
    }

    public String text() {
        Object value = isEditable() ? getEditor().getItem() : getSelectedItem();
        return String.valueOf(value).trim();
    }

    @Override
    public void updateUI() {
        setUI(new FlatComboUI());
    }

    @Override
    public Color getForeground() {
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
        boolean focused = isFocusOwner() || isPopupVisible()
                || (isEditable() && getEditor().getEditorComponent().isFocusOwner());
        g2.setColor(isEnabled() ? p.input() : Theme.blend(p.input(), p.background(), 0.5));
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.ARC_CONTROL, Theme.ARC_CONTROL);
        g2.setColor(focused ? p.accent() : p.border());
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.ARC_CONTROL, Theme.ARC_CONTROL);
        g2.dispose();
        super.paintComponent(g);
    }

    private static final class FlatComboUI extends BasicComboBoxUI {
        @Override
        protected JButton createArrowButton() {
            return new ArrowButton();
        }

        @Override
        public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
        }

        @Override
        public void paintCurrentValue(Graphics g, Rectangle bounds, boolean hasFocus) {
            super.paintCurrentValue(g, bounds, false);
        }

        @Override
        protected ComboBoxEditor createEditor() {
            return new FlatEditor();
        }

        @Override
        protected ComboPopup createPopup() {
            BasicComboPopup popup = new BasicComboPopup(comboBox) {
                @Override
                public Color getBackground() {
                    return Theme.palette().surface();
                }
            };
            popup.setBorder(new LineBorder());
            return popup;
        }
    }

    private static final class ArrowButton extends JButton {
        ArrowButton() {
            setOpaque(false);
            setContentAreaFilled(false);
            setBorder(null);
            setFocusable(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.smooth(g);
            g2.setColor(Theme.palette().muted());
            g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int cx = getWidth() / 2, cy = getHeight() / 2;
            g2.drawLine(cx - 4, cy - 2, cx, cy + 2);
            g2.drawLine(cx, cy + 2, cx + 4, cy - 2);
            g2.dispose();
        }
    }

    private static final class FlatEditor extends BasicComboBoxEditor {
        @Override
        protected JTextField createEditorComponent() {
            JTextField field = new JTextField("", 9) {
                @Override
                public Color getForeground() {
                    return Theme.palette().text();
                }

                @Override
                public Color getCaretColor() {
                    return Theme.palette().text();
                }

                @Override
                public Color getSelectionColor() {
                    return Theme.alpha(Theme.palette().accent(), 90);
                }

                @Override
                public Color getSelectedTextColor() {
                    return Theme.palette().text();
                }
            };
            field.setOpaque(false);
            field.setBorder(null);
            field.setFont(Theme.font(13f, Font.PLAIN));
            return field;
        }
    }

    private static final class Renderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, false);
            Theme.Palette p = Theme.palette();
            setFont(Theme.font(13f, Font.PLAIN));
            setForeground(p.text());
            if (index < 0) {
                setOpaque(false);
                setBorder(new EmptyBorder(0, 0, 0, 0));
            } else {
                setOpaque(true);
                setBackground(isSelected ? p.hover() : p.surface());
                setBorder(new EmptyBorder(0, 12, 0, 12));
            }
            return this;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(super.getPreferredSize().width, 32);
        }
    }

    private static final class LineBorder implements Border {
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            g.setColor(Theme.palette().border());
            g.drawRect(x, y, width - 1, height - 1);
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(1, 1, 1, 1);
        }

        @Override
        public boolean isBorderOpaque() {
            return true;
        }
    }
}
