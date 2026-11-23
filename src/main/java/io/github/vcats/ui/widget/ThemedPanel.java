package io.github.vcats.ui.widget;

import io.github.vcats.ui.theme.Theme;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.LayoutManager;

@SuppressWarnings("serial")
public final class ThemedPanel extends JPanel {
    public ThemedPanel(LayoutManager layout) {
        super(layout);
        setOpaque(true);
    }

    @Override
    public Color getBackground() {
        return Theme.palette().background();
    }
}
