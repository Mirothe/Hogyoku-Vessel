package dev.mirothe.vessel;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Locale;

final class Palette extends JDialog {
    record Item(String label, String hint, Runnable run) {
    }

    private final List<Item> items;
    private final DefaultListModel<Item> shown = new DefaultListModel<>();
    private final JList<Item> list = new JList<>(shown);
    private final JTextField query = new JTextField();
    private boolean closing;

    private Palette(JFrame owner, String placeholder, List<Item> items) {
        super(owner);
        this.items = items;
        setUndecorated(true);

        query.putClientProperty("JTextField.placeholderText", placeholder);
        query.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Fx.LINE),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        query.setBackground(Fx.t().raised());
        query.setForeground(Fx.WHITE);
        query.setFont(query.getFont().deriveFont(14f));
        query.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filter(); }
            public void removeUpdate(DocumentEvent e) { filter(); }
            public void changedUpdate(DocumentEvent e) { }
        });

        list.setBackground(Fx.t().raised());
        list.setVisibleRowCount(11);
        list.setCellRenderer((l, item, index, selected, focus) -> {
            JPanel row = new JPanel(new BorderLayout());
            row.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
            row.setBackground(selected ? Fx.t().select() : l.getBackground());
            JLabel name = new JLabel(item.label());
            name.setForeground(selected ? Fx.WHITE : Fx.TEXT);
            JLabel hint = new JLabel(item.hint() == null ? "" : item.hint());
            hint.setForeground(Fx.DIM);
            hint.setFont(hint.getFont().deriveFont(12f));
            row.add(name, BorderLayout.WEST);
            row.add(hint, BorderLayout.EAST);
            return row;
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                run();
            }
        });

        key("ESCAPE", this::close);
        key("ENTER", this::run);
        key("DOWN", () -> move(1));
        key("UP", () -> move(-1));

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(null);
        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(BorderFactory.createLineBorder(Fx.LINE));
        root.add(query, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        setContentPane(root);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowDeactivated(WindowEvent e) {
                close();
            }
        });
        filter();
        pack();
        setSize(620, getHeight());
        Fx.round(this, 10);
        setLocation(owner.getX() + (owner.getWidth() - getWidth()) / 2, owner.getY() + 64);
    }

    static void show(JFrame owner, String placeholder, List<Item> items) {
        Palette p = new Palette(owner, placeholder, items);
        Fx.fadeIn(p, -8);
        SwingUtilities.invokeLater(p.query::requestFocusInWindow);
    }

    private void key(String stroke, Runnable action) {
        query.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(stroke), stroke);
        query.getActionMap().put(stroke, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private void filter() {
        String q = query.getText().strip().toLowerCase(Locale.ROOT);
        shown.clear();
        for (Item item : items) if (matches(item.label().toLowerCase(Locale.ROOT), q)) shown.addElement(item);
        if (!shown.isEmpty()) list.setSelectedIndex(0);
    }

    // letters in order, gaps allowed, so "bp" finds "Build Pack"
    static boolean matches(String text, String q) {
        int at = 0;
        for (char c : q.toCharArray()) {
            if (c == ' ') continue;
            at = text.indexOf(c, at);
            if (at < 0) return false;
            at++;
        }
        return true;
    }

    private void move(int by) {
        if (shown.isEmpty()) return;
        int i = Math.floorMod(list.getSelectedIndex() + by, shown.size());
        list.setSelectedIndex(i);
        list.ensureIndexIsVisible(i);
    }

    private void run() {
        Item item = list.getSelectedValue();
        close();
        if (item != null) SwingUtilities.invokeLater(item.run());
    }

    private void close() {
        if (closing) return;
        closing = true;
        Fx.fadeOut(this, null);
    }
}
