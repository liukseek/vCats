package main.ui;

import main.config.Options;
import main.ui.theme.Theme;
import main.ui.widget.Card;
import main.ui.widget.ChevronIcon;
import main.ui.widget.FlatButton;
import main.ui.widget.FlatComboBox;
import main.ui.widget.FlatTextField;
import main.ui.widget.ThemedLabel;
import main.ui.widget.ToggleSwitch;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("serial")
final class OptionsForm extends JPanel {
    private record Choice(String value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private static final Pattern SIZE = Pattern.compile("(\\d+)\\s*x\\s*(\\d+)");
    private static final Pattern BITRATE = Pattern.compile("\\d+[kKmM]?");
    private static final int SECTION_GAP = 12;

    private final FlatTextField target = new FlatTextField("");
    private final FlatTextField name = new FlatTextField(new Options().name);
    private final FlatComboBox<Choice> type = new FlatComboBox<>(new Choice[]{
            new Choice("manual", "Manual (the board must accept)"),
            new Choice("invited", "Invited by the board")}, false);
    private final FlatComboBox<Choice> transport = new FlatComboBox<>(new Choice[]{
            new Choice("auto", "Automatic"),
            new Choice("raw", "Native TCP"),
            new Choice("ws", "WebSocket")}, false);
    private final FlatComboBox<String> size = new FlatComboBox<>(new String[]{"1920x1080", "1280x720", "1024x768", "2560x1440"}, true);
    private final FlatComboBox<String> fps = new FlatComboBox<>(new String[]{"15", "24", "30", "60"}, true);
    private final FlatTextField bitrate = new FlatTextField("3M");
    private final ToggleSwitch test = new ToggleSwitch("Send the test image (kittens) instead of the screen");
    private final FlatTextField ffmpeg = new FlatTextField("ffmpeg");
    private final FlatButton browse = new FlatButton("Browse", FlatButton.Variant.GHOST);
    private final FlatTextField input = new FlatTextField("");
    private final FlatButton advancedToggle = new FlatButton("Advanced settings", FlatButton.Variant.GHOST);
    private final ChevronIcon chevron = new ChevronIcon();
    private final Card advancedCard = new Card();

    private final List<JComponent> controls = List.of(target, name, type, transport, size, fps, bitrate, test, ffmpeg, browse, input);

    OptionsForm(IntConsumer onHeightChange) {
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        target.setPlaceholder("ABC123 or 192.168.1.50");
        input.setPlaceholder("Leave empty to capture the screen");
        size.setSelectedItem("1920x1080");
        fps.setSelectedItem("30");
        browse.addActionListener(e -> chooseFfmpeg());

        add(connectionCard());
        add(gap());
        add(videoCard());
        add(gap());
        add(advancedHeader(onHeightChange));
        add(advancedCard());
        advancedCard.setVisible(false);
    }

    private Card connectionCard() {
        Card card = new Card();
        card.setLayout(new GridBagLayout());
        card.add(title("Connection"), cell(0, 0, 2, true));
        card.add(labeled("PIN or IP address", target), cell(0, 1, 1, false));
        card.add(labeled("Device name", name), cell(1, 1, 1, true));
        card.add(labeled("Share type", type), cell(0, 2, 1, false));
        card.add(labeled("Transport", transport), cell(1, 2, 1, true));
        return card;
    }

    private Card videoCard() {
        Card card = new Card();
        card.setLayout(new GridBagLayout());
        card.add(title("Video"), cell(0, 0, 3, true));
        card.add(labeled("Resolution", size), cell(0, 1, 1, false));
        card.add(labeled("Frame rate", fps), cell(1, 1, 1, false));
        card.add(labeled("Bitrate", bitrate), cell(2, 1, 1, true));
        card.add(test, cell(0, 2, 3, true));
        return card;
    }

    private JPanel advancedHeader(IntConsumer onHeightChange) {
        advancedToggle.setIcon(chevron);
        advancedToggle.addActionListener(e -> {
            boolean show = !advancedCard.isVisible();
            advancedCard.setVisible(show);
            chevron.setExpanded(show);
            revalidate();
            repaint();
            onHeightChange.accept((show ? 1 : -1) * (advancedCard.getPreferredSize().height + SECTION_GAP));
        });
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.add(advancedToggle, BorderLayout.WEST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.CONTROL_HEIGHT));
        return row;
    }

    private Card advancedCard() {
        JPanel ffmpegRow = new JPanel(new BorderLayout(8, 0));
        ffmpegRow.setOpaque(false);
        ffmpegRow.add(ffmpeg, BorderLayout.CENTER);
        ffmpegRow.add(browse, BorderLayout.EAST);

        advancedCard.setLayout(new GridBagLayout());
        advancedCard.add(labeled("ffmpeg executable", ffmpegRow), cell(0, 0, 1, true));
        advancedCard.add(labeled("Custom ffmpeg input", input), cell(0, 1, 1, true));
        advancedCard.setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));
        return advancedCard;
    }

    private static JComponent gap() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        Dimension d = new Dimension(0, SECTION_GAP);
        p.setPreferredSize(d);
        p.setMinimumSize(d);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, SECTION_GAP));
        return p;
    }

    private static ThemedLabel title(String text) {
        return new ThemedLabel(text, ThemedLabel.Role.TEXT, 14f, Font.BOLD);
    }

    private static JPanel labeled(String text, JComponent field) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        panel.add(new ThemedLabel(text, ThemedLabel.Role.MUTED, 12f, Font.PLAIN), BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);
        return panel;
    }

    private static GridBagConstraints cell(int x, int y, int span, boolean last) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.gridwidth = span;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(y == 0 ? 0 : 14, 0, 0, last ? 0 : 14);
        return c;
    }

    private void chooseFfmpeg() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
            ffmpeg.setText(chooser.getSelectedFile().getAbsolutePath());
    }

    Options read() {
        Options o = new Options();
        String t = target.getText().trim();
        if (t.isEmpty()) throw new IllegalArgumentException("Enter the PIN or the IP address of the board.");
        o.target = t;

        String n = name.getText().trim();
        if (!n.isEmpty()) o.name = n;

        o.type = ((Choice) Objects.requireNonNull(type.getSelectedItem())).value();
        o.transport = ((Choice) Objects.requireNonNull(transport.getSelectedItem())).value();

        Matcher m = SIZE.matcher(size.text());
        if (!m.matches()) throw new IllegalArgumentException("The resolution must look like 1920x1080.");
        o.width = Integer.parseInt(m.group(1));
        o.height = Integer.parseInt(m.group(2));

        try {
            o.fps = Integer.parseInt(fps.text());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("The frame rate must be a whole number between 1 and 60.");
        }
        if (o.fps < 1 || o.fps > 60) throw new IllegalArgumentException("The frame rate must be between 1 and 60.");

        String b = bitrate.getText().trim();
        if (!BITRATE.matcher(b).matches()) throw new IllegalArgumentException("The bitrate must look like 3M or 2500k.");
        o.bitrate = b;

        String f = ffmpeg.getText().trim();
        if (!f.isEmpty()) o.ffmpeg = f;

        String in = input.getText().trim();
        o.input = in.isEmpty() ? null : in;

        o.test = test.isSelected();
        return o;
    }

    void setLocked(boolean locked) {
        for (JComponent c : controls) c.setEnabled(!locked);
        repaint();
    }
}
