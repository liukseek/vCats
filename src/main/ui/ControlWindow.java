package main.ui;

import main.config.Options;
import main.session.CastRunner;
import main.session.Phase;
import main.ui.theme.Logo;
import main.ui.theme.Theme;
import main.ui.widget.FlatButton;
import main.ui.widget.LogoMark;
import main.ui.widget.StatusPill;
import main.ui.widget.ThemedLabel;
import main.ui.widget.ThemedPanel;
import main.util.Log;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

@SuppressWarnings("serial")
public final class ControlWindow extends JFrame {
    private static final Dimension MIN_SIZE = new Dimension(700, 640);

    private final OptionsForm form = new OptionsForm(this::resizeBy);
    private final LogPanel log = new LogPanel();
    private final StatusPill status = new StatusPill();
    private final ThemedLabel banner = new ThemedLabel("", ThemedLabel.Role.DANGER, 12f, Font.PLAIN);
    private final FlatButton start = new FlatButton("Start", FlatButton.Variant.PRIMARY);
    private final FlatButton stop = new FlatButton("Stop", FlatButton.Variant.DANGER);
    private final FlatButton clear = new FlatButton("Clear log", FlatButton.Variant.GHOST);
    private final FlatButton themeButton = new FlatButton("", FlatButton.Variant.GHOST);
    private volatile CastRunner runner;

    public static void open() {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            new ControlWindow().setVisible(true);
        });
    }

    private ControlWindow() {
        super("vCats");
        List<Image> icons = Logo.windowIcons();
        if (!icons.isEmpty()) setIconImages(icons);
        stop.setEnabled(false);
        banner.setVisible(false);
        updateThemeButton();

        start.addActionListener(e -> onStart());
        stop.addActionListener(e -> onStop());
        clear.addActionListener(e -> log.clear());
        themeButton.addActionListener(e -> Theme.toggle());
        Theme.addListener(() -> SwingUtilities.invokeLater(() -> {
            updateThemeButton();
            getContentPane().repaint();
        }));

        ThemedPanel root = new ThemedPanel(new BorderLayout(0, 14));
        root.setBorder(BorderFactory.createEmptyBorder(20, 20, 16, 20));
        root.add(top(), BorderLayout.NORTH);
        root.add(middle(), BorderLayout.CENTER);
        root.add(footer(), BorderLayout.SOUTH);
        setContentPane(root);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                CastRunner r = runner;
                if (r != null) r.shutdown();
                dispose();
                System.exit(0);
            }
        });

        pack();
        setMinimumSize(MIN_SIZE);
        setSize(Math.max(getWidth(), MIN_SIZE.width), Math.max(getHeight(), MIN_SIZE.height));
        setLocationRelativeTo(null);
    }

    private JPanel top() {
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(new ThemedLabel("vCats", ThemedLabel.Role.TEXT, 24f, Font.BOLD));
        titles.add(new ThemedLabel("Screen sharing for ViewBoard receivers", ThemedLabel.Role.MUTED, 12f, Font.PLAIN));

        JPanel brand = new JPanel(new BorderLayout(14, 0));
        brand.setOpaque(false);
        brand.add(new LogoMark(), BorderLayout.WEST);
        brand.add(titles, BorderLayout.CENTER);

        JPanel themeHolder = new JPanel(new GridBagLayout());
        themeHolder.setOpaque(false);
        themeHolder.add(themeButton);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(brand, BorderLayout.WEST);
        header.add(themeHolder, BorderLayout.EAST);

        JPanel top = new JPanel(new BorderLayout(0, 16));
        top.setOpaque(false);
        top.add(header, BorderLayout.NORTH);
        top.add(form, BorderLayout.CENTER);
        return top;
    }

    private JPanel middle() {
        JPanel middle = new JPanel(new BorderLayout(0, 8));
        middle.setOpaque(false);
        middle.add(banner, BorderLayout.NORTH);
        middle.add(log, BorderLayout.CENTER);
        return middle;
    }

    private JPanel footer() {
        JPanel statusHolder = new JPanel(new GridBagLayout());
        statusHolder.setOpaque(false);
        statusHolder.add(status);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(clear);
        buttons.add(stop);
        buttons.add(start);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(statusHolder, BorderLayout.WEST);
        footer.add(buttons, BorderLayout.EAST);
        return footer;
    }

    private void updateThemeButton() {
        themeButton.setText(Theme.isDark() ? "Light theme" : "Dark theme");
    }

    private void resizeBy(int delta) {
        setSize(getWidth(), Math.max(MIN_SIZE.height, getHeight() + delta));
    }

    private void setBanner(String text) {
        banner.setText(text);
        banner.setVisible(!text.isEmpty());
        banner.revalidate();
    }

    private void onStart() {
        Options o;
        try {
            o = form.read();
        } catch (IllegalArgumentException e) {
            setBanner(e.getMessage());
            return;
        }
        setBanner("");
        CastRunner r = new CastRunner(this::onPhase);
        runner = r;
        setRunning(true);
        Thread t = new Thread(() -> {
            try {
                r.run(o);
            } catch (Exception e) {
                String message = e.getMessage() != null ? e.getMessage() : e.toString();
                Log.info("error: " + message);
                SwingUtilities.invokeLater(() -> {
                    setBanner(message);
                    status.show(StatusPill.Level.ERROR, "Failed");
                });
            } finally {
                SwingUtilities.invokeLater(() -> setRunning(false));
            }
        }, "vcast-session");
        t.setDaemon(true);
        t.start();
    }

    private void onStop() {
        CastRunner r = runner;
        if (r == null) return;
        stop.setEnabled(false);
        status.show(StatusPill.Level.BUSY, "Stopping...");
        r.stop();
    }

    private void setRunning(boolean running) {
        form.setLocked(running);
        start.setEnabled(!running);
        stop.setEnabled(running);
    }

    private void onPhase(Phase phase) {
        SwingUtilities.invokeLater(() -> {
            switch (phase) {
                case RESOLVING -> status.show(StatusPill.Level.BUSY, "Looking for the board");
                case CONNECTING -> status.show(StatusPill.Level.BUSY, "Connecting");
                case WAITING -> status.show(StatusPill.Level.BUSY, "Waiting for the board to accept");
                case CASTING -> status.show(StatusPill.Level.OK, "Sharing the screen");
                case STOPPED -> status.show(StatusPill.Level.IDLE, "Stopped");
            }
        });
    }
}
