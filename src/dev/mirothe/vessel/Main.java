package dev.mirothe.vessel;

import com.formdev.flatlaf.FlatLaf;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import java.util.stream.Stream;

public final class Main extends JFrame {
    private static final Preferences PREFS = Preferences.userNodeForPackage(Main.class);
    private static final String RELEASES = "Mirothe/Hogyoku-Vessel";
    private static final String ICONS = Stream.of("Segoe Fluent Icons", "Segoe MDL2 Assets")
            .filter(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())::contains)
            .findFirst().orElse(null);

    private final JTree tree = new JTree(new DefaultMutableTreeNode());
    private final JTabbedPane tabs = new Chrome.Bare();
    private final JTabbedPane panel = new JTabbedPane();
    private final DefaultListModel<Preview.Problem> problems = new DefaultListModel<>();
    private final JList<Preview.Problem> problemList = new JList<>(problems);
    private final JTextArea log = new JTextArea();
    private final JLabel explorerTitle = new JLabel();
    private final JLabel statusProblems = statusCell("");
    private final JLabel statusBusy = statusCell("");
    private final JLabel statusCaret = statusCell("");
    private final JLabel statusFps = statusCell("");
    private final JLabel problemMark = new JLabel();
    private final JPanel previewSide = new JPanel(new BorderLayout());
    private final Chrome.EffectBar effectBar = new Chrome.EffectBar(this::open, this::chipMenu, this::newEffect);
    private final Chrome.SaveButton saveButton = new Chrome.SaveButton(this::saveAll);
    private final JButton undoButton = editIcon("\uE7A7", "Undo (Ctrl+Z)", () -> { if (current() != null) current().undo(); });
    private final JButton redoButton = editIcon("\uE7A6", "Redo (Ctrl+Y)", () -> { if (current() != null) current().redo(); });
    private final Chrome.Rail rail = new Chrome.Rail(List.of(
            new Chrome.Rail.Item("home", "\uE80F", "Home", () -> showHome("home")),
            new Chrome.Rail.Item("work", "\uE943", "Editor", this::showWork),
            new Chrome.Rail.Item("examples", "\uE7B3", "Examples", () -> showHome("examples")),
            new Chrome.Rail.Item("learn", "\uE736", "Learn", () -> showHome("learn"))),
            new Chrome.Rail.Item("settings", "\uE713", "Settings", () -> showHome("settings")));
    private final JCheckBoxMenuItem obfuscate = new JCheckBoxMenuItem("Obfuscate", true);
    private final JCheckBoxMenuItem check = new JCheckBoxMenuItem("Check Shaders", true);
    private final JCheckBoxMenuItem skript = new JCheckBoxMenuItem("Also Make a Skript", false);
    private final CardLayout editorCards = new CardLayout();
    private final Fx.Reveal editorArea = new Fx.Reveal();
    private Home home;
    private final Fx.Loader loader = new Fx.Loader();
    private final Timer previewDelay = new Timer(350, e -> refreshPreview());
    private JSplitPane all;
    private JSplitPane editorAndPreview;
    private JSplitPane middle;
    private final JPanel sidebar = new JPanel(new BorderLayout());
    private final JPanel right = new JPanel(new BorderLayout());
    private final CardLayout sideCards = new CardLayout();
    private final JPanel sideStack = new JPanel(sideCards);
    private final Fx.Island sideCard = new Fx.Island(sideStack, 2);
    private int filesWidth = 260;
    private final Fx.Island editorCard = new Fx.Island(editorArea, 2);
    private final Fx.Island previewCard = new Fx.Island(right, 2);
    private final Fx.Island consoleCard = new Fx.Island(panel, 2);
    private Preview preview;
    private Learn.Panel lessonPanel;
    private boolean homeShowing = true;
    private final CardLayout screenCards = new CardLayout();
    private final JPanel screens = new JPanel(screenCards);
    private Path lessonFile;
    private Project project;
    private boolean building;
    private final JButton projectWidget = new JButton();

    private Main() {
        super(Forge.TOOL);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(1500, 900);
        setLocationRelativeTo(null);
        previewDelay.setRepeats(false);
        java.net.URL icon = Main.class.getResource("/icon.png");
        if (icon != null) setIconImage(new javax.swing.ImageIcon(icon).getImage());
        setJMenuBar(menus());
        getRootPane().putClientProperty("JRootPane.titleBarShowTitle", false);
        getRootPane().putClientProperty("JRootPane.titleBarShowIcon", false);
        editorArea.setLayout(editorCards);
        editorArea.setBackground(Fx.t().editor());
        editorArea.setOpaque(false);

        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setBackground(Fx.SIDE);
        tree.addMouseListener(new MouseAdapter() {
            // on press rather than click: swing drops the click if the mouse moves a pixel between press and release
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    Path p = pathAt(e);
                    if (p != null && Files.isRegularFile(p)) open(p);
                }
                popup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                popup(e);
            }
        });
        tree.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "openFile");
        tree.getActionMap().put("openFile", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                TreePath tp = tree.getSelectionPath();
                if (tp != null && ((DefaultMutableTreeNode) tp.getLastPathComponent()).getUserObject() instanceof Entry entry
                        && Files.isRegularFile(entry.path())) open(entry.path());
            }
        });
        JScrollPane treeScroll = new JScrollPane(tree);
        treeScroll.setBorder(null);
        Fx.smooth(treeScroll, false);
        sidebar.add(toolHeader("Project", explorerTitle, () -> toggle(sideCard, this.all)), BorderLayout.NORTH);
        sidebar.add(treeScroll, BorderLayout.CENTER);
        sidebar.setOpaque(false);
        sideStack.setOpaque(false);
        sideStack.add(sidebar, "files");
        explorerTitle.setText("");

        tabs.addChangeListener(e -> {
            boolean empty = tabs.getTabCount() == 0;
            editorCards.show(editorArea, empty ? "welcome" : "tabs");
            updateCaret();
            refreshPreview();
            presence();
            refreshBar();
        });
                home = new Home(new Home.Hooks(this::newProject, this::openProject, this::openProject, this::newEffect, this::pickBuild,
                this::startFrom, this::startLesson, this::backToEditor, () -> checkForUpdate(true),
                () -> browse("https://github.com/" + RELEASES + "/releases"), () -> project, () -> project != null || tabs.getTabCount() > 0,
                Main::recent, obfuscate, check, skript, t -> SwingUtilities.invokeLater(() -> changeTheme(t)), this::openEffect));
        editorArea.add(emptyEditor(), "welcome");
        getRootPane().registerKeyboardAction(e -> {
            if (homeShowing && (project != null || tabs.getTabCount() > 0)) backToEditor();
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        editorArea.add(tabs, "tabs");

        problemList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Preview.Problem p = problemList.getSelectedValue();
                CodeEditor ed = current();
                if (p != null && ed != null) ed.goToLine(p.line());
            }
        });
        problemList.setCellRenderer((list, value, index, selected, focus) -> {
            String where = value.line() > 0 ? "  <font color='" + Fx.hex(Fx.t().dim()) + "'>:" + value.line() + "</font>" : "";
            JLabel l = new JLabel("<html>" + glyphHtml("", Fx.hex(Fx.t().error())) + "&nbsp; " + escape(value.message()) + where + "</html>");
            l.setOpaque(true);
            l.setBorder(BorderFactory.createEmptyBorder(3, 14, 3, 8));
            l.setBackground(selected ? Fx.t().select() : Fx.SIDE);
            l.setForeground(Fx.TEXT);
            return l;
        });
        problemList.setBackground(Fx.SIDE);

        log.setEditable(false);
        log.setFont(CodeEditor.mono(13));
        log.setBackground(Fx.SIDE);
        log.setForeground(Fx.TEXT);
        log.setMargin(new java.awt.Insets(4, 14, 4, 14));

        JScrollPane problemScroll = borderless(new JScrollPane(problemList));
        JScrollPane logScroll = borderless(new JScrollPane(log));
        Fx.smooth(problemScroll, false);
        Fx.smooth(logScroll, false);
        panel.addTab("Problems", problemScroll);
        panel.addTab("Log", logScroll);
        panel.setBackground(Fx.SIDE);
        panel.putClientProperty("JTabbedPane.tabHeight", 30);
        panel.putClientProperty("JTabbedPane.trailingComponent", toolActions(() -> toggle(consoleCard, this.middle)));

        right.setOpaque(false);
        try {
            preview = new Preview(this::showProblems);
            preview.setMinimumSize(new Dimension());
            right.add(preview, BorderLayout.CENTER);
            preview.onStats(statusFps::setText);
        } catch (Throwable t) {
            JLabel sorry = new JLabel("<html><div style='width:260px'>The preview couldn't start. It needs OpenGL 3.3.<br><br><font color='"
                    + Fx.hex(Fx.DIM) + "'>" + escape(String.valueOf(t)) + "</font></div></html>");
            sorry.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
            sorry.setVerticalAlignment(JLabel.TOP);
            right.add(sorry, BorderLayout.CENTER);
        }

        // zero minimums so the islands can close all the way
        for (JComponent c : new JComponent[]{sideCard, editorCard, previewCard, consoleCard}) c.setMinimumSize(new Dimension());
        previewSide.setOpaque(false);
        previewSide.setMinimumSize(new Dimension());
        previewSide.add(previewCard, BorderLayout.CENTER);
        previewSide.add(footer(), BorderLayout.SOUTH);
        editorAndPreview = split(JSplitPane.HORIZONTAL_SPLIT, editorCard, previewSide, 0.55);
        middle = split(JSplitPane.VERTICAL_SPLIT, editorAndPreview, consoleCard, 0.78);
        all = split(JSplitPane.HORIZONTAL_SPLIT, sideCard, middle, 0);
        all.setDividerLocation(260);
        editorAndPreview.setMinimumSize(new Dimension());
        middle.setMinimumSize(new Dimension());
        // the files and the console stay out of the way until they're asked for
        sideCard.setVisible(false);
        consoleCard.setVisible(false);

        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(Fx.BACKDROP);
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(loader, BorderLayout.NORTH);
        body.add(all, BorderLayout.CENTER);
        center.add(body, BorderLayout.CENTER);
        effectBar.onClose(file -> closeFile(file));
        JPanel editActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        editActions.setOpaque(false);
        editActions.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
        editActions.add(undoButton);
        editActions.add(redoButton);
        editActions.add(Box.createHorizontalStrut(6));
        editActions.add(saveButton);
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        projectWidget.putClientProperty("JButton.buttonType", "toolBarButton");
        projectWidget.setFocusable(false);
        projectWidget.setToolTipText("Project");
        projectWidget.addActionListener(e -> projectMenu());
        updateProjectWidget();
        JPanel projectSide = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        projectSide.setOpaque(false);
        projectSide.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 0));
        projectSide.add(projectWidget);
        JComponent divider = new JComponent() {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                g.setColor(Fx.t().border());
                g.fillRect(getWidth() / 2, 10, 1, getHeight() - 20);
            }
        };
        divider.setPreferredSize(new Dimension(17, 46));
        projectSide.add(divider);
        header.add(projectSide, BorderLayout.WEST);
        header.add(effectBar, BorderLayout.CENTER);
        header.add(editActions, BorderLayout.EAST);
        center.add(header, BorderLayout.NORTH);
        center.setBorder(BorderFactory.createEmptyBorder(0, 4, 10, 10));
        // home takes the whole window, the workspace is everything else, and only one shows at a time
        screens.setBackground(Fx.BACKDROP);
        screens.add(home, "home");
        screens.add(center, "work");
        add(rail, BorderLayout.WEST);
        add(screens, BorderLayout.CENTER);
        getContentPane().setBackground(Fx.BACKDROP);
        showWelcome();
        showProblems(List.of());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (confirmUnsaved()) {
                    // no dispose, tearing down the gl canvas can throw and then exit never ran, leaving the process behind
                    setVisible(false);
                    System.exit(0);
                }
            }
        });
    }

    private JMenuBar menus() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        file.add(item("New Project...", null, this::newProject));
        file.add(item("Open Project...", KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK), this::openProject));
        JMenu recent = new JMenu("Open Recent");
        recent.addMenuListener(new MenuListener() {
            public void menuSelected(MenuEvent e) {
                recent.removeAll();
                for (String dir : recent()) recent.add(item(dir, null, () -> openProject(Path.of(dir))));
                if (recent.getItemCount() == 0) recent.add(new JMenuItem("Nothing yet")).setEnabled(false);
            }
            public void menuDeselected(MenuEvent e) { }
            public void menuCanceled(MenuEvent e) { }
        });
        file.add(recent);
        file.add(item("Project Settings...", KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, InputEvent.CTRL_DOWN_MASK), this::projectSettings));
        file.addSeparator();
        file.add(item("New Effect...", KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK), this::newEffect));
        file.add(item("Save", KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), this::saveCurrent));
        file.add(item("Save All", KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), this::saveAll));
        file.add(item("Close Editor", KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK), this::closeCurrent));
        file.addSeparator();
        file.add(item("Exit", null, () -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING))));

        JMenu view = new JMenu("View");
        view.add(item("Home", KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK), () -> showHome("home")));
        view.add(item("Command Palette...", KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), this::showCommands));
        view.add(item("Go to File...", KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK), this::showFiles));
        view.addSeparator();
        view.add(item("Files", KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), () -> toggle(sideCard, all)));
        view.add(item("Preview", KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), () -> toggle(previewSide, editorAndPreview)));
        view.add(item("Console", KeyStroke.getKeyStroke(KeyEvent.VK_J, InputEvent.CTRL_DOWN_MASK), () -> toggle(consoleCard, middle)));

        JMenu build = new JMenu("Build");
        build.add(item("Build Pack", KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK), this::build));
        build.addSeparator();
        obfuscate.setSelected(PREFS.getBoolean("obfuscate", true));
        check.setSelected(PREFS.getBoolean("check", true));
        obfuscate.addActionListener(e -> PREFS.putBoolean("obfuscate", obfuscate.isSelected()));
        check.addActionListener(e -> PREFS.putBoolean("check", check.isSelected()));
        skript.setSelected(PREFS.getBoolean("skript", false));
        skript.addActionListener(e -> PREFS.putBoolean("skript", skript.isSelected()));
        skript.setToolTipText("Writes a .sk file next to the pack with commands to spawn the skies and objects");
        build.add(obfuscate);
        build.add(check);
        build.add(skript);

        JMenu help = new JMenu("Help");
        help.add(item("Learn Shaders", KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), this::showLearn));
        help.addSeparator();
        help.add(item("Check for Updates", null, () -> checkForUpdate(true)));
        help.add(item("Releases on GitHub", null, () -> browse("https://github.com/" + RELEASES + "/releases")));

        JMenu main = new JMenu(ICONS != null ? "\uE700" : "Menu");
        if (ICONS != null) main.setFont(new Font(ICONS, Font.PLAIN, 14));
        main.setToolTipText("Main menu");
        main.add(file);
        main.add(view);
        main.add(build);
        main.add(help);
        bar.add(main);
        bar.add(Box.createHorizontalGlue());
        return bar;
    }

    private static JMenuItem item(String text, KeyStroke key, Runnable action) {
        JMenuItem i = new JMenuItem(text);
        if (key != null) i.setAccelerator(key);
        i.addActionListener(e -> action.run());
        return i;
    }

    private List<Palette.Item> commands() {
        List<Palette.Item> list = new ArrayList<>(List.of(
                new Palette.Item("Build Pack", "Ctrl+B", this::build),
                new Palette.Item("New Effect", "Ctrl+N", this::newEffect),
                new Palette.Item("Go to File", "Ctrl+P", this::showFiles),
                new Palette.Item("Save", "Ctrl+S", this::saveCurrent),
                new Palette.Item("Save All", "Ctrl+Shift+S", this::saveAll),
                new Palette.Item("Close Editor", "Ctrl+W", this::closeCurrent),
                new Palette.Item("Toggle Obfuscate", obfuscate.isSelected() ? "on" : "off", () -> flip(obfuscate)),
                new Palette.Item("Toggle Shader Check", check.isSelected() ? "on" : "off", () -> flip(check)),
                new Palette.Item("Toggle Skript Output", skript.isSelected() ? "on" : "off", () -> flip(skript)),
                new Palette.Item("Toggle Files", "Ctrl+Shift+E", () -> toggle(sideCard, all)),
                new Palette.Item("Toggle Preview", "Ctrl+Shift+V", () -> toggle(previewSide, editorAndPreview)),
                new Palette.Item("Toggle Console", "Ctrl+J", () -> toggle(consoleCard, middle)),
                new Palette.Item("Go Home", "Ctrl+H", () -> showHome("home")),
                new Palette.Item("Examples", null, () -> showHome("examples")),
                new Palette.Item("Settings", null, () -> showHome("settings")),
                new Palette.Item("Learn Shaders", "F1", this::showLearn),
                new Palette.Item("New Project", null, this::newProject),
                new Palette.Item("Project Settings", "Ctrl+,", this::projectSettings),
                new Palette.Item("Open Project", "Ctrl+O", this::openProject),
                new Palette.Item("Check for Updates", null, () -> checkForUpdate(true))));
        if (project != null) list.add(new Palette.Item("Reveal Project in File Explorer", null, () -> reveal(project.dir)));
        for (String dir : recent()) list.add(new Palette.Item("Open Recent: " + Path.of(dir).getFileName(), shorten(dir), () -> openProject(Path.of(dir))));
        return list;
    }

    private void showCommands() {
        Palette.show(this, "Type a command", commands());
    }

    private void showFiles() {
        if (project == null) {
            showCommands();
            return;
        }
        List<Palette.Item> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(project.dir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> !project.dir.relativize(p).toString().startsWith("build"))
                    .sorted()
                    .forEach(p -> {
                        Path parent = project.dir.relativize(p).getParent();
                        files.add(new Palette.Item(p.getFileName().toString(), parent == null ? "" : parent.toString().replace('\\', '/'), () -> open(p)));
                    });
        } catch (IOException ex) {
            error("Couldn't list the project files", ex);
            return;
        }
        Palette.show(this, "Search files by name", files);
    }

    private void flip(JCheckBoxMenuItem box) {
        box.doClick();
        Fx.toast(this, box.getText() + (box.isSelected() ? " is on" : " is off"), false, null, null);
    }

    private void updateProjectWidget() {
        String name = project == null ? "No project" : project.name;
        String chevron = ICONS != null ? "&nbsp;<font face='" + ICONS + "' color='" + Fx.hex(Fx.DIM) + "'>&#xE70D;</font>" : " v";
        projectWidget.setText("<html><b>" + escape(name) + "</b>" + chevron + "</html>");
        projectWidget.setIcon(project == null ? null : projectIcon(project, 28));
        projectWidget.setIconTextGap(10);
        projectWidget.setFont(projectWidget.getFont().deriveFont(15f));
        projectWidget.setPreferredSize(new Dimension(Math.min(280, projectWidget.getUI().getPreferredSize(projectWidget).width + 20), 46));
    }

    private void changeIcon() {
        if (project == null) return;
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Pick a picture for " + project.name);
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Pictures", "png", "jpg", "jpeg", "gif", "bmp"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            Project.setIcon(project.dir, fc.getSelectedFile());
            updateProjectWidget();
            presence();
            home.refresh();
            Fx.toast(this, "Changed the icon, it's the pack icon too", false, null, null);
        } catch (IOException ex) {
            error("Couldn't use that picture", ex);
        }
    }

    // the project's own picture, or a coloured square with its initials like IntelliJ draws for projects without one
    private static javax.swing.Icon projectIcon(Project p, int size) {
        java.awt.Image img = p == null ? null : Project.icon(p.dir, size);
        String name = p == null ? "?" : p.name;
        return new javax.swing.Icon() {
            public void paintIcon(Component c, java.awt.Graphics g, int x, int y) {
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2.clip(new java.awt.geom.RoundRectangle2D.Float(x, y, size, size, 5, 5));
                if (img != null) {
                    g2.drawImage(img, x, y, size, size, null);
                } else {
                    g2.setColor(Color.getHSBColor(Math.floorMod(name.hashCode(), 360) / 360f, 0.45f, 0.62f));
                    g2.fillRect(x, y, size, size);
                    g2.setColor(Color.WHITE);
                    g2.setFont(c.getFont().deriveFont(Font.BOLD, size * 0.5f));
                    String s = name.substring(0, Math.min(2, name.length())).toUpperCase();
                    java.awt.FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(s, x + (size - fm.stringWidth(s)) / 2f, y + (size + fm.getAscent() - fm.getDescent()) / 2f);
                }
                g2.dispose();
            }

            public int getIconWidth() {
                return size;
            }

            public int getIconHeight() {
                return size;
            }
        };
    }

    // everything about the project in one card under its name, so nothing lives up in the title bar
    private void projectMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.setBorder(BorderFactory.createLineBorder(Fx.t().border()));
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Fx.t().island());
        card.setBorder(BorderFactory.createEmptyBorder(18, 18, 14, 18));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        Runnable close = () -> menu.setVisible(false);

        if (project != null) {
            JLabel title = new JLabel("<html><b style='font-size:15px'>" + escape(project.name) + "</b><br><font color='" + Fx.hex(Fx.t().dim())
                    + "'>" + project.effects.size() + (project.effects.size() == 1 ? " effect" : " effects") + "</font></html>", projectIcon(project, 64), JLabel.LEFT);
            title.setIconTextGap(16);
            title.setForeground(Fx.WHITE);
            title.setFont(title.getFont().deriveFont(16f));
            card.add(title, c);
            JPanel buttons = new JPanel(new GridLayout(1, 2, 10, 0));
            buttons.setOpaque(false);
            buttons.add(cardButton("\uE70F", "Change icon", () -> { close.run(); changeIcon(); }));
            buttons.add(cardButton("\uE838", "Show in folder", () -> { close.run(); reveal(project.dir); }));
            c.insets = new java.awt.Insets(16, 0, 6, 0);
            card.add(buttons, c);
            c.insets = new java.awt.Insets(10, 0, 0, 0);
            card.add(cardField("Pack name", project.name, ".*\\S.*", v -> project.name = v), c);
            card.add(cardField("Description", project.description, ".*", v -> project.description = v), c);
            JPanel more = new JPanel(new GridBagLayout());
            more.setOpaque(false);
            GridBagConstraints mc = (GridBagConstraints) c.clone();
            mc.insets = new java.awt.Insets(10, 0, 0, 0);
            more.add(cardField("Author", project.author, ".*", v -> project.author = v), mc);
            more.add(cardField("Namespace", project.namespace, "[a-z0-9_.-]+", v -> project.namespace = v), mc);
            more.add(cardField("Discord image link", project.discordImage, "(https://\\S+)?", v -> {
                project.discordImage = v;
                // a link typed in by hand is kept, clearing it hands the job back to the upload
                project.discordImageFor = "";
            }), mc);
            Fx.Glide open = new Fx.Glide(card, 0);
            JComponent drawer = new JComponent() {
                {
                    setLayout(new BorderLayout());
                    add(more);
                }

                @Override
                public Dimension getPreferredSize() {
                    return new Dimension(0, (int) Math.round(more.getPreferredSize().height * open.get()));
                }

                @Override
                public void doLayout() {
                    more.setBounds(0, 0, getWidth(), more.getPreferredSize().height);
                }
            };
            Chevron chevron = new Chevron(open);
            JButton toggle = new JButton("More settings");
            toggle.putClientProperty("JButton.buttonType", "toolBarButton");
            toggle.setHorizontalAlignment(JButton.LEFT);
            toggle.setFocusable(false);
            toggle.setFont(toggle.getFont().deriveFont(14f));
            toggle.setLayout(new BorderLayout());
            toggle.add(chevron, BorderLayout.EAST);
            toggle.setPreferredSize(new Dimension(0, 38));
            // the card grows with the drawer every frame, so it slides open instead of jumping
            Timer[] grow = {null};
            toggle.addActionListener(e -> {
                double target = open.get() > 0.5 ? 0 : 1;
                open.to(target);
                if (grow[0] != null) grow[0].stop();
                grow[0] = new Timer(1000 / 60, t -> {
                    drawer.revalidate();
                    fitPopup(menu);
                    if (Math.abs(open.get() - target) < 0.001) ((Timer) t.getSource()).stop();
                });
                grow[0].start();
            });
            c.insets = new java.awt.Insets(8, -8, 0, -8);
            card.add(toggle, c);
            c.insets = new java.awt.Insets(0, 0, 0, 0);
            card.add(drawer, c);
        }

        List<JComponent> others = new ArrayList<>();
        for (String dir : recent()) {
            if (project != null && project.dir.toString().equals(dir)) continue;
            Path p = Path.of(dir);
            Project other = null;
            try {
                other = Project.load(p);
            } catch (Exception ignored) {
            }
            JComponent row = cardRow(projectIcon(other, 28), other != null ? other.name : p.getFileName().toString(), null, () -> { close.run(); openProject(p); });
            row.setToolTipText(dir);
            others.add(row);
            if (others.size() == 5) break;
        }
        if (!others.isEmpty()) {
            JLabel h = new JLabel("Switch project");
            h.setForeground(Fx.t().dim());
            c.insets = new java.awt.Insets(project != null ? 14 : 0, 0, 4, 0);
            card.add(h, c);
            c.insets = new java.awt.Insets(0, -8, 0, -8);
            for (JComponent row : others) card.add(row, c);
        }

        JPanel ends = new JPanel(new GridLayout(1, 2, 8, 0));
        ends.setOpaque(false);
        ends.add(cardRow(null, "New project", "\uE710", () -> { close.run(); newProject(); }));
        ends.add(cardRow(null, "Open folder", "\uE8B7", () -> { close.run(); openProject(); }));
        c.insets = new java.awt.Insets(10, -8, 0, -8);
        card.add(ends, c);

        JPanel sized = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(420, super.getPreferredSize().height);
            }
        };
        sized.add(card);
        menu.add(sized);
        menu.show(projectWidget, 0, projectWidget.getHeight() + 6);
        Window w = SwingUtilities.getWindowAncestor(menu);
        if (w != null && w != this) {
            Point end = w.getLocation();
            Fx.opacity(w, 0f);
            Fx.animate(170, p -> {
                Fx.opacity(w, (float) p);
                w.setLocation(end.x, (int) Math.round(end.y - 8 * (1 - p)));
            }, null);
        }
    }

    private void fitPopup(JPopupMenu menu) {
        Dimension d = menu.getPreferredSize();
        Window w = SwingUtilities.getWindowAncestor(menu);
        if (w != null && w != this) {
            w.setSize(d);
            w.validate();
        } else {
            menu.setSize(d);
            menu.revalidate();
        }
        menu.repaint();
    }

    // a little arrow that turns from pointing right to pointing down as its glide goes from 0 to 1
    private static final class Chevron extends JComponent {
        private final Fx.Glide turn;

        Chevron(Fx.Glide turn) {
            this.turn = turn;
            setPreferredSize(new Dimension(34, 0));
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(getWidth() / 2.0 - 4, getHeight() / 2.0);
            g2.rotate(turn.get() * Math.PI / 2);
            g2.setColor(Fx.t().dim());
            g2.setStroke(new java.awt.BasicStroke(1.4f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            g2.draw(new java.awt.geom.Path2D.Float() {
                {
                    moveTo(-2.5f, -5f);
                    lineTo(2.5f, 0f);
                    lineTo(-2.5f, 5f);
                }
            });
            g2.dispose();
        }
    }

    private static JButton cardButton(String glyph, String text, Runnable action) {
        String label = ICONS != null ? "<html><font face='" + ICONS + "'>" + glyph + "</font>&nbsp;&nbsp;" + text + "</html>" : text;
        JButton b = new JButton(label);
        b.setFocusable(false);
        b.setPreferredSize(new Dimension(0, 38));
        b.addActionListener(e -> action.run());
        return b;
    }

    // a flat clickable line: an optional picture or glyph, then the words
    private static JComponent cardRow(javax.swing.Icon icon, String text, String glyph, Runnable action) {
        JButton b = new JButton(text, icon);
        b.putClientProperty("JButton.buttonType", "toolBarButton");
        b.setHorizontalAlignment(JButton.LEFT);
        b.setIconTextGap(12);
        b.setFocusable(false);
        b.setFont(b.getFont().deriveFont(14f));
        if (icon == null && glyph != null && ICONS != null)
            b.setText("<html><font face='" + ICONS + "' color='" + Fx.hex(Fx.t().dim()) + "'>" + glyph + "</font>&nbsp;&nbsp;" + escape(text) + "</html>");
        b.setPreferredSize(new Dimension(0, icon != null ? 44 : 38));
        b.addActionListener(e -> action.run());
        return b;
    }

    // saves when you press enter or click away, nothing to confirm. a value that breaks the rule gets a red outline instead
    private JComponent cardField(String label, String value, String rule, java.util.function.Consumer<String> set) {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setForeground(Fx.t().dim());
        JTextField f = new JTextField(value);
        f.setFont(f.getFont().deriveFont(14f));
        f.setPreferredSize(new Dimension(0, 38));
        String[] saved = {value == null ? "" : value};
        Runnable commit = () -> {
            String v = f.getText().strip();
            boolean ok = v.matches(rule);
            f.putClientProperty("JComponent.outline", ok ? null : "error");
            if (project == null || !ok || v.equals(saved[0])) return;
            saved[0] = v;
            set.accept(v);
            projectChanged();
        };
        f.addActionListener(e -> commit.run());
        f.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                commit.run();
            }
        });
        p.add(l, BorderLayout.NORTH);
        p.add(f, BorderLayout.CENTER);
        return p;
    }

    private void consoleTab(int i) {
        if (consoleCard.isVisible() && panel.getSelectedIndex() == i) {
            toggle(consoleCard, middle);
            return;
        }
        panel.setSelectedIndex(i);
        if (!consoleCard.isVisible()) toggle(consoleCard, middle);
    }

    // the title row on top of a tool window, with its name and a button that hides it
    private JComponent toolHeader(String title, JLabel extra, Runnable hide) {
        JPanel h = new JPanel(new BorderLayout(8, 0));
        h.setOpaque(false);
        h.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 4));
        JLabel t = new JLabel(title);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 13f));
        t.setForeground(Fx.WHITE);
        JPanel words = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        words.setOpaque(false);
        words.add(t);
        if (extra != null) {
            extra.setFont(extra.getFont().deriveFont(12f));
            extra.setForeground(Fx.DIM);
            words.add(Box.createHorizontalStrut(10));
            words.add(extra);
        }
        JPanel mid = new JPanel(new GridBagLayout());
        mid.setOpaque(false);
        h.add(words, BorderLayout.CENTER);
        h.add(toolActions(hide), BorderLayout.EAST);
        return h;
    }

    private static JComponent toolActions(Runnable hide) {
        JButton b = new JButton(Chrome.ICONS != null ? "" : "-");
        b.putClientProperty("JButton.buttonType", "toolBarButton");
        b.setFocusable(false);
        b.setToolTipText("Hide");
        b.addActionListener(e -> hide.run());
        b.setFont(Chrome.ICONS != null ? new Font(Chrome.ICONS, Font.PLAIN, 12) : b.getFont());
        b.setForeground(Fx.DIM);
        b.setPreferredSize(new Dimension(26, 24));
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 3));
        p.setOpaque(false);
        p.add(b);
        return p;
    }

    // opens or closes a tool window and remembers where its divider was
    private void toggle(Component target, JSplitPane split) {
        boolean first = split.getLeftComponent() == target;
        int full = (split.getOrientation() == JSplitPane.HORIZONTAL_SPLIT ? split.getWidth() : split.getHeight()) - split.getDividerSize();
        if (target.isVisible()) {
            split.putClientProperty("lastDivider", split.getDividerLocation());
            target.setVisible(false);
            split.revalidate();
        } else {
            Object last = split.getClientProperty("lastDivider");
            target.setVisible(true);
            split.setDividerLocation(last instanceof Integer at ? at : (int) (full * (first ? 0.18 : 0.7)));
        }
    }

    // under the preview: what's wrong, then the build button
    private JComponent footer() {
        JPanel line = new JPanel(new BorderLayout(8, 0));
        line.setOpaque(false);
        line.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 4));
        if (Chrome.ICONS != null) problemMark.setFont(new Font(Chrome.ICONS, Font.PLAIN, 14));
        problemMark.setForeground(Fx.WHITE);
        statusProblems.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
        statusProblems.setFont(statusProblems.getFont().deriveFont(13f));
        statusProblems.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        statusProblems.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                consoleTab(0);
            }
        });
        JLabel logLink = statusCell("Log");
        logLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        logLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                consoleTab(1);
            }
        });
        JPanel extras = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        extras.setOpaque(false);
        extras.add(statusBusy);
        extras.add(statusFps);
        extras.add(statusCaret);
        extras.add(logLink);
        line.add(problemMark, BorderLayout.WEST);
        line.add(statusProblems, BorderLayout.CENTER);
        line.add(extras, BorderLayout.EAST);

        JPanel f = new JPanel(new BorderLayout(0, 8));
        f.setOpaque(false);
        f.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        f.add(new Fx.Island(line, 0, 10), BorderLayout.NORTH);
        f.add(Chrome.action("Build pack", 58, this::build), BorderLayout.SOUTH);
        return f;
    }

    private static JLabel statusCell(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Fx.DIM);
        l.setFont(l.getFont().deriveFont(12f));
        l.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
        return l;
    }

    // colours look the theme up when they paint, so a switch only needs FlatLaf redone and a repaint.
    // the few things that bake a colour into html get rebuilt
    private void changeTheme(Theme t) {
        if (t == Theme.current) return;
        Theme.save(t);
        Theme.apply();
        FlatLaf.updateUI();
        updateProjectWidget();
        showProblems(new ArrayList<>(java.util.Collections.list(problems.elements())));
        for (int i = 0; i < tabs.getTabCount(); i++) {
            CodeEditor ed = (CodeEditor) tabs.getComponentAt(i);
            ed.retheme();
        }
        home.refresh();
        repaint();
    }

    private static void launch(Path dir, String section) {
        Theme.apply();
        // the preview is a native GL canvas, and menus drawn in Swing would end up hidden behind it
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);
        Main m = new Main();
        if (dir != null && Files.exists(dir.resolve("project.json"))) m.openProject(dir);
        // the last project opens quietly behind home, Esc goes straight to it
        m.showHome(section);
        m.setVisible(true);
    }

    private static <T extends JComponent> T borderless(T c) {
        c.setBorder(null);
        return c;
    }

    private static JSplitPane split(int orientation, Component a, Component b, double weight) {
        JSplitPane s = new JSplitPane(orientation, a, b);
        s.setResizeWeight(weight);
        s.setBorder(null);
        s.setContinuousLayout(true);
        s.setBackground(Fx.BACKDROP);
        s.setOpaque(false);
        return s;
    }

    private static String glyphHtml(String glyph, String color) {
        return ICONS != null ? "<font face='" + ICONS + "' color='" + color + "'>" + glyph + "</font>" : "<font color='" + color + "'>x</font>";
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String shorten(String path) {
        return path.length() > 52 ? "..." + path.substring(path.length() - 49) : path;
    }

    private void showWelcome() {
        if (home != null && homeShowing) home.refresh();
    }

    private void showHome(String section) {
        homeShowing = true;
        home.refresh();
        home.select(section);
        rail.select(section);
        screenCards.show(screens, "home");
        home.play(420, 14);
        presence();
    }

    // on home only the app logo shows. in a project its own picture is the big one and the app logo sits in the corner,
    // which needs a link since Discord can't read pictures off this computer
    private final java.util.Set<Path> uploading = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> checkedLinks = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // uploads the project's pack.png once, and again whenever the picture itself changes
    private void syncDiscordIcon() {
        Project p = project;
        if (p == null || !Discord.enabled() || !uploading.add(p.dir)) return;
        Thread t = new Thread(() -> {
            try {
                Path png = Project.icon(p.dir);
                if (!p.discordImage.isBlank() && p.discordImageFor.isEmpty()) {
                    if (!checkedLinks.contains(p.discordImage) && Discord.works(p.discordImage)) {
                        checkedLinks.add(p.discordImage);
                        SwingUtilities.invokeLater(this::presence);
                    }
                    return;
                }
                if (!Files.exists(png)) return;
                String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(png)));
                if (hash.equals(p.discordImageFor) && !p.discordImage.isBlank()) {
                    if (checkedLinks.contains(p.discordImage)) return;
                    if (Discord.works(p.discordImage)) {
                        checkedLinks.add(p.discordImage);
                        SwingUtilities.invokeLater(this::presence);
                        return;
                    }
                }
                String link = Discord.upload(png);
                checkedLinks.add(link);
                SwingUtilities.invokeLater(() -> {
                    p.discordImage = link;
                    p.discordImageFor = hash;
                    try {
                        p.save();
                    } catch (IOException ignored) {
                    }
                    presence();
                });
            } catch (Exception ignored) {
                // offline or catbox is down, the app logo stays until the next try
            } finally {
                uploading.remove(p.dir);
            }
        }, "discord icon");
        t.setDaemon(true);
        t.start();
    }

    private void presence() {
        syncDiscordIcon();
        if (homeShowing) {
            Discord.show("On the home screen", null, "logo", Forge.TOOL, null);
            return;
        }
        int tab = tabs.getSelectedIndex();
        String details = project == null ? "Writing shaders" : "Working on " + project.name;
        String state = tab < 0 ? null : "Editing " + tabs.getTitleAt(tab);
        boolean own = project != null && checkedLinks.contains(project.discordImage);
        Discord.show(details, state, own ? project.discordImage : "logo", own ? project.name : Forge.TOOL, own ? "logo" : null);
    }

    private void showWork() {
        if (project == null && tabs.getTabCount() == 0) showHome("home");
        else backToEditor();
    }

    private void openEffect(Path dir, Path file) {
        if (project == null || !project.dir.equals(dir)) openProject(dir);
        if (project == null || !project.dir.equals(dir)) return;
        if (file != null) open(file);
        backToEditor();
    }

    private void backToEditor() {
        if (project == null && tabs.getTabCount() == 0) return;
        homeShowing = false;
        rail.select("work");
        presence();
        screenCards.show(screens, "work");
    }

    // the shortcuts worth knowing, for when nothing is open
    private JComponent emptyEditor() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Fx.t().editor());
        JPanel rows = new JPanel(new GridLayout(0, 2, 18, 12));
        rows.setOpaque(false);
        String[][] hints = {{"Go to File", "Ctrl+P"}, {"Search Commands", "Ctrl+Shift+P"}, {"New Effect", "Ctrl+N"},
                {"Build Pack", "Ctrl+B"}, {"Home", "Ctrl+H"}, {"Learn Shaders", "F1"}};
        for (String[] h : hints) {
            JLabel name = new JLabel(h[0], JLabel.RIGHT);
            name.setForeground(Fx.DIM);
            JLabel keys = new JLabel(h[1]);
            keys.setForeground(Fx.t().accent());
            rows.add(name);
            rows.add(keys);
        }
        p.add(rows);
        return p;
    }

    private static List<String> recent() {
        List<String> list = new ArrayList<>();
        for (String dir : PREFS.get("recent", "").split("\n")) {
            if (!dir.isBlank() && Files.exists(Path.of(dir, "project.json"))) list.add(dir);
        }
        return list;
    }

    private static void remember(Path dir) {
        List<String> list = recent();
        list.remove(dir.toString());
        list.add(0, dir.toString());
        PREFS.put("recent", String.join("\n", list.subList(0, Math.min(list.size(), 6))));
        PREFS.put("last", dir.toString());
    }

    private void newProject() {
        ProjectForm.Answers a = ProjectForm.ask(this, null);
        if (a == null || !confirmUnsaved()) return;
        try {
            Project p = Project.create(a.dir(), a.name());
            p.author = a.author();
            p.description = a.description();
            p.namespace = a.namespace();
            p.save();
            load(p);
            Fx.toast(this, "Made " + p.name + ". Ctrl+N adds an effect", false, null, null);
        } catch (IOException ex) {
            error("Couldn't create the project", ex);
        }
    }

    private void projectSettings() {
        if (project == null) {
            JOptionPane.showMessageDialog(this, "Open or create a project first.");
            return;
        }
        ProjectForm.Answers a = ProjectForm.ask(this, project);
        if (a == null) return;
        project.name = a.name();
        project.author = a.author();
        project.description = a.description();
        project.namespace = a.namespace();
        if (!a.discordImage().equals(project.discordImage)) {
            project.discordImage = a.discordImage();
            // a link typed in by hand is kept, clearing it hands the job back to the upload
            project.discordImageFor = "";
        }
        projectChanged();
    }

    private void projectChanged() {
        try {
            project.save();
            presence();
            reloadOpen(project.file());
            setTitle(project.name + " - " + Forge.TOOL);
            explorerTitle.setText(project.name);
            updateProjectWidget();
            remember(project.dir);
            home.refresh();
            Fx.toast(this, "Saved the project settings", false, null, null);
        } catch (IOException ex) {
            error("Couldn't save the project settings", ex);
        }
    }

    private void openProject() {
        JFileChooser fc = new JFileChooser(PREFS.get("last", null));
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Open a project folder");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        openProject(fc.getSelectedFile().toPath());
    }

    private void openProject(Path dir) {
        if (!Files.exists(dir.resolve("project.json"))) {
            notAProject(dir);
            return;
        }
        if (!confirmUnsaved()) return;
        try {
            load(Project.load(dir));
        } catch (IOException | RuntimeException ex) {
            error("Couldn't open " + dir.getFileName(), ex);
        }
    }

    private void notAProject(Path dir) {
        if (Files.exists(dir.resolve("pack.mcmeta"))) {
            JOptionPane.showMessageDialog(this, dir.getFileName() + " is a finished resource pack, so there's nothing to edit in it.\n"
                    + "Open a project folder instead (one with a project.json in it), or start a new one.", Forge.TOOL, JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int r = JOptionPane.showConfirmDialog(this, dir.getFileName() + " isn't a Hogyoku Vessel project yet. Turn it into one?",
                Forge.TOOL, JOptionPane.YES_NO_OPTION);
        if (r != JOptionPane.YES_OPTION || !confirmUnsaved()) return;
        try {
            load(Project.create(dir, dir.getFileName().toString()));
        } catch (IOException ex) {
            error("Couldn't create the project", ex);
        }
    }

    private void load(Project p) {
        if (lessonPanel != null && (lessonFile == null || !lessonFile.startsWith(p.dir))) exitLesson();
        project = p;
        remember(p.dir);
        tabs.removeAll();
        setTitle(p.name + " - " + Forge.TOOL);
        explorerTitle.setText(p.name);
        updateProjectWidget();
        refreshTree();
        showWelcome();
        log("opened " + p.dir);
        if (!p.effects.isEmpty()) open(p.dir.resolve(p.effects.get(0).file()));
    }

    private void refreshTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(project.dir);
        add(root, project.dir);
        tree.setModel(new DefaultTreeModel(root));
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
        refreshBar();
    }

    private void refreshBar() {
        List<Chrome.EffectBar.Entry> entries = new ArrayList<>();
        java.util.Set<Path> shown = new java.util.HashSet<>();
        if (project != null) {
            for (Project.Effect e : project.effects) {
                Path file = project.dir.resolve(e.file()).normalize();
                shown.add(file);
                entries.add(new Chrome.EffectBar.Entry(file, Chrome.pretty(e.id()), Project.picture(Project.shot(project.dir, e.id())), dirty(file), false));
            }
        }
        for (int i = 0; i < tabs.getTabCount(); i++) {
            Path file = ((CodeEditor) tabs.getComponentAt(i)).file().normalize();
            if (shown.add(file)) entries.add(new Chrome.EffectBar.Entry(file, file.getFileName().toString(), null, dirty(file), true));
        }
        CodeEditor ed = current();
        effectBar.set(entries, ed == null ? null : ed.file().normalize());
        int unsaved = 0;
        for (int i = 0; i < tabs.getTabCount(); i++) if (((CodeEditor) tabs.getComponentAt(i)).dirty()) unsaved++;
        saveButton.unsaved(unsaved);
        // the undo history hears about an edit after this runs, so look a moment later.
        // flatlaf barely dims a disabled toolbar button on black, so the colour says it instead
        SwingUtilities.invokeLater(() -> {
            undoButton.setForeground(ed != null && ed.canUndo() ? Fx.WHITE : Fx.t().faint());
            redoButton.setForeground(ed != null && ed.canRedo() ? Fx.WHITE : Fx.t().faint());
        });
    }

    private static JButton editIcon(String glyph, String tip, Runnable action) {
        JButton b = new JButton(Chrome.ICONS != null ? glyph : tip.substring(0, 1));
        if (Chrome.ICONS != null) b.setFont(new Font(Chrome.ICONS, Font.PLAIN, 15));
        b.putClientProperty("JButton.buttonType", "toolBarButton");
        b.setFocusable(false);
        b.setToolTipText(tip);
        b.setPreferredSize(new Dimension(40, 40));
        b.addActionListener(e -> action.run());
        return b;
    }

    private boolean dirty(Path file) {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            CodeEditor ed = (CodeEditor) tabs.getComponentAt(i);
            if (ed.file().normalize().equals(file)) return ed.dirty();
        }
        return false;
    }

    private void add(DefaultMutableTreeNode node, Path dir) {
        try (Stream<Path> list = Files.list(dir)) {
            List<Path> sorted = list.sorted(java.util.Comparator.comparing((Path q) -> Files.isRegularFile(q))
                    .thenComparing(q -> q.getFileName().toString())).toList();
            for (Path p : sorted) {
                String name = p.getFileName().toString();
                if (name.equals("build") || name.startsWith(".")) continue;
                DefaultMutableTreeNode child = new DefaultMutableTreeNode(new Entry(p));
                node.add(child);
                if (Files.isDirectory(p)) add(child, p);
            }
        } catch (IOException ignored) {
        }
    }

    private record Entry(Path path) {
        @Override
        public String toString() {
            return path.getFileName().toString();
        }
    }

    private Path pathAt(MouseEvent e) {
        TreePath tp = tree.getPathForLocation(e.getX(), e.getY());
        if (tp == null) return null;
        tree.setSelectionPath(tp);
        Object o = ((DefaultMutableTreeNode) tp.getLastPathComponent()).getUserObject();
        return o instanceof Entry entry ? entry.path : null;
    }

    private void popup(MouseEvent e) {
        if (!e.isPopupTrigger() || project == null) return;
        fileMenu(pathAt(e)).show(tree, e.getX(), e.getY());
    }

    private void chipMenu(Path p, MouseEvent e) {
        if (project != null) fileMenu(p).show(e.getComponent(), e.getX(), e.getY());
    }

    private JPopupMenu fileMenu(Path p) {
        JPopupMenu menu = new JPopupMenu();
        boolean file = p != null && Files.isRegularFile(p);
        Project.Effect effect = file ? project.effectFor(p) : null;
        if (file) menu.add(item("Open", null, () -> open(p)));
        menu.add(item("New Effect...", null, this::newEffect));
        if (effect != null) {
            menu.addSeparator();
            menu.add(item("Effect Settings...", null, () -> effectSettings(effect)));
            menu.add(item("Duplicate", null, () -> duplicate(effect)));
            menu.add(item("Give Command", null, () -> copy("/give @s minecraft:paper[minecraft:item_model=\"" + project.namespace + ":" + effect.item() + "\"]",
                    "Copied the /give command for " + effect.id())));
        }
        if (file && p.toString().endsWith(".glsl")) {
            menu.add(item("Rename...", null, () -> rename(p)));
            menu.add(item("Delete", null, () -> delete(p)));
        }
        if (p != null) {
            menu.addSeparator();
            menu.add(item("Copy Path", null, () -> copy(p.toString(), "Copied the path")));
            menu.add(item("Copy Relative Path", null, () -> copy(project.dir.relativize(p).toString().replace('\\', '/'), "Copied the relative path")));
            menu.add(item("Reveal in File Explorer", null, () -> reveal(Files.isDirectory(p) ? p : p.getParent())));
        }
        menu.addSeparator();
        menu.add(item("Build Pack", null, this::build));
        menu.add(item("Refresh", null, this::refreshTree));
        for (int i = 0; i < tabs.getTabCount(); i++) {
            CodeEditor ed = (CodeEditor) tabs.getComponentAt(i);
            if (!ed.file().normalize().equals(p == null ? null : p.normalize())) continue;
            menu.addSeparator();
            menu.add(item("Close", null, () -> close(tabs.indexOfComponent(ed))));
            menu.add(item("Close Others", null, () -> {
                for (int k = tabs.getTabCount() - 1; k >= 0; k--) if (tabs.getComponentAt(k) != ed) close(k);
            }));
            menu.add(item("Close All", null, () -> {
                for (int k = tabs.getTabCount() - 1; k >= 0; k--) close(k);
            }));
            break;
        }
        return menu;
    }

    private void copy(String text, String done) {
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(text), null);
        Fx.toast(this, done, false, null, null);
    }

    private void effectSettings(Project.Effect effect) {
        JTextField alpha = new JTextField(String.valueOf(effect.alpha()));
        JTextField itemId = new JTextField(effect.item());
        JPanel form = new JPanel(new GridLayout(0, 1, 0, 4));
        form.add(new JLabel("Alpha code (the texture alpha that switches this effect on, 1 to 254)"));
        form.add(alpha);
        form.add(new JLabel("Item id (the item model is " + project.namespace + ":<item id>)"));
        form.add(itemId);
        if (JOptionPane.showConfirmDialog(this, form, effect.id() + " settings", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        int a;
        try {
            a = Integer.parseInt(alpha.getText().strip());
        } catch (NumberFormatException ex) {
            a = -1;
        }
        String id = itemId.getText().strip().toLowerCase().replaceAll("[^a-z0-9_./-]", "_");
        final int code = a;
        if (code < 1 || code > 254) {
            JOptionPane.showMessageDialog(this, "The alpha code has to be a number from 1 to 254.");
            return;
        }
        if (project.effects.stream().anyMatch(o -> o != effect && o.alpha() == code)) {
            JOptionPane.showMessageDialog(this, "Another effect already uses alpha " + code + ".");
            return;
        }
        if (id.isEmpty()) return;
        try {
            project.effects.set(project.effects.indexOf(effect), new Project.Effect(effect.id(), effect.file(), code, id, effect.defines()));
            project.save();
            reloadOpen(project.file());
            refreshPreview();
            Fx.toast(this, "Saved " + effect.id() + " (alpha " + code + ", item " + id + ")", false, null, null);
        } catch (IOException ex) {
            error("Couldn't save the effect", ex);
        }
    }

    private void duplicate(Project.Effect effect) {
        Path from = project.dir.resolve(effect.file());
        String id = effect.id() + "_copy";
        for (int n = 2; Files.exists(from.resolveSibling(id + ".glsl")); n++) id = effect.id() + "_copy" + n;
        Path to = from.resolveSibling(id + ".glsl");
        try {
            Files.copy(from, to);
            String rel = project.dir.relativize(to).toString().replace('\\', '/');
            project.effects.add(new Project.Effect(id, rel, project.freeAlpha(), id, new java.util.LinkedHashMap<>(effect.defines())));
            project.save();
            reloadOpen(project.file());
            refreshTree();
            open(to);
        } catch (IOException ex) {
            error("Couldn't duplicate " + effect.id(), ex);
        }
    }

    private void reveal(Path dir) {
        try {
            Desktop.getDesktop().open(dir.toFile());
        } catch (IOException ex) {
            error("Couldn't open the folder", ex);
        }
    }

    private void browse(String url) {
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (IOException ex) {
            error("Couldn't open the browser", ex);
        }
    }

    private void newEffect() {
        if (project == null) {
            JOptionPane.showMessageDialog(this, "Open or create a project first.");
            return;
        }
        List<Gallery.Choice> choices = new ArrayList<>();
        choices.add(new Gallery.Choice("my_sky", "Blank sky", "Sky", "an empty sky to start from", Templates.NEW_EFFECT, null));
        choices.add(new Gallery.Choice("my_object", "Blank object", "Object", "an empty object to start from", Templates.NEW_OBJECT, null));
        choices.add(new Gallery.Choice("my_surface", "Blank surface", "Surface", "an empty surface to start from", Templates.NEW_SURFACE, null));
        choices.add(new Gallery.Choice("my_material", "Blank material", "Material", "an empty material to start from", Templates.NEW_MATERIAL, null));
        choices.add(new Gallery.Choice("my_overlay", "Blank overlay", "Overlay", "an empty overlay to start from", Templates.NEW_OVERLAY, null));
        for (Templates.Example ex : Templates.EXAMPLES) choices.add(ex.choice());
        Gallery.show(this, choices, c -> createEffect(c.id(), c.source()));
    }

    private void showLearn() {
        showHome("learn");
    }

    // lessons live in their own project in Documents so they never clutter a real pack
    private void startLesson(Learn.Lesson lesson) {
        Path dir = Path.of(System.getProperty("user.home"), "Documents", "Hogyoku Vessel", "Lessons");
        try {
            if (project == null || !project.dir.equals(dir)) {
                if (!confirmUnsaved()) return;
                if (Files.exists(dir.resolve("project.json"))) {
                    load(Project.load(dir));
                } else {
                    Project p = Project.create(dir, "Lessons");
                    p.namespace = "lessons";
                    p.description = "Shaders made while learning";
                    p.save();
                    load(p);
                }
            }
            Path file = dir.resolve("effects").resolve(lesson.id() + ".glsl");
            if (!Files.exists(file)) {
                Files.writeString(file, lesson.starter(), StandardCharsets.UTF_8);
                project.effects.add(new Project.Effect(lesson.id(), "effects/" + lesson.id() + ".glsl", project.freeAlpha(), lesson.id(), new java.util.LinkedHashMap<>()));
                project.save();
                reloadOpen(project.file());
                refreshTree();
            }
            int at = Learn.LESSONS.indexOf(lesson);
            boolean wasOpen = lessonPanel != null;
            if (wasOpen) sideStack.remove(lessonPanel);
            lessonFile = file;
            lessonPanel = new Learn.Panel(lesson, new Learn.Panel.Actions(
                    () -> resetLesson(lesson),
                    this::exitLesson,
                    () -> {
                        if (at + 1 < Learn.LESSONS.size()) startLesson(Learn.LESSONS.get(at + 1));
                    }));
            sideStack.add(lessonPanel, "lesson");
            sideCards.show(sideStack, "lesson");
            if (!sideCard.isVisible()) toggle(sideCard, all);
            if (!wasOpen) filesWidth = all.getDividerLocation();
            slideSidebar(Math.max(filesWidth, 430));
            // one lesson file at a time, the previous lesson's tab makes room for this one
            for (int k = tabs.getTabCount() - 1; k >= 0; k--) {
                Path other = ((CodeEditor) tabs.getComponentAt(k)).file();
                if (other.startsWith(dir.resolve("effects")) && !other.equals(file) && !((CodeEditor) tabs.getComponentAt(k)).dirty()) tabs.removeTabAt(k);
            }
            open(file);
            backToEditor();
            CodeEditor ed = current();
            if (ed != null) lessonPanel.check(ed.source());
        } catch (IOException | RuntimeException ex) {
            error("Couldn't start the lesson", ex);
        }
    }

    private void exitLesson() {
        if (lessonPanel == null) return;
        sideCards.show(sideStack, "files");
        sideStack.remove(lessonPanel);
        lessonPanel = null;
        lessonFile = null;
        all.setDividerLocation(filesWidth);
        if (sideCard.isVisible()) toggle(sideCard, all);
    }

    private void resetLesson(Learn.Lesson lesson) {
        if (lessonFile == null) return;
        int r = JOptionPane.showConfirmDialog(this, "Put the starter code back? Your changes to this lesson are lost.", "Reset code", JOptionPane.OK_CANCEL_OPTION);
        if (r != JOptionPane.OK_OPTION) return;
        try {
            Files.writeString(lessonFile, lesson.starter(), StandardCharsets.UTF_8);
            reloadOpen(lessonFile);
            CodeEditor ed = current();
            if (ed != null && lessonPanel != null) lessonPanel.check(ed.source());
            refreshPreview();
        } catch (IOException ex) {
            error("Couldn't reset the lesson", ex);
        }
    }

    private void slideSidebar(int to) {
        int from = all.getDividerLocation();
        Fx.animate(320, p -> all.setDividerLocation((int) (from + (to - from) * p)), null);
    }

    // the welcome page cards work without a project too, they just make one first
    private void startFrom(Templates.Example ex) {
        if (project == null) newProject();
        if (project != null) createEffect(ex.id(), ex.source());
    }

    private void createEffect(String suggested, String source) {
        String id = JOptionPane.showInputDialog(this, "Effect name (letters, numbers and _)", suggested);
        if (id == null) return;
        id = id.strip().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        if (id.isEmpty()) return;
        Path file = project.dir.resolve("effects").resolve(id + ".glsl");
        if (Files.exists(file)) {
            JOptionPane.showMessageDialog(this, "There's already an effect called " + id + ".");
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, source, StandardCharsets.UTF_8);
            project.effects.add(new Project.Effect(id, "effects/" + id + ".glsl", project.freeAlpha(), id, new java.util.LinkedHashMap<>()));
            project.save();
            reloadOpen(project.file());
            refreshTree();
            open(file);
            log("added effect " + id);
        } catch (IOException ex) {
            error("Couldn't add the effect", ex);
        }
    }

    private void rename(Path file) {
        String old = file.getFileName().toString().replace(".glsl", "");
        String id = JOptionPane.showInputDialog(this, "New name", old);
        if (id == null || id.isBlank() || id.equals(old)) return;
        id = id.strip().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        Path target = file.resolveSibling(id + ".glsl");
        try {
            closeFile(file);
            Files.move(file, target);
            String rel = project.dir.relativize(target).toString().replace('\\', '/');
            for (int i = 0; i < project.effects.size(); i++) {
                Project.Effect e = project.effects.get(i);
                if (project.dir.resolve(e.file()).normalize().equals(file.normalize())) {
                    project.effects.set(i, new Project.Effect(e.id().equals(old) ? id : e.id(), rel, e.alpha(), e.item(), e.defines()));
                }
            }
            project.save();
            reloadOpen(project.file());
            refreshTree();
            open(target);
        } catch (IOException ex) {
            error("Couldn't rename " + old, ex);
        }
    }

    private void delete(Path file) {
        int ok = JOptionPane.showConfirmDialog(this, "Delete " + file.getFileName() + "? This can't be undone.", "Delete", JOptionPane.OK_CANCEL_OPTION);
        if (ok != JOptionPane.OK_OPTION) return;
        try {
            closeFile(file);
            Files.deleteIfExists(file);
            project.effects.removeIf(e -> project.dir.resolve(e.file()).normalize().equals(file.normalize()));
            project.save();
            reloadOpen(project.file());
            refreshTree();
        } catch (IOException ex) {
            error("Couldn't delete " + file.getFileName(), ex);
        }
    }

    private void open(Path file) {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (((CodeEditor) tabs.getComponentAt(i)).file().equals(file)) {
                tabs.setSelectedIndex(i);
                return;
            }
        }
        try {
            CodeEditor ed = new CodeEditor(file);
            ed.onCaret(this::updateCaret);
            ed.onChange(() -> {
                updateTitle(ed);
                if (ed == current()) previewDelay.restart();
                if (lessonPanel != null && ed.file().equals(lessonFile)) lessonPanel.check(ed.source());
            });
            JPanel corner = new JPanel();
            corner.setBackground(Fx.t().editor());
            ed.setCorner(ScrollPaneConstants.UPPER_LEFT_CORNER, corner);
            tabs.addTab(file.getFileName().toString(), ed);
            tabs.setToolTipTextAt(tabs.getTabCount() - 1, file.toString());
            tabs.setSelectedComponent(ed);
            if (homeShowing) backToEditor();
        } catch (IOException ex) {
            error("Couldn't open " + file.getFileName(), ex);
        }
    }

    private void updateTitle(CodeEditor ed) {
        int i = tabs.indexOfComponent(ed);
        if (i >= 0) tabs.setTitleAt(i, ed.file().getFileName() + (ed.dirty() ? "  ●" : ""));
        refreshBar();
    }

    private void updateCaret() {
        CodeEditor ed = current();
        statusCaret.setText(ed != null ? ed.position() : "");
    }

    private CodeEditor current() {
        return (CodeEditor) tabs.getSelectedComponent();
    }

    private void closeCurrent() {
        if (tabs.getSelectedIndex() >= 0) close(tabs.getSelectedIndex());
    }

    private void close(int i) {
        CodeEditor ed = (CodeEditor) tabs.getComponentAt(i);
        if (ed.dirty()) {
            int r = JOptionPane.showConfirmDialog(this, "Save " + ed.file().getFileName() + " before closing?");
            if (r == JOptionPane.CANCEL_OPTION || r == JOptionPane.CLOSED_OPTION) return;
            if (r == JOptionPane.YES_OPTION && !save(ed)) return;
        }
        tabs.removeTabAt(i);
    }

    private void closeFile(Path file) {
        for (int i = tabs.getTabCount() - 1; i >= 0; i--) {
            if (((CodeEditor) tabs.getComponentAt(i)).file().equals(file)) tabs.removeTabAt(i);
        }
    }

    private void reloadOpen(Path file) throws IOException {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            CodeEditor ed = (CodeEditor) tabs.getComponentAt(i);
            if (ed.file().equals(file)) {
                ed.reload();
                updateTitle(ed);
            }
        }
    }

    private void saveCurrent() {
        CodeEditor ed = current();
        if (ed != null) save(ed);
    }

    private boolean save(CodeEditor ed) {
        try {
            ed.save();
            updateTitle(ed);
            refreshBar();
            if (project != null && ed.file().equals(project.file())) {
                project = Project.load(project.dir);
                setTitle(project.name + " - " + Forge.TOOL);
                refreshPreview();
            }
            return true;
        } catch (IOException | RuntimeException ex) {
            error("Couldn't save " + ed.file().getFileName(), ex);
            return false;
        }
    }

    private boolean saveAll() {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            CodeEditor ed = (CodeEditor) tabs.getComponentAt(i);
            if (ed.dirty() && !save(ed)) return false;
        }
        return true;
    }

    private boolean confirmUnsaved() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < tabs.getTabCount(); i++) {
            CodeEditor ed = (CodeEditor) tabs.getComponentAt(i);
            if (ed.dirty()) names.add(ed.file().getFileName().toString());
        }
        if (names.isEmpty()) return true;
        int r = JOptionPane.showConfirmDialog(this, "Save changes to " + String.join(", ", names) + "?");
        if (r == JOptionPane.YES_OPTION) return saveAll();
        return r == JOptionPane.NO_OPTION;
    }

    private void refreshPreview() {
        CodeEditor ed = current();
        if (preview == null || ed == null || project == null) return;
        if (!ed.file().toString().endsWith(".glsl")) return;
        Project.Effect effect = project.effectFor(ed.file());
        Map<String, String> defines = effect != null ? effect.defines() : Map.of();
        preview.show(ed.source(), defines);
        if (effect != null) keepShot(Project.shot(project.dir, effect.id()));
    }

    // a broken shader keeps showing its last good frame, so nothing gets saved while there are problems
    private void keepShot(Path file) {
        preview.shoot(img -> {
            if (!problems.isEmpty()) return;
            Thread t = new Thread(() -> {
                try {
                    Files.createDirectories(file.getParent());
                    javax.imageio.ImageIO.write(img, "png", file.toFile());
                    SwingUtilities.invokeLater(this::refreshBar);
                } catch (IOException ex) {
                    SwingUtilities.invokeLater(() -> log("couldn't save a thumbnail: " + ex.getMessage()));
                }
            }, "sky shot");
            t.setDaemon(true);
            t.start();
        });
    }

    private void showProblems(List<Preview.Problem> list) {
        problems.clear();
        list.forEach(problems::addElement);
        CodeEditor ed = current();
        if (ed != null) ed.showProblems(list);
        panel.setTitleAt(0, list.isEmpty() ? "Problems" : "Problems  " + list.size());
        problemMark.setText(list.isEmpty() || Chrome.ICONS == null ? "" : "\uE7BA");
        statusProblems.setForeground(list.isEmpty() ? Fx.DIM : Fx.WHITE);
        if (list.isEmpty()) {
            statusProblems.setText("No problems");
        } else {
            Preview.Problem p = list.get(0);
            statusProblems.setText((p.line() > 0 ? "Line " + p.line() + ": " : "") + p.message() + (list.size() > 1 ? "  (" + (list.size() - 1) + " more)" : ""));
        }
    }

    private void build() {
        if (project == null) {
            JOptionPane.showMessageDialog(this, "Open or create a project first.");
            return;
        }
        if (building || !saveAll()) return;
        runBuild(project.dir, project.name);
    }

    // home's build tile asks which project, and builds it without opening it in the editor
    private void pickBuild() {
        List<String> dirs = new ArrayList<>(recent());
        if (project != null) {
            dirs.remove(project.dir.toString());
            dirs.add(0, project.dir.toString());
        }
        if (dirs.isEmpty()) {
            Fx.toast(this, "Make a project first, then you can build it", false, "New project", this::newProject);
            return;
        }
        List<Palette.Item> items = new ArrayList<>();
        for (String d : dirs) {
            Path dir = Path.of(d);
            boolean open = project != null && project.dir.equals(dir);
            items.add(new Palette.Item("Build " + dir.getFileName(), open ? "open now" : shorten(d), () -> {
                if (open) build();
                else if (!building) runBuild(dir, dir.getFileName().toString());
            }));
        }
        Palette.show(this, "Which project should be built?", items);
    }

    private void runBuild(Path dir, String name) {
        building = true;
        boolean ob = obfuscate.isSelected(), ck = check.isSelected(), sk = skript.isSelected();
        loader.busy(true);
        statusBusy.setText("Building " + name + "...");
        log("building " + name + "...");
        Fx.toast(this, "Building " + name + "...", false, null, null);
        new SwingWorker<Path, String>() {
            @Override
            protected Path doInBackground() throws Exception {
                Project p = Project.load(dir);
                Forge forge = new Forge(p, this::publish);
                Path zip = forge.build(ob, ck);
                if (sk) forge.writeSkript(zip.getParent());
                return zip;
            }

            @Override
            protected void process(List<String> lines) {
                lines.forEach(Main.this::log);
            }

            @Override
            protected void done() {
                building = false;
                loader.busy(false);
                statusBusy.setText("");
                try {
                    Path zip = get();
                    String what = sk ? "Built " + zip.getFileName() + " and its Skript" : "Built " + zip.getFileName();
                    Fx.toast(Main.this, what, false, "Show in folder", () -> reveal(zip.getParent()));
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    log("build failed: " + cause.getMessage());
                    Fx.toast(Main.this, "The build failed", true, "Show output", () -> {
                        if (!consoleCard.isVisible()) toggle(consoleCard, middle);
                        panel.setSelectedIndex(1);
                    });
                }
            }
        }.execute();
    }

    private void log(String line) {
        log.append(line + "\n");
        log.setCaretPosition(log.getDocument().getLength());
    }

    private void error(String what, Exception ex) {
        log(what + ": " + ex.getMessage());
        JOptionPane.showMessageDialog(this, what + ".\n\n" + ex.getMessage(), Forge.TOOL, JOptionPane.ERROR_MESSAGE);
    }

    private void checkForUpdate(boolean asked) {
        Thread t = new Thread(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + RELEASES + "/releases/latest"))
                        .header("User-Agent", "HogyokuVessel")
                        .header("Accept", "application/vnd.github+json")
                        .timeout(Duration.ofSeconds(10))
                        .build();
                HttpResponse<String> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) throw new IOException("GitHub answered " + res.statusCode());
                String latest = String.valueOf(((Map<?, ?>) Json.parse(res.body())).get("tag_name")).replaceFirst("^[vV]", "");
                SwingUtilities.invokeLater(() -> {
                    if (newer(latest, Forge.VERSION)) {
                        Fx.toast(this, "Version " + latest + " is out (you have " + Forge.VERSION + ")", false, "Update now", this::update);
                    } else if (asked) {
                        Fx.toast(this, "You're on the latest version", false, null, null);
                    }
                });
            } catch (Exception ex) {
                // offline is fine, only say something if they clicked the menu item
                if (asked) SwingUtilities.invokeLater(() -> Fx.toast(this, "Couldn't reach GitHub", true, null, null));
            }
        }, "update check");
        t.setDaemon(true);
        t.start();
    }

    static boolean newer(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? number(x[i]) : 0, q = i < y.length ? number(y[i]) : 0;
            if (p != q) return p > q;
        }
        return false;
    }

    private static int number(String part) {
        String digits = part.replaceAll("\\D.*", "");
        return digits.isEmpty() ? 0 : Integer.parseInt(digits);
    }

    private void update() {
        if (!confirmUnsaved()) return;
        String app = System.getProperty("jpackage.app-path");
        Path setup = app != null ? Path.of(app).getParent().resolve("Setup.exe") : null;
        try {
            if (setup != null && Files.exists(setup)) {
                new ProcessBuilder(setup.toString(), "/update").directory(new java.io.File(System.getProperty("java.io.tmpdir"))).start();
                setVisible(false);
                System.exit(0);
            }
            browse("https://github.com/" + RELEASES + "/releases/latest");
        } catch (IOException ex) {
            error("Couldn't start the update", ex);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length >= 2 && args[0].equals("--build")) {
            boolean readable = List.of(args).contains("--readable");
            Project p = Project.load(Path.of(args[1]));
            Forge forge = new Forge(p, System.out::println);
            Path zip = forge.build(!readable, !List.of(args).contains("--no-check"));
            if (List.of(args).contains("--skript")) forge.writeSkript(zip.getParent());
            return;
        }
        SwingUtilities.invokeLater(() -> {
            Theme.load();
            String last = PREFS.get("last", null);
            launch(args.length > 0 ? Path.of(args[0]) : last == null ? null : Path.of(last), "home");
            for (java.awt.Window w : java.awt.Window.getWindows()) if (w instanceof Main m && w.isVisible()) m.checkForUpdate(false);
        });
    }
}
