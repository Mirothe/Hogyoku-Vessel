package dev.mirothe.vessel;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.JWindow;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;

final class Fx {
    static final Color BACKDROP = Theme.LIVE.frame(), BLACK = Theme.LIVE.island(), SIDE = Theme.LIVE.island(), LINE = Theme.LIVE.border(),
            TEXT = Theme.LIVE.text(), DIM = Theme.LIVE.dim(), WHITE = Theme.LIVE.strong(), RED = Theme.LIVE.error();

    private static final List<JWindow> toasts = new ArrayList<>();

    private Fx() {
    }

    static Theme t() {
        return Theme.LIVE;
    }

    private static final java.util.prefs.Preferences PREFS = java.util.prefs.Preferences.userNodeForPackage(Fx.class);
    static boolean motion = PREFS.getBoolean("motion", true);

    static void motion(boolean on) {
        motion = on;
        PREFS.putBoolean("motion", on);
    }

    // eased out, so things arrive fast and settle softly
    static Timer animate(int ms, DoubleConsumer step, Runnable done) {
        if (!motion) {
            step.accept(1);
            if (done != null) done.run();
            return new Timer(ms, null);
        }
        long start = System.nanoTime();
        Timer t = new Timer(1000 / 60, null);
        t.addActionListener(e -> {
            double p = Math.min(1, (System.nanoTime() - start) / 1e6 / ms);
            step.accept(1 - Math.pow(1 - p, 3));
            if (p >= 1) {
                t.stop();
                if (done != null) done.run();
            }
        });
        step.accept(0);
        t.start();
        return t;
    }

    // a number that slides toward wherever it's told to go and repaints its owner on the way
    static final class Glide {
        private final JComponent owner;
        private double value, target;
        private Timer timer;

        Glide(JComponent owner, double start) {
            this.owner = owner;
            value = target = start;
        }

        double get() {
            return value;
        }

        void value0() {
            value = 0;
        }

        void to(double t) {
            target = t;
            if (!motion) {
                value = t;
                owner.repaint();
                return;
            }
            if (timer != null || value == target) return;
            timer = new Timer(1000 / 60, e -> {
                value += (target - value) * 0.22;
                if (Math.abs(target - value) < 0.004) {
                    value = target;
                    timer.stop();
                    timer = null;
                }
                owner.repaint();
            });
            timer.start();
        }
    }

    static void opacity(Window w, float a) {
        try {
            w.setOpacity(Math.max(0f, Math.min(1f, a)));
        } catch (RuntimeException ignored) {
            // no translucent windows on this screen, it just pops in
        }
    }

    static Color mix(Color a, Color b, double t) {
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t), (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t), (int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    static void hover(AbstractButton b, Color idle, Color hot) {
        b.setForeground(idle);
        b.getModel().addChangeListener(e -> b.setForeground(b.getModel().isRollover() || b.isSelected() ? hot : idle));
    }

    static void fadeIn(Window w, int dy) {
        Point end = w.getLocation();
        opacity(w, 0f);
        w.setLocation(end.x, end.y + dy);
        w.setVisible(true);
        animate(180, p -> {
            opacity(w, (float) p);
            w.setLocation(end.x, (int) Math.round(end.y + dy * (1 - p)));
        }, null);
    }

    static void fadeOut(Window w, Runnable done) {
        animate(120, p -> opacity(w, (float) (1 - p)), () -> {
            w.dispose();
            if (done != null) done.run();
        });
    }

    static void round(Window w, int arc) {
        w.setShape(new RoundRectangle2D.Double(0, 0, w.getWidth(), w.getHeight(), arc, arc));
    }

    // a real window, not a lightweight popup, because the GL preview would paint right over a lightweight one
    static void toast(JFrame owner, String text, boolean bad, String actionText, Runnable action) {
        Theme t = t();
        JWindow w = new JWindow(owner);
        JPanel box = new JPanel(new BorderLayout(14, 0));
        box.setBackground(t.raised());
        box.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(bad ? t.error() : t.border()),
                BorderFactory.createEmptyBorder(12, 16, 12, 14)));
        JLabel label = new JLabel(text);
        label.setForeground(bad ? t.error() : t.text());
        box.add(label, BorderLayout.CENTER);
        if (actionText != null) {
            JButton go = new JButton(actionText);
            go.putClientProperty("JButton.buttonType", "borderless");
            go.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            go.setFocusable(false);
            go.setForeground(t.accent());
            go.addActionListener(e -> {
                action.run();
                close(w);
            });
            box.add(go, BorderLayout.EAST);
        }
        w.setContentPane(box);
        w.pack();
        w.setSize(Math.max(300, w.getWidth()), w.getHeight());
        round(w, 10);

