package dev.mirothe.vessel;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

// everything that isn't the editor. the rail picks the page, home itself is a banner of the project and its effects
final class Home extends Fx.Reveal {
    record Hooks(Runnable newProject, Runnable openProject, Consumer<Path> openPath, Runnable newEffect, Runnable build,
                 Consumer<Templates.Example> example, Consumer<Learn.Lesson> lesson, Runnable back, Runnable updates,
                 Runnable releases, Supplier<Project> project, Supplier<Boolean> editorOpen, Supplier<List<String>> recent,
                 JCheckBoxMenuItem obfuscate, JCheckBoxMenuItem check, JCheckBoxMenuItem skript, Consumer<Theme> theme,
                 java.util.function.BiConsumer<Path, Path> openEffect) {
    }

    private static final String ICONS = Stream.of("Segoe Fluent Icons", "Segoe MDL2 Assets")
            .filter(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())::contains)
            .findFirst().orElse(null);
    private final Hooks hooks;
    private final Theme t = Fx.t();
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel();
    private String section = "home";

    Home(Hooks hooks) {
        this.hooks = hooks;
        setLayout(new BorderLayout());
        setBackground(t.frame());
        content.setLayout(cards);
        content.setOpaque(false);

        add(content, BorderLayout.CENTER);
        refresh();
    }

    void refresh() {
        show(section);
    }

    void select(String name) {
        section = name;
        show(name);
    }

    // every page is built fresh when it's opened, so recent projects and progress are current
    private void show(String name) {
        JComponent page = switch (name) {
            case "examples" -> boxed(examplesPage());
            case "learn" -> boxed(learnPage());
            case "settings" -> settingsPage();
            case "about" -> boxed(aboutPage());
            default -> homePage();
        };
        content.removeAll();
        content.add(page, name);
        cards.show(content, name);
        content.revalidate();
        content.repaint();
    }

    private static JComponent boxed(JPanel page) {
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));
        holder.add(new Fx.Island(Learn.scroller(centered(page)), 0));
        return holder;
    }

    // on wide screens the pages stop growing and sit in the middle
    private static JPanel centered(JPanel page) {
        JPanel holder = new Learn.Page();
        holder.setLayout(new java.awt.LayoutManager() {
            public void addLayoutComponent(String name, Component c) { }
            public void removeLayoutComponent(Component c) { }

            public Dimension preferredLayoutSize(java.awt.Container c) {
                int w = Math.min(c.getWidth() > 0 ? c.getWidth() : 1100, 1100);
                page.setSize(w, Short.MAX_VALUE);
                return new Dimension(w, page.getPreferredSize().height);
            }

            public Dimension minimumLayoutSize(java.awt.Container c) {
                return new Dimension(0, 0);
            }

            public void layoutContainer(java.awt.Container c) {
                int w = Math.min(c.getWidth(), 1100);
                page.setBounds((c.getWidth() - w) / 2, 0, w, page.getPreferredSize().height);
            }
        });
        holder.add(page);
        return holder;
    }

    private JPanel page(String title) {
        JPanel p = new Learn.Page();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(22, 28, 28, 28));
        if (title != null) {
            JLabel l = new JLabel(title);
            l.setFont(l.getFont().deriveFont(Font.BOLD, 16f));
            l.setForeground(t.strong());
            p.add(left(l));
            p.add(Box.createVerticalStrut(16));
        }
        return p;
    }

    private JLabel heading(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 13f));
        l.setForeground(t.strong());
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        return left(l);
    }

    private static JPanel grid(int columns) {
        JPanel g = new JPanel(new GridLayout(0, columns, 12, 12));
        g.setOpaque(false);
        return g;
    }

    // a grid inside a vertical box keeps asking for more room unless its height is pinned to what it needs
    private static JComponent pinned(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return c;
    }

    // your projects up top, examples to start from, then the next lessons at the bottom
    private JComponent homePage() {
        Project current = hooks.project().get();
        List<Project> projects = new java.util.ArrayList<>();
        if (current != null) projects.add(current);
        for (String dir : hooks.recent().get()) {
            if (projects.size() == 4) break;
            if (current != null && current.dir.toString().equals(dir)) continue;
            try {
                projects.add(Project.load(Path.of(dir)));
            } catch (java.io.IOException | RuntimeException ignored) {
            }
        }

        JPanel page = new Learn.Page();
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
        page.setBorder(BorderFactory.createEmptyBorder(30, 36, 36, 36));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JPanel words = column();
        String who = ProjectForm.defaultAuthor();
        JLabel hello = new JLabel(who == null || who.isBlank() ? "Welcome back" : "Welcome back, " + who);
        hello.setFont(new Font("Segoe UI", Font.BOLD, 36));
        hello.setForeground(t.strong());
        JLabel sub = new JLabel(projects.isEmpty() ? "Make a project to start writing shaders, or start from an example."
                : "Pick up where you left off, or start something new.");
        sub.setFont(sub.getFont().deriveFont(16f));
        sub.setForeground(t.dim());
        words.add(left(hello));
        words.add(Box.createVerticalStrut(4));
        words.add(left(sub));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 6));
        buttons.setOpaque(false);
        buttons.add(wide(Learn.primary("New project"), hooks.newProject()));
        buttons.add(wide(Learn.secondary("Open folder"), hooks.openProject()));
        top.add(words, BorderLayout.CENTER);
        top.add(buttons, BorderLayout.EAST);
        page.add(pinned(top));

        if (!projects.isEmpty()) {
            page.add(Box.createVerticalStrut(34));
            page.add(section("Projects"));
            JPanel cardsRow = new JPanel(new GridLayout(1, 4, 16, 0));
            cardsRow.setOpaque(false);
            for (Project p : projects) {
                boolean now = current != null && p.dir.equals(current.dir);
                cardsRow.add(new ProjectCard(p, now, () -> {
                    if (now) hooks.openEffect().accept(p.dir, null);
                    else hooks.openPath().accept(p.dir);
                }));
            }
            for (int i = projects.size(); i < 4; i++) cardsRow.add(Box.createGlue());
            cardsRow.setPreferredSize(new Dimension(0, 290));
            page.add(pinned(cardsRow));
        }

        page.add(Box.createVerticalStrut(34));
        page.add(section("Examples to start from"));
        JPanel examples = new JPanel(new GridLayout(1, 5, 14, 0));
        examples.setOpaque(false);
        Stream.concat(Templates.EXAMPLES.stream().filter(x -> x.level().equals("Sky")), Templates.EXAMPLES.stream().filter(x -> !x.level().equals("Sky")))
                .limit(5).forEach(ex -> examples.add(new Gallery.Card(ex.choice(), 220, () -> hooks.example().accept(ex))));
        examples.setPreferredSize(new Dimension(0, Gallery.Card.height(220)));
        page.add(pinned(examples));

        page.add(Box.createVerticalStrut(34));
        page.add(section("Keep learning"));
        JPanel lessons = new JPanel(new GridLayout(1, 3, 14, 0));
        lessons.setOpaque(false);
        List<Learn.Lesson> next = Learn.LESSONS.stream().filter(l -> !Learn.finished(l)).limit(3).toList();
        if (next.isEmpty()) next = Learn.LESSONS.stream().limit(3).toList();
        for (Learn.Lesson l : next) lessons.add(new LessonCard(l, () -> reader(Learn.lessonHtml(l), l)));
        lessons.setPreferredSize(new Dimension(0, 88));
        page.add(pinned(lessons));
        return Learn.scroller(page);
    }

    private JLabel section(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 19));
        l.setForeground(t.strong());
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
        return left(l);
    }

    private static JButton wide(JButton b, Runnable action) {
        b.setFont(b.getFont().deriveFont(Font.BOLD, 15f));
        b.setPreferredSize(new Dimension(Math.max(170, b.getPreferredSize().width + 40), 50));
        b.addActionListener(e -> action.run());
        return b;
    }

    private static java.awt.Image shotOf(Project p, Project.Effect e) {
        return Project.picture(Project.shot(p.dir, e.id()));
    }

    private JPanel examplesPage() {
        JPanel page = page("Examples");
        JLabel sub = new JLabel("<html>Objects float inside the item, surfaces paint its faces, materials repaint an item's pixels, "
                + "overlays cover the screen and skies surround the player. Click one to add it to your project.</html>");
        sub.setForeground(t.dim());
        page.add(pinned(sub));
        page.add(Box.createVerticalStrut(20));
        String[][] kinds = {{"Object", "Objects"}, {"Surface", "Surfaces"}, {"Material", "Materials"}, {"Overlay", "Overlays"}, {"Sky", "Skies"}};
        for (String[] kind : kinds) {
            JPanel g = grid(3);
            for (Templates.Example ex : Templates.EXAMPLES) {
                if (ex.level().equals(kind[0])) g.add(new Gallery.Card(ex.choice(), 220, () -> hooks.example().accept(ex)));
            }
            page.add(heading(kind[1]));
            page.add(pinned(g));
            page.add(Box.createVerticalStrut(22));
        }
        return page;
    }

    private JPanel learnPage() {
        JPanel page = page("Learn");
        JLabel sub = new JLabel(Learn.finishedCount() + " of " + Learn.LESSONS.size() + " lessons done. Lessons open right in the editor.");
        sub.setForeground(t.dim());
        page.add(left(sub));
        page.add(Box.createVerticalStrut(8));
        Learn.Progress bar = new Learn.Progress();
        bar.set(Learn.finishedCount() / (float) Learn.LESSONS.size());
        bar.setMaximumSize(new Dimension(360, 4));
        page.add(left(bar));
        page.add(Box.createVerticalStrut(22));

        page.add(heading("Lessons"));
        JPanel lessons = column();
        for (Learn.Lesson l : Learn.LESSONS) {
            int number = Learn.LESSONS.indexOf(l) + 1;
            String meta = l.steps().size() + " tasks · " + l.minutes() + " min" + (Learn.finished(l) ? " · done" : "");
            lessons.add(pinned(new Row(number + ".  " + l.title(), l.blurb(), meta, false, () -> reader(Learn.lessonHtml(l), l))));
        }
        page.add(lessons);
        page.add(Box.createVerticalStrut(22));
        page.add(heading("Guides"));
        JPanel guides = column();
        for (Learn.Guide g : Learn.GUIDES) guides.add(pinned(new Row(g.title(), g.blurb(), null, false, () -> reader(g.html(), null))));
        page.add(guides);
        return page;
    }

    // a guide or lesson page shown in place of the learn list, with a way back
    private void reader(String html, Learn.Lesson lesson) {
        JPanel page = page(null);
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        JButton backButton = Learn.secondary("Back to Learn");
        backButton.addActionListener(e -> select("learn"));
        bar.add(backButton, BorderLayout.WEST);
        if (lesson != null) {
            JButton go = Learn.primary(Learn.finished(lesson) ? "Do It Again" : "Start Lesson");
            go.addActionListener(e -> hooks.lesson().accept(lesson));
            bar.add(go, BorderLayout.EAST);
        }
        page.add(pinned(bar));
        page.add(Box.createVerticalStrut(14));
        JEditorPane body = Learn.page(html);
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        page.add(body);
        content.removeAll();
        content.add(boxed(page), "reader");
        cards.show(content, "reader");
        content.revalidate();
        content.repaint();
    }

    private String settingsSection = "appearance";
    private boolean fadeSection;

    // its own list of sections on the left, and only the picked one on the right
    private JComponent settingsPage() {
        String[][] sections = {{"appearance", "\uE790", "Appearance"}, {"building", "\uE8F1", "Building"}, {"projects", "\uE8B7", "New projects"},
                {"discord", "\uE8BD", "Discord"}, {"learning", "\uE736", "Learning"}, {"about", "\uE946", "About"}};
        JPanel page = new JPanel(new BorderLayout(28, 0));
        page.setOpaque(false);
        page.setBorder(BorderFactory.createEmptyBorder(26, 30, 14, 14));
        JLabel title = new JLabel("Settings");
        title.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 26));
        title.setForeground(t.strong());
        title.setBorder(BorderFactory.createEmptyBorder(0, 6, 18, 0));
        page.add(title, BorderLayout.NORTH);

        JPanel nav = column();
        nav.setPreferredSize(new Dimension(236, 0));
        String[] picked = sections[0];
        for (String[] s : sections) {
            if (s[0].equals(settingsSection)) picked = s;
            nav.add(pinned(new NavRow(s[1], s[2], s[0].equals(settingsSection), () -> {
                if (s[0].equals(settingsSection)) return;
                settingsSection = s[0];
                fadeSection = true;
                show("settings");
            })));
            nav.add(Box.createVerticalStrut(4));
        }
        page.add(nav, BorderLayout.WEST);

        JPanel body = new Learn.Page();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(34, 40, 34, 40));
        if (!picked[0].equals("about")) {
            JLabel name = new JLabel(picked[2]);
            name.setFont(new Font("Segoe UI", Font.BOLD, 28));
            name.setForeground(t.strong());
            body.add(left(name));
            JLabel blurb = new JLabel(switch (picked[0]) {
                case "building" -> "What happens when you press Build pack.";
                case "projects" -> "Filled in for you every time you make a project.";
                case "discord" -> "Your Discord status while Hogyoku Vessel is open.";
                case "learning" -> "Lessons and how far along you are.";
                default -> "How Hogyoku Vessel looks. Changes apply right away.";
            });
            blurb.setFont(blurb.getFont().deriveFont(15f));
            blurb.setForeground(t.dim());
            body.add(left(blurb));
            body.add(Box.createVerticalStrut(30));
        }
        switch (picked[0]) {
            case "building" -> {
                body.add(setting("Obfuscate shaders", "Renames everything and adds decoys so the shipped shaders are hard to copy", new Switch(hooks.obfuscate())));
                body.add(divider());
                body.add(setting("Check every version", "Runs glslang on each version before zipping, 26.3 as Vulkan too", new Switch(hooks.check())));
                body.add(divider());
                body.add(setting("Write a Skript file", "A .sk next to the pack with commands to spawn the skies and objects", new Switch(hooks.skript())));
            }
            case "projects" -> {
                JTextField author = new JTextField(ProjectForm.defaultAuthor());
                author.setFont(author.getFont().deriveFont(14f));
                author.setPreferredSize(new Dimension(260, 40));
                author.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                    public void insertUpdate(javax.swing.event.DocumentEvent e) { ProjectForm.defaultAuthor(author.getText().strip()); }
                    public void removeUpdate(javax.swing.event.DocumentEvent e) { ProjectForm.defaultAuthor(author.getText().strip()); }
                    public void changedUpdate(javax.swing.event.DocumentEvent e) { }
                });
                body.add(setting("Default author", "Goes into the copyright line of every new project", author));
            }
            case "discord" -> body.add(setting("Show what you're working on", "Your project and file in your Discord status",
                    new Switch(Discord.enabled(), Discord::enabled)));
            case "learning" -> {
                JButton reset = Learn.secondary("Reset progress");
                reset.addActionListener(e -> {
                    Learn.resetProgress();
                    refresh();
                });
                body.add(setting("Lesson progress", Learn.finishedCount() + " of " + Learn.LESSONS.size() + " lessons marked done", reset));
            }
            case "about" -> {
                JPanel about = aboutPage();
                about.setBorder(null);
                body.add(left(about));
            }
            default -> {
                JLabel themeLabel = new JLabel("Theme");
                themeLabel.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 16));
                themeLabel.setForeground(t.strong());
                themeLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
                body.add(left(themeLabel));
                JPanel themes = new JPanel(new GridLayout(0, 3, 16, 16));
                themes.setOpaque(false);
                for (Theme option : Theme.ALL) themes.add(new Swatch(option, () -> hooks.theme().accept(option)));
                body.add(pinned(themes));
                body.add(Box.createVerticalStrut(18));
                body.add(divider());
                body.add(setting("Animations", "Slide and fade things in. Turn off for a snappier feel", new Switch(Fx.motion, Fx::motion)));
                body.add(divider());
                body.add(setting("Code text size", "How big the code is in the editor", stepper()));
            }
        }

        Fx.Reveal panel = new Fx.Reveal();
        panel.setLayout(new BorderLayout());
        panel.setOpaque(false);
        panel.add(new Fx.Island(Learn.scroller(body), 0, 16));
        page.add(panel, BorderLayout.CENTER);
        if (fadeSection) {
            fadeSection = false;
            panel.play(220, 8);
        }
        return page;
    }

    private JComponent setting(String title, String desc, JComponent control) {
        JPanel row = new JPanel(new BorderLayout(24, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(16, 0, 16, 0));
        JPanel words = column();
        JLabel name = new JLabel(title);
        name.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 16));
        name.setForeground(t.strong());
        JLabel sub = new JLabel(desc);
        sub.setFont(sub.getFont().deriveFont(14f));
        sub.setForeground(t.dim());
        words.add(left(name));
        words.add(Box.createVerticalStrut(3));
        words.add(left(sub));
        JPanel holder = new JPanel(new java.awt.GridBagLayout());
        holder.setOpaque(false);
        holder.add(control);
        row.add(words, BorderLayout.CENTER);
        row.add(holder, BorderLayout.EAST);
        return pinned(row);
    }

    private JComponent divider() {
        JComponent line = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(t.border());
                g.fillRect(0, 0, getWidth(), 1);
            }
        };
        line.setPreferredSize(new Dimension(0, 1));
        return pinned(line);
    }

    private JComponent stepper() {
        JPanel box = new JPanel(new BorderLayout());
        box.setBackground(t.raised());
        box.setPreferredSize(new Dimension(160, 40));
        JLabel value = new JLabel(String.valueOf(CodeEditor.textSize()), JLabel.CENTER);
        value.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 15));
        value.setForeground(t.strong());
        java.util.function.IntConsumer by = d -> {
            int size = Math.max(10, Math.min(24, CodeEditor.textSize() + d));
            CodeEditor.textSize(size);
            value.setText(String.valueOf(size));
        };
        JButton less = new JButton("\u2013");
        JButton more = new JButton("+");
        for (JButton b : new JButton[]{less, more}) {
            b.putClientProperty("JButton.buttonType", "toolBarButton");
            b.setFocusable(false);
            b.setFont(b.getFont().deriveFont(17f));
            b.setPreferredSize(new Dimension(44, 40));
        }
        less.addActionListener(e -> by.accept(-1));
        more.addActionListener(e -> by.accept(1));
        box.add(less, BorderLayout.WEST);
        box.add(value, BorderLayout.CENTER);
        box.add(more, BorderLayout.EAST);
        box.putClientProperty("FlatLaf.style", "arc: 10");
        return box;
    }

    private JPanel aboutPage() {
        JPanel page = page(null);
        JPanel hero = new JPanel(new BorderLayout(20, 0));
        hero.setOpaque(false);
        Fx.Badge gem = new Fx.Badge();
        gem.setPreferredSize(new Dimension(96, 96));
        hero.add(gem, BorderLayout.WEST);
        JPanel words = column();
        JLabel name = new JLabel(Forge.TOOL);
        name.setFont(name.getFont().deriveFont(Font.BOLD, 22f));
        name.setForeground(t.strong());
        JLabel sub = new JLabel("IDE for Minecraft shaders, by Mirothe  ·  version " + Forge.VERSION);
        sub.setForeground(t.dim());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.setOpaque(false);
        JButton update = Learn.primary("Check for Updates");
        update.addActionListener(e -> hooks.updates().run());
        JButton releases = Learn.secondary("Releases on GitHub");
        releases.addActionListener(e -> hooks.releases().run());
        buttons.add(update);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(releases);
        words.add(Box.createVerticalStrut(10));
        words.add(left(name));
        words.add(Box.createVerticalStrut(2));
        words.add(left(sub));
        words.add(Box.createVerticalStrut(14));
        words.add(left(buttons));
        hero.add(words, BorderLayout.CENTER);
        page.add(pinned(hero));
        page.add(Box.createVerticalStrut(28));

        page.add(heading("What it is"));
        JEditorPane what = Learn.page("""
                <p>Hogyoku Vessel is an IDE for Minecraft shaders. You write GLSL, watch it run on your GPU as you
                type, and it compiles, checks and packages your shaders into a resource pack for every version from 1.21 to 26.3.</p>
                <p>Minecraft's own core shaders changed shape five times across those versions, with different uniforms, includes,
                fog and lighting each time. Vessel keeps a template for every one of them, drops your code into all of them, and
                compiles each result so a mistake shows up in the editor instead of as a broken game.</p>
                """);
        what.setAlignmentX(Component.LEFT_ALIGNMENT);
        page.add(what);
        page.add(Box.createVerticalStrut(18));

        page.add(heading("What's inside"));
        JEditorPane can = Learn.page("""
                <table>
                <tr><td><b>Shader editor</b></td><td>GLSL highlighting, errors underlined on the exact line as you type, colors written in code shown in their own color, undo, smart indent</td></tr>
                <tr><td><b>Live preview</b></td><td>an OpenGL view that recompiles on every change and drops its own resolution when a shader gets heavy</td></tr>
                <tr><td><b>Multi version compiler</b></td><td>every effect goes into the 1.21, 1.21.6, 26.1, 26.2 and 26.3 shader layouts, with the right overlays and pack metadata</td></tr>
                <tr><td><b>Validation</b></td><td>every output compiled with glslang in every variant Minecraft uses, and 26.3 checked as Vulkan SPIR-V too</td></tr>
                <tr><td><b>Obfuscator</b></td><td>renames every function and variable, pools the numbers and adds decoy code, so shipped shaders are hard to lift</td></tr>
                <tr><td><b>Effect types</b></td><td>skies, objects inside an item, surfaces in four shapes, materials that animate an item's pixels, and overlays over the player's view</td></tr>
                <tr><td><b>Skript output</b></td><td>a generated .sk with commands to show your effects in game</td></tr>
                <tr><td><b>Themes</b></td><td>Islands Dark, Darcula, Islands Light, High Contrast and Vessel Black</td></tr>
                <tr><td><b>Learning</b></td><td>guides and lessons that check your code as you write it, plus finished shaders to pull apart</td></tr>
                </table>
                """);
        can.setAlignmentX(Component.LEFT_ALIGNMENT);
        page.add(can);
        page.add(Box.createVerticalStrut(18));
        page.add(heading("Built with"));
        JLabel built = new JLabel("<html>FlatLaf for the look, LWJGL for the live preview, glslang for the shader checks, and "
                + "JetBrains Mono for the code, used under the SIL Open Font License.</html>");
        built.setForeground(t.dim());
        page.add(pinned(built));
        return page;
    }

    private static JPanel column() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        return p;
    }

    private static <T extends JComponent> T left(T c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    // a list row: a coloured square with initials, the name, and a dim line under it
    private final class Row extends JComponent {
        private final String title, sub, meta;
        private final boolean current;
        private boolean hot;
        java.awt.Image icon;

        Row(String title, String sub, boolean current, Runnable action) {
            this(title, sub, null, current, action);
        }

        Row(String title, String sub, String meta, boolean current, Runnable action) {
            this.title = title;
            this.sub = sub;
            this.meta = meta;
            this.current = current;
            setPreferredSize(new Dimension(300, sub == null ? 34 : 50));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hot = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hot = false;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    action.run();
                }
            });
        }

        boolean matches(String q) {
            return q.isEmpty() || title.toLowerCase().contains(q) || (sub != null && sub.toLowerCase().contains(q));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            if (hot || current) {
                g2.setColor(current ? t.select() : t.hover());
                g2.fill(new RoundRectangle2D.Float(0, 1, w, h - 2, 8, 8));
            }
            int x = 10;
            if (meta == null) {
                RoundRectangle2D.Float square = new RoundRectangle2D.Float(x, (h - 30) / 2f, 30, 30, 8, 8);
                if (icon != null) {
                    java.awt.Shape outer = g2.getClip();
                    g2.clip(square);
                    g2.drawImage(icon, x, (h - 30) / 2, 30, 30, null);
                    g2.setClip(outer);
                } else {
                    g2.setColor(t.select());
                    g2.fill(square);
                    g2.setColor(t.strong());
                    g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
                    String initials = initials(title);
                    g2.drawString(initials, x + 15 - g2.getFontMetrics().stringWidth(initials) / 2f, h / 2f + 4.5f);
                }
                x += 42;
            }
            int room = w - x - (meta != null ? 160 : 12);
            g2.setFont(getFont().deriveFont(Font.BOLD, 13f));
            g2.setColor(t.strong());
            g2.drawString(Fx.fit(g2, title, room), x, sub == null ? h / 2f + 5 : 21);
            if (sub != null) {
                g2.setFont(getFont().deriveFont(12f));
                g2.setColor(t.dim());
                g2.drawString(Fx.fit(g2, sub, room), x, 39);
            }
            if (meta != null) {
                g2.setFont(getFont().deriveFont(12f));
                g2.setColor(t.dim());
                g2.drawString(meta, w - 12 - g2.getFontMetrics().stringWidth(meta), h / 2f + 4);
            }
            g2.dispose();
        }

        private static String initials(String name) {
            String[] words = name.trim().split("[\\s_-]+");
            String s = words.length > 1 ? "" + words[0].charAt(0) + words[1].charAt(0) : name.substring(0, Math.min(2, name.length()));
            return s.toUpperCase();
        }
    }

    // a little picture of a theme's colours that switches to it when clicked
    private final class Swatch extends JComponent {
        private final Theme option;
        private final Fx.Glide glow = new Fx.Glide(this, 0);

        Swatch(Theme option, Runnable action) {
            this.option = option;
            setPreferredSize(new Dimension(200, 150));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    glow.to(1);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    glow.to(0);
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    action.run();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Chrome.smooth(g);
            int w = getWidth() - 4, h = getHeight() - 32;
            g2.translate(2, 2);
            g2.setColor(option.frame());
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, 12, 12));
            g2.setColor(option.island());
            g2.fill(new RoundRectangle2D.Float(12, 12, 22, h - 24, 6, 6));
            g2.fill(new RoundRectangle2D.Float(42, 12, w - 54, h * 0.55f, 6, 6));
            g2.setColor(option.keyword());
            g2.fillRect(52, 24, 26, 4);
            g2.setColor(option.call());
            g2.fillRect(52, 34, 44, 4);
            g2.setColor(option.string());
            g2.fillRect(60, 44, 30, 4);
            g2.setColor(option.accent());
            g2.fill(new RoundRectangle2D.Float(42, h - 34, 70, 22, 6, 6));
            boolean on = option.name().equals(t.name());
            g2.setColor(on ? t.strong() : Fx.mix(t.border(), t.dim(), glow.get()));
            g2.setStroke(new java.awt.BasicStroke(on ? 2f : 1f));
            g2.draw(new RoundRectangle2D.Float(on ? -1 : 0, on ? -1 : 0, on ? w + 2 : w, on ? h + 2 : h, 13, 13));
            g2.setColor(on ? t.strong() : t.dim());
            g2.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 14));
            g2.drawString(Fx.fit(g2, option.name(), w), 2, getHeight() - 10);
            g2.dispose();
        }
    }

    // an on/off switch whose knob slides across
    private final class Switch extends JComponent {
        private boolean on;
        private final Fx.Glide knob;

        Switch(JCheckBoxMenuItem item) {
            this(item.isSelected(), v -> {
                if (item.isSelected() != v) item.doClick();
            });
        }

        Switch(boolean start, Consumer<Boolean> changed) {
            on = start;
            knob = new Fx.Glide(this, start ? 1 : 0);
            setPreferredSize(new Dimension(48, 28));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    on = !on;
                    knob.to(on ? 1 : 0);
                    changed.accept(on);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Chrome.smooth(g);
            double k = knob.get();
            int w = getWidth(), h = getHeight();
            g2.setColor(Fx.mix(t.raised(), t.accent(), k));
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, h, h));
            g2.setColor(Fx.mix(t.dim(), t.onAccent(), k));
            int d = h - 8;
            g2.fill(new java.awt.geom.Ellipse2D.Double(4 + (w - d - 8) * k, 4, d, d));
            g2.dispose();
        }
    }

    // one line in the settings list on the left
    private final class NavRow extends JComponent {
        private final String glyph, label;
        private final boolean on;
        private final Fx.Glide glow = new Fx.Glide(this, 0);

        NavRow(String glyph, String label, boolean on, Runnable action) {
            this.glyph = glyph;
            this.label = label;
            this.on = on;
            setPreferredSize(new Dimension(236, 44));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    glow.to(1);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    glow.to(0);
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    action.run();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Chrome.smooth(g);
            int w = getWidth(), h = getHeight();
            if (on || glow.get() > 0) {
                g2.setColor(on ? t.select() : Fx.mix(t.frame(), t.hover(), glow.get()));
                g2.fill(new RoundRectangle2D.Float(0, 0, w, h, 10, 10));
            }
            java.awt.Color c = on ? t.strong() : Fx.mix(t.dim(), t.text(), glow.get());
            g2.setColor(c);
            if (ICONS != null) {
                g2.setFont(new Font(ICONS, Font.PLAIN, 16));
                g2.drawString(glyph, 16, h / 2f + 7);
            }
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 15));
            g2.drawString(label, 48, h / 2f + 6);
            g2.dispose();
        }
    }

    // a project on home: its picture, then its name and how many effects it has
    private final class ProjectCard extends JComponent {
        private final Project project;
        private final boolean now;
        private final java.awt.Image image;
        private final Fx.Glide glow = new Fx.Glide(this, 0);

        ProjectCard(Project p, boolean now, Runnable action) {
            project = p;
            this.now = now;
            java.awt.Image found = null;
            for (Project.Effect e : p.effects) {
                found = shotOf(p, e);
                if (found != null) break;
            }
            image = found != null ? found : Project.picture(Project.icon(p.dir));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(p.dir.toString());
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    glow.to(1);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    glow.to(0);
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    action.run();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Chrome.smooth(g);
            double k = glow.get();
            int w = getWidth(), h = getHeight(), pic = h - 90;
            RoundRectangle2D.Float shape = new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 14, 14);
            g2.setColor(Fx.mix(t.island(), t.raised(), k));
            g2.fill(shape);
            java.awt.Shape outer = g2.getClip();
            g2.clip(shape);
            if (image != null) {
                // the picture leans in a touch on hover
                double grow = 0.04 * k;
                Chrome.cover(g2, image, new java.awt.geom.Rectangle2D.Double(-w * grow / 2, -pic * grow / 2, w * (1 + grow), pic * (1 + grow)));
            } else {
                g2.setColor(t.raised());
                g2.fillRect(0, 0, w, pic);
            }
            g2.setClip(outer);
            g2.setColor(Fx.mix(t.border(), t.strong(), k * 0.6));
            g2.draw(shape);
            if (now) {
                g2.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 13));
                int tw = g2.getFontMetrics().stringWidth("Open now") + 22;
                g2.setColor(new java.awt.Color(0, 0, 0, 200));
                g2.fill(new RoundRectangle2D.Float(12, 12, tw, 26, 13, 13));
                g2.setColor(java.awt.Color.WHITE);
                g2.drawString("Open now", 23, 30);
            }
            g2.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 18));
            g2.setColor(t.strong());
            g2.drawString(Fx.fit(g2, project.name, w - 36), 18, pic + 38);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            g2.setColor(t.dim());
            String meta = project.effects.size() + (project.effects.size() == 1 ? " effect" : " effects");
            g2.drawString(Fx.fit(g2, meta, w - 36), 18, pic + 64);
            g2.dispose();
        }
    }

    // the next lesson: its number in a square, the title, and how long it takes
    private final class LessonCard extends JComponent {
        private final Learn.Lesson lesson;
        private final Fx.Glide glow = new Fx.Glide(this, 0);

        LessonCard(Learn.Lesson lesson, Runnable action) {
            this.lesson = lesson;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(lesson.blurb());
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    glow.to(1);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    glow.to(0);
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    action.run();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Chrome.smooth(g);
            double k = glow.get();
            int w = getWidth(), h = getHeight();
            g2.setColor(Fx.mix(t.island(), t.raised(), k));
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, 14, 14));
            int n = Learn.LESSONS.indexOf(lesson) + 1;
            g2.setColor(Fx.mix(t.raised(), t.hover(), k));
            g2.fill(new RoundRectangle2D.Float(18, (h - 48) / 2f, 48, 48, 12, 12));
            g2.setColor(t.strong());
            g2.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 18));
            String num = String.valueOf(n);
            g2.drawString(num, 42 - g2.getFontMetrics().stringWidth(num) / 2f, h / 2f + 7);
            g2.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 16));
            g2.drawString(Fx.fit(g2, lesson.title(), w - 140), 84, h / 2f - 3);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 1).deriveFont(13.5f));
            g2.setColor(t.dim());
            g2.drawString("Lesson " + n + "  \u00b7  " + lesson.minutes() + " min", 84, h / 2f + 19);
            if (ICONS != null) {
                g2.setFont(new Font(ICONS, Font.PLAIN, 12));
                g2.drawString("\uE76C", (float) (w - 32 + 4 * k), h / 2f + 6);
            }
            g2.dispose();
        }
    }
}
