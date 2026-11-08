package dev.mirothe.vessel;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

// the parts both the home screen and the workspace share
final class Chrome {
    static final String ICONS = Stream.of("Segoe Fluent Icons", "Segoe MDL2 Assets")
            .filter(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())::contains)
            .findFirst().orElse(null);
    private Chrome() {
    }

    static String pretty(String id) {
        String s = id.replace('_', ' ').strip();
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        return g2;
    }

    // fills the shape with the picture, cropping whatever doesn't fit
    static void cover(Graphics2D g, Image img, Shape shape) {
        java.awt.Rectangle b = shape.getBounds();
        double k = Math.max(b.width / (double) img.getWidth(null), b.height / (double) img.getHeight(null));
        int w = (int) Math.ceil(img.getWidth(null) * k), h = (int) Math.ceil(img.getHeight(null) * k);
        Shape old = g.getClip();
        g.clip(shape);
        g.drawImage(img, b.x + (b.width - w) / 2, b.y + (b.height - h) / 2, w, h, null);
        g.setClip(old);
    }

    static JButton action(String text, int height, Runnable action) {
        String label = ICONS != null ? "<html><font face='" + ICONS + "'>&#xE768;</font>&nbsp;&nbsp;" + text + "</html>" : text;
        JButton b = new Fx.Soft(label, true);
        b.setFont(b.getFont().deriveFont(Font.BOLD, 16f));
        b.setPreferredSize(new Dimension(b.getPreferredSize().width + 40, height));
        b.addActionListener(e -> action.run());
        return b;
    }

    // the icon rail down the left of the window
    static final class Rail extends JComponent {
        record Item(String key, String glyph, String label, Runnable action) {
        }

        private static final int ROW = 72, TOP = 74;
        private final List<Item> items;
        private final Item bottom;
        private final Image logo;
        private String selected = "";
        private Item from;
        private Item hover;
        private final Fx.Glide slide = new Fx.Glide(this, 1);
        private final java.util.Map<Item, Fx.Glide> hovers = new java.util.HashMap<>();

        Rail(List<Item> items, Item bottom) {
            this.items = items;
            this.bottom = bottom;
            java.net.URL icon = Rail.class.getResource("/icon.png");
            logo = icon == null ? null : new javax.swing.ImageIcon(icon).getImage();
            setPreferredSize(new Dimension(84, 0));
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    hover = at(e.getY());
                    for (Item i : all()) glow(i).to(i == hover ? 1 : 0);
                    setCursor(Cursor.getPredefinedCursor(hover == null ? Cursor.DEFAULT_CURSOR : Cursor.HAND_CURSOR));
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = null;
                    for (Item i : all()) glow(i).to(0);
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    Item i = at(e.getY());
                    if (i != null) i.action().run();
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        void select(String key) {
            if (key.equals(selected)) return;
            from = find(selected);
            selected = key;
            slide.value0();
            slide.to(1);
            repaint();
        }

        private List<Item> all() {
            return Stream.concat(items.stream(), Stream.of(bottom)).toList();
        }

        private Item find(String key) {
            for (Item i : all()) if (i.key().equals(key)) return i;
            return null;
        }

        private Fx.Glide glow(Item i) {
            return hovers.computeIfAbsent(i, k -> new Fx.Glide(this, 0));
        }

        private int top(Item i) {
            return i == bottom ? getHeight() - ROW - 10 : TOP + items.indexOf(i) * (ROW + 6);
        }

        private Item at(int y) {
            for (Item i : items) if (y >= top(i) && y < top(i) + ROW) return i;
            return y >= top(bottom) && y < top(bottom) + ROW ? bottom : null;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            Theme t = Fx.t();
            g2.setColor(t.frame());
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(t.border());
            g2.fillRect(getWidth() - 1, 0, 1, getHeight());
            if (logo != null) g2.drawImage(logo, (getWidth() - 38) / 2, 18, 38, 38, null);
            int w = getWidth();
            for (Item i : all()) {
                double a = glow(i).get();
                if (a > 0 && !i.key().equals(selected)) {
                    g2.setColor(Fx.mix(t.frame(), t.hover(), a));
                    g2.fill(new RoundRectangle2D.Float(8, top(i), w - 17, ROW - 4, 12, 12));
                }
            }
            Item on = find(selected);
            if (on != null) {
                // the pill travels from the last section to this one
                double p = slide.get();
                float y = (float) (from == null ? top(on) : top(from) + (top(on) - top(from)) * p);
                g2.setColor(t.select());
                g2.fill(new RoundRectangle2D.Float(8, y, w - 17, ROW - 4, 12, 12));
                g2.setColor(t.strong());
                g2.fill(new RoundRectangle2D.Float(0, y + 18, 3, ROW - 40, 3, 3));
            }
            for (Item i : all()) paintItem(g2, t, i);
            g2.dispose();
        }

        private void paintItem(Graphics2D g2, Theme t, Item i) {
            boolean on = i.key().equals(selected);
            int y = top(i), w = getWidth();
            g2.setColor(on ? Fx.mix(t.dim(), t.strong(), slide.get()) : Fx.mix(t.dim(), t.text(), glow(i).get()));
            if (ICONS != null) {
                g2.setFont(new Font(ICONS, Font.PLAIN, 21));
                g2.drawString(i.glyph(), (w - 1 - g2.getFontMetrics().stringWidth(i.glyph())) / 2f, y + 34);
            }
            g2.setFont(getFont() == null ? new Font("Segoe UI", Font.PLAIN, 12) : getFont().deriveFont(12f));
            g2.drawString(i.label(), (w - 1 - g2.getFontMetrics().stringWidth(i.label())) / 2f, y + 56);
        }
    }

    // the row of effects above the editor. it stands in for tabs, so it also lists any other file that's open
    static final class EffectBar extends JPanel {
        record Entry(Path file, String label, Image thumb, boolean dirty, boolean closable) {
        }

        private final Consumer<Path> open;
        private Consumer<Path> close = p -> { };
        private final BiConsumer<Path, MouseEvent> menu;
        private final Runnable add;

        EffectBar(Consumer<Path> open, BiConsumer<Path, MouseEvent> menu, Runnable add) {
            super(new FlowLayout(FlowLayout.LEFT, 6, 0));
            this.open = open;
            this.menu = menu;
            this.add = add;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 8));
        }

        void onClose(Consumer<Path> close) {
            this.close = close;
        }

        void set(List<Entry> entries, Path active) {
            removeAll();
            for (Entry e : entries) add(new Chip(e, e.file().equals(active)));
            if (add != null) add(new Chip(null, false));
            revalidate();
            repaint();
        }

        private final class Chip extends JComponent {
            private final Entry entry;
            private final boolean on;
            private boolean hot;
            private final Fx.Glide glow = new Fx.Glide(this, 0);

            Chip(Entry entry, boolean on) {
                this.entry = entry;
                this.on = on;
                int w = entry == null ? 40 : getFontMetrics(chipFont()).stringWidth(text()) + (entry.closable() ? 76 : 54);
                setPreferredSize(new Dimension(w, 40));
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setToolTipText(entry == null ? "New effect" : entry.file().toString());
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hot = true;
                        glow.to(1);
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hot = false;
                        glow.to(0);
                    }

                    @Override
                    public void mousePressed(MouseEvent e) {
                        popup(e);
                        if (!e.isPopupTrigger() && SwingUtilities.isLeftMouseButton(e)) {
                            if (entry == null) add.run();
                            else if (entry.closable() && e.getX() > getWidth() - 28) close.accept(entry.file());
                            else open.accept(entry.file());
                        } else if (entry != null && entry.closable() && SwingUtilities.isMiddleMouseButton(e)) {
                            close.accept(entry.file());
                        }
                    }

                    @Override
                    public void mouseReleased(MouseEvent e) {
                        popup(e);
                    }
                });
            }

            private void popup(MouseEvent e) {
                if (e.isPopupTrigger() && entry != null) menu.accept(entry.file(), e);
            }

            private Font chipFont() {
                return new Font("Segoe UI Semibold", Font.PLAIN, 14);
            }

            private String text() {
                return entry.label() + (entry.dirty() ? "  ●" : "");
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = smooth(g);
                Theme t = Fx.t();
                int h = getHeight();
                if (on || glow.get() > 0) {
                    g2.setColor(on ? t.select() : Fx.mix(t.frame(), t.hover(), glow.get()));
                    g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), h, 10, 10));
                }
                if (entry == null) {
                    g2.setColor(t.dim());
                    int c = getWidth() / 2, m = h / 2;
                    g2.fillRect(c - 7, m, 14, 1);
                    g2.fillRect(c, m - 7, 1, 14);
                } else {
                    RoundRectangle2D.Float square = new RoundRectangle2D.Float(8, (h - 24) / 2f, 24, 24, 7, 7);
                    if (entry.thumb() != null) {
                        cover(g2, entry.thumb(), square);
                    } else {
                        g2.setColor(t.raised());
                        g2.fill(square);
                    }
                    g2.setFont(chipFont());
                    g2.setColor(on ? t.strong() : Fx.mix(t.dim(), t.text(), glow.get()));
                    g2.drawString(text(), 42, h / 2f + 5);
                    if (entry.closable() && (glow.get() > 0.05 || on)) {
                        if (!on) g2.setColor(Fx.mix(t.frame(), t.text(), glow.get()));
                        int x = getWidth() - 20, m = h / 2;
                        g2.setStroke(new java.awt.BasicStroke(1.4f));
                        g2.drawLine(x - 4, m - 4, x + 4, m + 4);
                        g2.drawLine(x - 4, m + 4, x + 4, m - 4);
                    }
                }
                g2.dispose();
            }
        }
    }

    // white when there's something to save, a quiet outline once everything is
    static final class SaveButton extends JComponent {
        private final Runnable action;
        private int unsaved;
        private final Fx.Glide fill = new Fx.Glide(this, 0), glow = new Fx.Glide(this, 0);

        SaveButton(Runnable action) {
            this.action = action;
            setPreferredSize(new Dimension(104, 40));
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
                    if (unsaved > 0 && SwingUtilities.isLeftMouseButton(e)) action.run();
                }
            });
        }

        void unsaved(int count) {
            unsaved = count;
            fill.to(count > 0 ? 1 : 0);
            setCursor(Cursor.getPredefinedCursor(count > 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            setToolTipText(count > 1 ? "Save every changed file (Ctrl+Shift+S)" : count == 1 ? "Save (Ctrl+S)" : "Everything is saved");
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            Theme t = Fx.t();
            int w = getWidth(), h = getHeight();
            RoundRectangle2D.Float shape = new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 10, 10);
            String label = unsaved > 1 ? "Save all" : unsaved == 1 ? "Save" : "Saved";
            double f = fill.get();
            java.awt.Color solid = Fx.mix(t.accent(), mix(t.accent(), t.frame()), unsaved > 0 ? glow.get() : 0);
            g2.setColor(Fx.mix(t.frame(), solid, f));
            g2.fill(shape);
            g2.setColor(Fx.mix(t.border(), solid, f));
            g2.draw(shape);
            g2.setColor(Fx.mix(t.dim(), t.onAccent(), f));
            g2.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 14));
            g2.drawString(label, (w - g2.getFontMetrics().stringWidth(label)) / 2f, h / 2f + 5);
            g2.dispose();
        }

        private static java.awt.Color mix(java.awt.Color a, java.awt.Color b) {
            return new java.awt.Color((a.getRed() * 7 + b.getRed()) / 8, (a.getGreen() * 7 + b.getGreen()) / 8, (a.getBlue() * 7 + b.getBlue()) / 8);
        }
    }

    // a tabbed pane with no tabs, the effect bar does that job
    static final class Bare extends JTabbedPane {
        @Override
        public void updateUI() {
            setUI(new BasicTabbedPaneUI() {
                @Override
                protected int calculateTabAreaHeight(int placement, int runs, int height) {
                    return 0;
                }

                @Override
                protected Insets getContentBorderInsets(int placement) {
                    return new Insets(0, 0, 0, 0);
                }

                @Override
                protected void paintTabArea(Graphics g, int placement, int selected) {
                }

                @Override
                protected void paintContentBorder(Graphics g, int placement, int selected) {
                }
            });
        }
    }
}
