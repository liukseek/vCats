package io.github.vcats.ui.widget;

import io.github.vcats.ui.theme.Theme;

import javax.swing.JLabel;
import javax.swing.border.EmptyBorder;
import java.awt.Color;

@SuppressWarnings("serial")
public final class ThemedLabel extends JLabel {
    public enum Role {TEXT, MUTED, DANGER}

    private final Role role;

    public ThemedLabel(String text, Role role, float size, int style) {
        super(text);
        this.role = role;
        setFont(Theme.font(size, style));
        setBorder(new EmptyBorder(0, 0, 0, 8));
        setAlignmentX(LEFT_ALIGNMENT);
    }

    @Override
    public Color getForeground() {
        if (role == null) return super.getForeground();
        Theme.Palette p = Theme.palette();
        return switch (role) {
            case TEXT -> p.text();
            case MUTED -> p.muted();
            case DANGER -> p.danger();
        };
    }
}
