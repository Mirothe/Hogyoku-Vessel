package dev.mirothe.vessel;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Consumer;

// the "new effect" picker: a grid of cards with a rendered thumbnail of every example
final class Gallery extends JDialog {
    record Choice(String id, String name, String kind, String blurb, String source, BufferedImage thumb) {
    }

    private boolean closing;

    private Gallery(JFrame owner, List<Choice> choices, Consumer<Choice> picked) {
        super(owner);
        setUndecorated(true);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Fx.t().island());
        root.setBorder(BorderFactory.createLineBorder(Fx.t().border()));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.setBorder(BorderFactory.createEmptyBorder(18, 22, 10, 22));
        JLabel title = new JLabel("New Effect");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setForeground(Fx.WHITE);
        JLabel sub = new JLabel("Pick a starting point. Objects and surfaces live in the item, materials repaint it, overlays cover the screen. Esc to close.");
        sub.setForeground(Fx.DIM);
        head.add(title, BorderLayout.NORTH);
        head.add(sub, BorderLayout.SOUTH);
        root.add(head, BorderLayout.NORTH);

        JPanel grid = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 14));
        grid.setOpaque(false);
        for (Choice c : choices) {
            grid.add(new Card(c, 268, () -> {
                close();
                picked.accept(c);
            }));
        }
        int rows = (choices.size() + 2) / 3;
        grid.setPreferredSize(new Dimension(3 * (268 + 14) + 14, rows * (Card.height(268) + 14) + 14));
        JScrollPane scroll = new JScrollPane(grid);
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 14, 14, 0));
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        Fx.smooth(scroll, true);
        root.add(scroll, BorderLayout.CENTER);
        setContentPane(root);

        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "close");
        root.getActionMap().put("close", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                close();
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowDeactivated(WindowEvent e) {
                close();
            }
        });

        setSize(3 * (268 + 14) + 14 + 44, Math.min(660, owner.getHeight() - 80));
        Fx.round(this, 12);
        setLocation(owner.getX() + (owner.getWidth() - getWidth()) / 2, owner.getY() + (owner.getHeight() - getHeight()) / 2);
    }

    static void show(JFrame owner, List<Choice> choices, Consumer<Choice> picked) {
        Fx.fadeIn(new Gallery(owner, choices, picked), 12);
    }

    private void close() {
        if (closing) return;
        closing = true;
        Fx.fadeOut(this, null);
    }

    // a thumbnail card that lifts and lights up its edge while the mouse is over it
    static final class Card extends JComponent {
        private final Choice choice;
        private final int width;
        private float lift;
        private boolean over;

        static int height(int width) {
            return width * 5 / 8 + 58;
        }

        Card(Choice choice, int width, Runnable action) {
            this.choice = choice;
            this.width = width;
            setPreferredSize(new Dimension(width, height(width)));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(choice.blurb());
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    over = true;
                    lift = 1;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    over = false;
                    lift = 0;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    action.run();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.clipRect(0, 0, getWidth(), getHeight());
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int pad = 3;
            int w = getWidth() - pad * 2, h = getHeight() - pad * 2;
            int imageH = Math.min(w * 5 / 8, h - 58);
            g2.translate(pad, pad);
            Theme t = Fx.t();
            Shape card = new RoundRectangle2D.Float(0, 0, w, h, 12, 12);
            g2.setColor(lift > 0 ? t.hover() : t.raised());
            g2.fill(card);

            // clip() narrows the clip the scroll pane handed us, setClip() would throw it away and paint over the header
            Shape outer = g2.getClip();
            g2.clip(new RoundRectangle2D.Float(0, 0, w, imageH + 18, 12, 12));
            if (choice.thumb() != null) {
                // crop to fill instead of stretching, so wide cards don't squash the picture
                BufferedImage img = choice.thumb();
                double scale = Math.max(w / (double) img.getWidth(), imageH / (double) img.getHeight());
                int dw = (int) Math.ceil(img.getWidth() * scale), dh = (int) Math.ceil(img.getHeight() * scale);
                g2.drawImage(img, (w - dw) / 2, (imageH - dh) / 2, dw, dh, null);
            } else {
                g2.setColor(t.editor());
                g2.fillRect(0, 0, w, imageH);
                g2.setColor(lift > 0 ? t.strong() : t.dim());
                g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int cx = w / 2, cy = imageH / 2;
                g2.drawLine(cx - 14, cy, cx + 14, cy);
                g2.drawLine(cx, cy - 14, cx, cy + 14);
            }
            g2.setClip(outer);
            g2.setColor(lift > 0 ? t.hover() : t.raised());
            g2.fillRect(0, imageH, w, 18);

            g2.setFont(getFont().deriveFont(Font.BOLD, 13f));
            g2.setColor(t.strong());
            g2.drawString(Fx.fit(g2, choice.name(), w - 28), 14, imageH + 20);
            g2.setFont(getFont().deriveFont(12f));
            g2.setColor(Fx.DIM);
            g2.drawString(Fx.fit(g2, choice.blurb(), w - 28), 14, imageH + 40);

            String kind = choice.kind().toUpperCase();
            g2.setFont(getFont().deriveFont(Font.BOLD, 9.5f));
            int tw = g2.getFontMetrics().stringWidth(kind) + 14;
            g2.setColor(new Color(0, 0, 0, 150));
            g2.fill(new RoundRectangle2D.Float(10, 10, tw, 18, 18, 18));
            g2.setColor(new Color(255, 255, 255, 220));
            g2.drawString(kind, 17, 23);

            g2.setStroke(new BasicStroke(1.2f));
            g2.setColor(lift > 0 ? t.accent() : t.border());
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 12, 12));
            g2.dispose();
        }
    }
}
