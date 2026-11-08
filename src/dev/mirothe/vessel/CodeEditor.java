package dev.mirothe.vessel;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Element;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.undo.UndoManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CodeEditor extends JScrollPane {
    private final Theme theme = Fx.t();

    private static final Set<String> BUILTINS = Set.of("abs", "acos", "asin", "atan", "ceil", "clamp", "cos", "cross", "degrees",
            "distance", "dot", "exp", "exp2", "floor", "fract", "inversesqrt", "length", "log", "log2", "max", "min", "mix", "mod",
            "normalize", "pow", "radians", "reflect", "refract", "round", "sign", "sin", "smoothstep", "sqrt", "step", "tan",
            "texture", "transpose", "inverse", "determinant", "fwidth", "dFdx", "dFdy", "floatBitsToUint", "uintBitsToFloat");
    private static final Pattern TOKEN = Pattern.compile(
            "(?<comment>//[^\\n]*|/\\*.*?(?:\\*/|$))|(?<pre>^[ \\t]*#[^\\n]*)|(?<word>[A-Za-z_]\\w*)(?<call>\\s*\\()?|(?<num>\\b\\d+\\.?\\d*(?:[eE][-+]?\\d+)?[fFuU]?\\b|\\.\\d+)",
            Pattern.DOTALL | Pattern.MULTILINE);

    private static final Pattern COLOR = Pattern.compile("vec3\\(\\s*([0-9]*\\.?[0-9]+)\\s*,\\s*([0-9]*\\.?[0-9]+)\\s*,\\s*([0-9]*\\.?[0-9]+)\\s*\\)");

    private final JTextPane text = new JTextPane() {
        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(theme.editor());
            g.fillRect(0, 0, getWidth(), getHeight());
            try {
                Rectangle2D r = modelToView2D(getCaretPosition());
                if (r != null && getSelectionStart() == getSelectionEnd()) {
                    g.setColor(theme.currentLine());
                    g.fillRect(0, (int) r.getY(), getWidth(), (int) r.getHeight());
                }
            } catch (BadLocationException ignored) {
            }
            super.paintComponent(g);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return getUI().getPreferredSize(this).width <= getParent().getSize().width;
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            int line = lineAt(viewToModel2D(e.getPoint())) + 1;
            for (Preview.Problem p : problems) if (p.line() == line) return p.message();
            return null;
        }
    };
    private final StyledDocument doc = text.getStyledDocument();
    private final UndoManager undo = new UndoManager();
    private final Timer recolor = new Timer(150, e -> highlight());
    private final Highlighter.HighlightPainter squiggle = new Squiggle();
    private final Path file;
    private List<Preview.Problem> problems = List.of();
    private boolean dirty;
    private boolean painting;
    private Runnable onChange = () -> { };
    private int editFrom = Integer.MAX_VALUE;
    private int editTo = -1;

    CodeEditor(Path file) throws IOException {
        this.file = file;
        text.setBackground(theme.editor());
        text.setOpaque(false);
        getViewport().setBackground(theme.editor());
        text.setForeground(theme.text());
        text.setCaretColor(theme.strong());
        javax.swing.text.DefaultCaret caret = new javax.swing.text.DefaultCaret();
        caret.setBlinkRate(500);
        text.setCaret(caret);
        text.putClientProperty("caretWidth", 2);
        text.setSelectionColor(theme.textSelect());
        text.setSelectedTextColor(null);
        text.setFont(mono(textSize()));
        OPEN.add(this);
        text.setMargin(new java.awt.Insets(0, 6, 0, 0));
        text.setToolTipText("");
        setViewportView(text);
        setRowHeaderView(new Gutter());
        setBorder(null);
        Fx.smooth(this, false);

        text.setText(Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\t', ' '));
        text.setCaretPosition(0);
        highlight();
        recolor.setRepeats(false);

        doc.addUndoableEditListener(e -> {
            // recoloring also fires edits, and those shouldn't end up on the undo stack
            if (!painting) undo.addEdit(e.getEdit());
        });
        doc.addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                if (!painting) {
                    if (editTo > e.getOffset()) editTo += e.getLength();
                    editFrom = Math.min(editFrom, e.getOffset());
                    editTo = Math.max(editTo, e.getOffset() + e.getLength());
                }
                changed();
            }

            public void removeUpdate(DocumentEvent e) {
                if (!painting) {
                    if (editTo > e.getOffset()) editTo = Math.max(e.getOffset(), editTo - e.getLength());
                    editFrom = Math.min(editFrom, e.getOffset());
                    editTo = Math.max(editTo, e.getOffset());
                }
                changed();
            }
            public void changedUpdate(DocumentEvent e) { }
        });
        keys();
        text.addCaretListener(e -> {
            getRowHeader().repaint();
            text.repaint();
        });
    }

    private static final String MONO = loadMono();

    // the styled document asks for bold and italic by family name, so every face has to be registered
    private static String loadMono() {
        try {
            for (String face : List.of("Regular", "Bold", "Italic")) {
                try (var in = CodeEditor.class.getResourceAsStream("/fonts/JetBrainsMono-" + face + ".ttf")) {
                    java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(Font.createFont(Font.TRUETYPE_FONT, in));
                }
            }
            return "JetBrains Mono";
        } catch (Exception e) {
            return "Consolas";
        }
    }

    private static final java.util.prefs.Preferences PREFS = java.util.prefs.Preferences.userNodeForPackage(CodeEditor.class);
    private static final java.util.Set<CodeEditor> OPEN = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    static int textSize() {
        return PREFS.getInt("text.size", 14);
    }

    static void textSize(int size) {
        PREFS.putInt("text.size", size);
        for (CodeEditor ed : OPEN) {
            ed.text.setFont(mono(size));
            ed.getRowHeader().getView().setFont(mono(size - 1));
            ed.getRowHeader().revalidate();
            ed.repaint();
        }
    }

    static Font mono(int size) {
        return new Font(MONO, Font.PLAIN, size);
    }

    private void changed() {
        if (painting) return;
        dirty = true;
        recolor.restart();
        onChange.run();
        getRowHeader().repaint();
    }

    private void keys() {
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), () -> { if (undo.canUndo()) undo.undo(); });
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), () -> { if (undo.canRedo()) undo.redo(); });
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), () -> text.replaceSelection("    "));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), () -> {
            String before = lineBeforeCaret();
            String indent = before.replaceAll("^(\\s*).*", "$1");
            if (before.stripTrailing().endsWith("{")) indent += "    ";
            text.replaceSelection("\n" + indent);
        });
    }

    private void bind(KeyStroke key, Runnable action) {
        String name = key.toString();
        text.getInputMap(JComponent.WHEN_FOCUSED).put(key, name);
        text.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private String lineBeforeCaret() {
        int caret = text.getCaretPosition();
        Element line = doc.getDefaultRootElement().getElement(lineAt(caret));
        try {
            return doc.getText(line.getStartOffset(), caret - line.getStartOffset());
        } catch (BadLocationException e) {
            return "";
        }
    }

    private int lineAt(int offset) {
        return doc.getDefaultRootElement().getElementIndex(offset);
    }

    // only the lines that changed since last time get recolored, which keeps typing smooth in big files.
    // block comments can reach across lines, so a file with one in it still gets the full pass
    private void highlight() {
        painting = true;
        try {
            String all = doc.getText(0, doc.getLength());
            int from = 0, to = all.length();
            if (editFrom <= editTo && !all.contains("/*")) {
                Element root = doc.getDefaultRootElement();
                from = root.getElement(lineAt(Math.min(editFrom, all.length()))).getStartOffset();
                to = Math.min(all.length(), root.getElement(lineAt(Math.min(editTo, all.length()))).getEndOffset());
            }
            editFrom = Integer.MAX_VALUE;
            editTo = -1;
            String src = all.substring(from, to);
            doc.setCharacterAttributes(from, src.length(), style(theme.text()), true);
            Matcher m = TOKEN.matcher(src);
            while (m.find()) {
                Color c = null;
                int start = from + m.start(), end = from + m.end();
                if (m.group("comment") != null) c = theme.comment();
                else if (m.group("pre") != null) c = theme.preproc();
                else if (m.group("num") != null) c = theme.number();
                else if (m.group("word") != null) {
                    String w = m.group("word");
                    end = from + m.end("word");
                    if (Obfuscator.TYPES.contains(w)) c = theme.type();
                    else if (Obfuscator.KEYWORDS.contains(w)) c = theme.keyword();
                    else if (BUILTINS.contains(w)) c = theme.builtin();
                    else if (m.group("call") != null) c = theme.call();
                }
                if (c != null) {
                    AttributeSet a = style(c);
                    if (m.group("comment") != null) a = italic(a);
                    doc.setCharacterAttributes(start, end - start, a, true);
                }
            }
            // a written out color shows in its own color, lifted a bit when it would be too dark to read
            Matcher color = COLOR.matcher(src);
            while (color.find()) {
                try {
                    Color c = new Color(Math.min(1f, Float.parseFloat(color.group(1))), Math.min(1f, Float.parseFloat(color.group(2))),
                            Math.min(1f, Float.parseFloat(color.group(3))));
                    float light = (0.2126f * c.getRed() + 0.7152f * c.getGreen() + 0.0722f * c.getBlue()) / 255f;
                    if (theme.dark() && light < 0.45f) c = Fx.mix(c, Color.WHITE, Math.min(0.7f, (0.45f - light) * 1.3f));
                    if (!theme.dark() && light > 0.6f) c = Fx.mix(c, Color.BLACK, Math.min(0.6f, (light - 0.6f) * 1.5f));
                    doc.setCharacterAttributes(from + color.start(), color.end() - color.start(), style(c), true);
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (BadLocationException ignored) {
        } finally {
            painting = false;
        }
    }

    private static AttributeSet italic(AttributeSet a) {
        SimpleAttributeSet s = new SimpleAttributeSet(a);
        StyleConstants.setItalic(s, true);
        return s;
    }

    private static AttributeSet style(Color c) {
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setForeground(s, c);
        return s;
    }

    void showProblems(List<Preview.Problem> list) {
        problems = list;
        Highlighter h = text.getHighlighter();
        h.removeAllHighlights();
        Element root = doc.getDefaultRootElement();
        for (Preview.Problem p : list) {
            if (p.line() < 1 || p.line() > root.getElementCount()) continue;
            Element line = root.getElement(p.line() - 1);
            try {
                h.addHighlight(line.getStartOffset(), Math.max(line.getStartOffset() + 1, line.getEndOffset() - 1), squiggle);
            } catch (BadLocationException ignored) {
            }
        }
        getRowHeader().repaint();
    }

    // colours that were worked out from the theme, like the lifted vec3 swatches, get worked out again
    void retheme() {
        editFrom = Integer.MAX_VALUE;
        editTo = -1;
        highlight();
        repaint();
    }

    void goToLine(int line) {
        Element root = doc.getDefaultRootElement();
        if (line < 1 || line > root.getElementCount()) return;
        text.setCaretPosition(root.getElement(line - 1).getStartOffset());
        SwingUtilities.invokeLater(text::requestFocusInWindow);
    }

    void save() throws IOException {
        Files.writeString(file, text.getText(), StandardCharsets.UTF_8);
        dirty = false;
    }

    void reload() throws IOException {
        painting = true;
        text.setText(Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n"));
        painting = false;
        highlight();
        dirty = false;
    }

    String source() {
        return text.getText();
    }

    Path file() {
        return file;
    }

    void undo() {
        if (undo.canUndo()) undo.undo();
    }

    void redo() {
        if (undo.canRedo()) undo.redo();
    }

    boolean canUndo() {
        return undo.canUndo();
    }

    boolean canRedo() {
        return undo.canRedo();
    }

    boolean dirty() {
        return dirty;
    }

    void onChange(Runnable r) {
        onChange = r;
    }

    void onCaret(Runnable r) {
        text.addCaretListener(e -> r.run());
    }

    String position() {
        int dot = text.getCaretPosition();
        int line = lineAt(dot);
        return "Ln " + (line + 1) + ", Col " + (dot - doc.getDefaultRootElement().getElement(line).getStartOffset() + 1);
    }

    private final class Gutter extends JComponent {
        Gutter() {
            setFont(mono(textSize() - 1));
        }

        @Override
        public Dimension getPreferredSize() {
            int lines = doc.getDefaultRootElement().getElementCount();
            int w = getFontMetrics(getFont()).stringWidth(String.valueOf(Math.max(lines, 100))) + 34;
            return new Dimension(w, text.getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(theme.editor());
            Rectangle clip = g.getClipBounds();
            g.fillRect(clip.x, clip.y, clip.width, clip.height);
            Element root = doc.getDefaultRootElement();
            int first = root.getElementIndex(text.viewToModel2D(new java.awt.Point(0, clip.y)));
            int last = root.getElementIndex(text.viewToModel2D(new java.awt.Point(0, clip.y + clip.height)));
            ((Graphics2D) g).setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int ascent = g.getFontMetrics().getAscent();
            int caretLine = lineAt(text.getCaretPosition());
            for (int i = first; i <= last; i++) {
                try {
                    Rectangle2D r = text.modelToView2D(root.getElement(i).getStartOffset());
                    int y = (int) r.getY();
                    boolean bad = false;
                    for (Preview.Problem p : problems) if (p.line() == i + 1) bad = true;
                    if (bad) {
                        g.setColor(theme.error());
                        g.fillOval(6, y + (int) r.getHeight() / 2 - 3, 6, 6);
                    }
                    String n = String.valueOf(i + 1);
                    g.setColor(bad ? theme.error() : i == caretLine ? theme.text() : theme.gutter());
                    g.drawString(n, getWidth() - 12 - g.getFontMetrics().stringWidth(n), y + ascent + 1);
                } catch (BadLocationException ignored) {
                }
            }
        }
    }

    private final class Squiggle extends DefaultHighlighter.DefaultHighlightPainter {
        Squiggle() {
            super(null);
        }

        @Override
        public Shape paintLayer(Graphics g, int start, int end, Shape bounds, JTextComponent c, javax.swing.text.View view) {
            try {
                Rectangle r = view.modelToView(start, javax.swing.text.Position.Bias.Forward, end, javax.swing.text.Position.Bias.Backward, bounds).getBounds();
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(theme.error());
                g2.setStroke(new BasicStroke(1f));
                int y = r.y + r.height - 2;
                for (int x = r.x; x < r.x + r.width; x += 4) {
                    g2.drawLine(x, y, x + 2, y + 2);
                    g2.drawLine(x + 2, y + 2, x + 4, y);
                }
                return r;
            } catch (BadLocationException e) {
                return null;
            }
        }
    }
}
