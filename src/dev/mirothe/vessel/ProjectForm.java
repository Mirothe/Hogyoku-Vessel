package dev.mirothe.vessel;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.prefs.Preferences;

// asks for the pack's details before a project gets made, and edits them later from Project Settings
final class ProjectForm extends JDialog {
    record Answers(Path dir, String name, String author, String description, String namespace, String discordImage) {
    }

    private static final Preferences PREFS = Preferences.userNodeForPackage(ProjectForm.class);

    private final JTextField name = field("My Skies");
    private final JTextField author = field("Who made it");
    private final JTextField description = field("Shows under the pack in the resource pack menu");
    private final JTextField namespace = field("skyboxes");
    private final JTextField folder = field("");
    private final JTextField discord = field("https://... a link to the project icon, optional");
    private final JLabel problem = new JLabel(" ");
    private final boolean editing;
    private boolean namespaceTouched;
    private boolean folderTouched;
    private Answers answers;

    private ProjectForm(JFrame owner, Project existing) {
        super(owner, true);
        editing = existing != null;
        setUndecorated(true);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Fx.t().island());
        root.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Fx.LINE),
                BorderFactory.createEmptyBorder(26, 30, 22, 30)));

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        JLabel title = new JLabel(editing ? "Project Settings" : "New Project");
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 16f));
        title.setForeground(Fx.WHITE);
        JLabel sub = new JLabel(editing ? "These end up in the pack and in the generated Skript."
                : "A few questions first. You can change all of this later in File > Project Settings.");
        sub.setForeground(Fx.DIM);
        body.add(left(title));
        body.add(Box.createVerticalStrut(4));
        body.add(left(sub));
        body.add(Box.createVerticalStrut(22));

        body.add(row("Pack name", "what players see in the resource pack list", name));
        body.add(row("Author", "goes into the copyright line in every shader", author));
        body.add(row("Description", "the line under the pack name", description));
        body.add(row("Namespace", "lowercase id for the items, also the Skript command", namespace));
        if (editing) body.add(row("Discord image", "shows big in your Discord status while you work on this project", discord));
        if (!editing) {
            JButton browse = new JButton("Browse");
            browse.setFocusable(false);
            browse.addActionListener(e -> browse());
            JPanel pick = new JPanel(new BorderLayout(8, 0));
            pick.setOpaque(false);
            pick.add(folder, BorderLayout.CENTER);
            pick.add(browse, BorderLayout.EAST);
            body.add(row("Folder", "the project lives here, the built pack goes in its build folder", pick));
        }
        problem.setForeground(Fx.RED);
        body.add(left(problem));
        root.add(body, BorderLayout.CENTER);

        JButton cancel = new JButton("Cancel");
        cancel.setFocusable(false);
        cancel.addActionListener(e -> dispose());
        JButton ok = new JButton(editing ? "Save" : "Create project");
        ok.putClientProperty("JButton.buttonType", "default");
        ok.putClientProperty("FlatLaf.style", "margin: 5,16,5,16");
        ok.addActionListener(e -> submit());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(ok);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(ok);
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "cancel");
        root.getActionMap().put("cancel", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        if (editing) {
            name.setText(existing.name);
            author.setText(existing.author);
            description.setText(existing.description);
            namespace.setText(existing.namespace);
            discord.setText(existing.discordImage);
            namespaceTouched = true;
        } else {
            name.setText("My Skies");
            author.setText(defaultAuthor());
            description.setText("Custom skies");
            follow();
        }
        // until someone types in these two, they keep following the pack name
        name.getDocument().addDocumentListener(changed(this::follow));
        namespace.getDocument().addDocumentListener(changed(() -> {
            if (namespace.isFocusOwner()) namespaceTouched = true;
        }));
        folder.getDocument().addDocumentListener(changed(() -> {
            if (folder.isFocusOwner()) folderTouched = true;
        }));

        pack();
        setSize(Math.max(620, getWidth()), getHeight());
        Fx.round(this, 16);
        setLocationRelativeTo(owner);
    }

    static String defaultAuthor() {
        return PREFS.get("author", System.getProperty("user.name", ""));
    }

    static void defaultAuthor(String name) {
        PREFS.put("author", name);
    }

    static Answers ask(JFrame owner, Project existing) {
        ProjectForm form = new ProjectForm(owner, existing);
        // the dialog is modal, so the fade has to start before setVisible blocks
        form.setOpacity(0f);
        Fx.animate(220, p -> form.setOpacity((float) p), null);
        form.setVisible(true);
        return form.answers;
    }

    private void follow() {
        String n = name.getText().strip();
        if (!namespaceTouched) {
            String id = n.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
            namespace.setText(id.isEmpty() ? "skyboxes" : id);
        }
        if (!folderTouched && !editing) {
            Path docs = Path.of(System.getProperty("user.home"), "Documents", "Hogyoku Vessel");
            folder.setText(docs.resolve(n.isEmpty() ? "My Skies" : n.replaceAll("[\\\\/:*?\"<>|]", "")).toString());
        }
    }

    private void browse() {
        JFileChooser fc = new JFileChooser(folder.getText());
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Where should the project live?");
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        folderTouched = true;
        folder.setText(fc.getSelectedFile().toPath().resolve(name.getText().strip()).toString());
    }

    private void submit() {
        String n = name.getText().strip();
        String ns = namespace.getText().strip();
        if (n.isEmpty()) {
            problem.setText("The pack needs a name.");
            return;
        }
        if (!ns.matches("[a-z0-9_.-]+")) {
            problem.setText("The namespace can only use lowercase letters, numbers, _ . and -");
            return;
        }
        Path dir = null;
        if (!editing) {
            if (folder.getText().isBlank()) {
                problem.setText("Pick a folder for the project.");
                return;
            }
            dir = Path.of(folder.getText().strip());
            if (Files.exists(dir.resolve("project.json"))) {
                problem.setText("There's already a project in that folder. Open it instead, or pick another one.");
                return;
            }
        }
        PREFS.put("author", author.getText().strip());
        String link = discord.getText().strip();
        if (!link.isEmpty() && !link.startsWith("https://")) {
            problem.setText("The Discord image has to be a link that starts with https://");
            return;
        }
        answers = new Answers(dir, n, author.getText().strip(), description.getText().strip(), ns, link);
        dispose();
    }

    private static JTextField field(String hint) {
        JTextField f = new JTextField();
        f.putClientProperty("JTextField.placeholderText", hint);
        f.putClientProperty("FlatLaf.style", "margin: 5,8,5,8");
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, f.getPreferredSize().height + 14));
        return f;
    }

    private static JComponent row(String label, String hint, JComponent input) {
        JPanel row = new JPanel(new BorderLayout(0, 5));
        row.setOpaque(false);
        JPanel words = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        words.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setForeground(Fx.TEXT);
        l.setFont(l.getFont().deriveFont(java.awt.Font.BOLD, 12.5f));
        JLabel h = new JLabel("   " + hint);
        h.setForeground(Fx.DIM);
        h.setFont(h.getFont().deriveFont(11.5f));
        words.add(l);
        words.add(h);
        row.add(words, BorderLayout.NORTH);
        row.add(input, BorderLayout.CENTER);
        row.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    private static JComponent left(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private static DocumentListener changed(Runnable r) {
        return new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { r.run(); }
            public void removeUpdate(DocumentEvent e) { r.run(); }
            public void changedUpdate(DocumentEvent e) { }
        };
    }
}