        int y = owner.getY() + owner.getHeight() - w.getHeight() - 46;
        for (JWindow other : toasts) y -= other.getHeight() + 10;
        w.setLocation(owner.getX() + owner.getWidth() - w.getWidth() - 22, y);
        toasts.add(w);
        w.setVisible(true);
        Timer life = new Timer(actionText != null ? 7000 : 3500, e -> close(w));
        life.setRepeats(false);
        life.start();
    }

    private static void close(JWindow w) {
        if (toasts.remove(w)) w.dispose();
    }

    // thin bar that sweeps across while something is working
    static final class Loader extends JComponent {
        private final Timer tick = new Timer(15, e -> repaint());
        private long started;

        Loader() {
            setPreferredSize(new Dimension(0, 2));
            setOpaque(false);
        }

        void busy(boolean on) {
            if (on) {
                started = System.nanoTime();
                tick.start();
            } else {
                tick.stop();
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (!tick.isRunning()) return;
            double p = ((System.nanoTime() - started) / 1e9 * 0.8) % 1.0;
            int w = getWidth(), seg = w / 4;
            g.setColor(t().accent());
            g.fillRect((int) (p * (w + seg)) - seg, 0, seg, 2);
        }
    }

    // the hogyoku gem on a rounded tile
    static final class Badge extends JComponent {
        private static final java.awt.image.BufferedImage GEM = loadGem();

        Badge() {
            setPreferredSize(new Dimension(150, 150));
            setMaximumSize(getPreferredSize());
            setOpaque(false);
        }

        private static java.awt.image.BufferedImage loadGem() {
            try {
                return javax.imageio.ImageIO.read(Fx.class.getResource("/icon.png"));
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            int w = getWidth(), h = getHeight();
            float arc = Math.min(28f, Math.min(w, h) * 0.26f);
            g2.setColor(t().dark() ? new Color(0x1a1a1a) : new Color(0x2b2d30));
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, arc, arc));
            if (GEM != null) {
                int size = (int) (Math.min(w, h) * 0.78);
                g2.drawImage(GEM, (w - size) / 2, (h - size) / 2, size, size, null);
            }
            g2.dispose();
        }
    }

    // a rounded panel floating on the frame colour, the building block of the window
    static final class Island extends JPanel {
        private final int arc;

        Island(java.awt.Component content, int pad) {
            this(content, pad, 12);
        }

        Island(java.awt.Component content, int pad, int arc) {
            super(new BorderLayout());
            this.arc = arc;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(pad, pad, pad, pad));
            add(content);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(t().island());
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));
            if (!t().dark() || t().name().equals("High Contrast")) {
                g2.setColor(t().border());
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, arc, arc));
            }
            g2.dispose();
        }
    }

    // wheel scrolling like IntelliJ's: every notch adds to a target and the view eases there over a fixed
    // short time, starting again from wherever it is, so fast scrolling stays smooth and stops right away
    static void smooth(JScrollPane pane, boolean hideBars) {
        if (hideBars) {
            pane.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
            pane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        }
        pane.setWheelScrollingEnabled(false);
        double[] from = new double[2], target = new double[2];
        long[] started = {0};
        Timer glide = new Timer(5, null);
        glide.addActionListener(e -> {
            JViewport vp = pane.getViewport();
            if (vp.getView() == null) {
                glide.stop();
                return;
            }
            double p = Math.min(1, (System.nanoTime() - started[0]) / 1e6 / 150.0);
            double ease = 1 - Math.pow(1 - p, 3);
            int x = (int) Math.round(from[0] + (target[0] - from[0]) * ease);
            int y = (int) Math.round(from[1] + (target[1] - from[1]) * ease);
            vp.setViewPosition(new Point(x, y));
            if (p >= 1) glide.stop();
        });
        pane.addMouseWheelListener(e -> {
            JViewport vp = pane.getViewport();
            if (vp.getView() == null) return;
            Point at = vp.getViewPosition();
            if (!glide.isRunning()) {
                target[0] = at.x;
                target[1] = at.y;
            }
            from[0] = at.x;
            from[1] = at.y;
            int axis = e.isShiftDown() ? 0 : 1;
            int max = axis == 0 ? vp.getView().getWidth() - vp.getWidth() : vp.getView().getHeight() - vp.getHeight();
            target[axis] = Math.max(0, Math.min(Math.max(0, max), target[axis] + e.getPreciseWheelRotation() * 90));
            started[0] = System.nanoTime();
            glide.start();
            e.consume();
        });
    }

    // shortens text with an ellipsis so it never runs past the space it has
    static String fit(Graphics2D g, String text, int width) {
        java.awt.FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(text) <= width) return text;
        String dots = "…";
        int end = text.length();
        while (end > 0 && fm.stringWidth(text.substring(0, end) + dots) > width) end--;
        return text.substring(0, end).stripTrailing() + dots;
    }

    // a button that's filled with the accent when it's the main one, outlined otherwise
    static final class Soft extends JButton {
        Soft(String text, boolean bold) {
            super(text);
            setFocusable(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            putClientProperty("FlatLaf.style", "margin: 5,16,5,16");
            if (bold) {
                setBackground(t().accent());
                setForeground(t().onAccent());
            }
        }
    }

    static String hex(Color c) {
        return String.format("#%06x", c.getRGB() & 0xffffff);
    }

    static Font heading(float size) {
        return new Font("Segoe UI Semibold", Font.PLAIN, 12).deriveFont(size);
    }

    // fades and lifts its contents in when a page changes
    static class Reveal extends JPanel {
        private float alpha = 1f, lift;
        private Timer running;

        Reveal() {
            setOpaque(true);
        }

        void play() {
            play(260, 10);
        }

        void play(int ms, float dy) {
            if (running != null) running.stop();
            running = animate(ms, p -> {
                alpha = (float) p;
                lift = (float) (dy * (1 - p));
                repaint();
            }, null);
        }

        @Override
        public void paint(Graphics g) {
            if (alpha >= 1f) {
                super.paint(g);
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(getBackground());
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setComposite(java.awt.AlphaComposite.SrcOver.derive(alpha));
            g2.translate(0, Math.round(lift));
            super.paint(g2);
            g2.dispose();
        }
    }
}
