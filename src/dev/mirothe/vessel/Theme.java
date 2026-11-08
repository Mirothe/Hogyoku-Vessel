package dev.mirothe.vessel;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.BorderFactory;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

// a colour scheme for the whole app. frame is behind the islands, island is the panels themselves,
// the rest are roles every screen picks from instead of hard coding its own greys
record Theme(String name, boolean dark,
             Color frame, Color island, Color editor, Color border, Color hover, Color select, Color raised,
             Color accent, Color onAccent, Color strong, Color text, Color dim, Color faint, Color error, Color good,
             Color keyword, Color type, Color number, Color string, Color comment, Color call, Color builtin, Color preproc,
             Color currentLine, Color textSelect, Color gutter) {

    static final List<Theme> ALL = List.of(
            new Theme("Vessel Black", true,
                    rgb(0x000000), rgb(0x0e0e0e), rgb(0x0e0e0e), rgb(0x1e1e1e), rgb(0x1c1c1c), rgb(0x2a2a2a), rgb(0x161616),
                    rgb(0xffffff), rgb(0x000000), rgb(0xffffff), rgb(0xd8d8d8), rgb(0x777777), rgb(0x4a4a4a), rgb(0xff5c5c), rgb(0x9ae6a0),
                    rgb(0xffffff), rgb(0x9e9e9e), rgb(0xe8e8e8), rgb(0xc8c8c8), rgb(0x555555), rgb(0xffffff), rgb(0xd0d0d0), rgb(0x8f8f8f),
                    rgb(0x181818), rgb(0x333333), rgb(0x4a4a4a)),
            new Theme("Islands Dark", true,
                    rgb(0x131314), rgb(0x1e1f22), rgb(0x1e1f22), rgb(0x2b2d30), rgb(0x2b2d30), rgb(0x2e436e), rgb(0x26282b),
                    rgb(0x3574f0), rgb(0xffffff), rgb(0xffffff), rgb(0xdfe1e5), rgb(0x8c8f95), rgb(0x5a5d63), rgb(0xf75464), rgb(0x5fb865),
                    rgb(0xcf8e6d), rgb(0xcf8e6d), rgb(0x2aacb8), rgb(0x6aab73), rgb(0x7a7e85), rgb(0x56a8f5), rgb(0xc77dbb), rgb(0xb3ae60),
                    rgb(0x26282e), rgb(0x214283), rgb(0x4b5059)),
            new Theme("Darcula", true,
                    rgb(0x2b2b2b), rgb(0x3c3f41), rgb(0x2b2b2b), rgb(0x323232), rgb(0x4b4f52), rgb(0x0d293e), rgb(0x45494a),
                    rgb(0x4a88c7), rgb(0xffffff), rgb(0xffffff), rgb(0xbbbbbb), rgb(0x8c8c8c), rgb(0x606366), rgb(0xff6b68), rgb(0x629755),
                    rgb(0xcc7832), rgb(0xcc7832), rgb(0x6897bb), rgb(0x6a8759), rgb(0x808080), rgb(0xffc66d), rgb(0x9876aa), rgb(0xbbb529),
                    rgb(0x323232), rgb(0x214283), rgb(0x606366)),
            new Theme("Islands Light", false,
                    rgb(0xe9ebef), rgb(0xf7f8fa), rgb(0xffffff), rgb(0xdfe1e5), rgb(0xe8eaee), rgb(0xd4e2ff), rgb(0xffffff),
                    rgb(0x3574f0), rgb(0xffffff), rgb(0x000000), rgb(0x1e1f22), rgb(0x6c707e), rgb(0xa8adbd), rgb(0xdb3b4b), rgb(0x208a3c),
                    rgb(0x0033b3), rgb(0x0033b3), rgb(0x1750eb), rgb(0x067d17), rgb(0x8c8c8c), rgb(0x00627a), rgb(0x871094), rgb(0x9e880d),
                    rgb(0xf5f8fe), rgb(0xa6d2ff), rgb(0xadb1b8)),
            new Theme("High Contrast", true,
                    rgb(0x000000), rgb(0x000000), rgb(0x000000), rgb(0xffffff), rgb(0x1a1a1a), rgb(0x0a4bbf), rgb(0x0e0e0e),
                    rgb(0x1aebff), rgb(0x000000), rgb(0xffffff), rgb(0xffffff), rgb(0xd0d0d0), rgb(0x9a9a9a), rgb(0xff4040), rgb(0x40ff40),
                    rgb(0x00ccff), rgb(0x00ccff), rgb(0xffff00), rgb(0x00ff00), rgb(0xb0b0b0), rgb(0xff8000), rgb(0xff66ff), rgb(0xffffff),
                    rgb(0x1a1a1a), rgb(0x0a4bbf), rgb(0x9a9a9a)));

    private static final Preferences PREFS = Preferences.userNodeForPackage(Theme.class);
    static Theme current = ALL.get(0);

    // a stand in whose colours look up the current theme every time they're used, so whatever holds on to
    // one of them follows a theme switch on the next repaint without being rebuilt
    static final Theme LIVE = new Theme("live", true,
            live(Theme::frame),
            live(Theme::island),
            live(Theme::editor),
            live(Theme::border),
            live(Theme::hover),
            live(Theme::select),
            live(Theme::raised),
            live(Theme::accent),
            live(Theme::onAccent),
            live(Theme::strong),
            live(Theme::text),
            live(Theme::dim),
            live(Theme::faint),
            live(Theme::error),
            live(Theme::good),
            live(Theme::keyword),
            live(Theme::type),
            live(Theme::number),
            live(Theme::string),
            live(Theme::comment),
            live(Theme::call),
            live(Theme::builtin),
            live(Theme::preproc),
            live(Theme::currentLine),
            live(Theme::textSelect),
            live(Theme::gutter));

    @Override
    public String name() {
        return this == LIVE ? current.name : name;
    }

    @Override
    public boolean dark() {
        return this == LIVE ? current.dark : dark;
    }

    private static Color live(java.util.function.Function<Theme, Color> role) {
        return new Live(role);
    }

    private static final class Live extends Color {
        private final java.util.function.Function<Theme, Color> role;

        Live(java.util.function.Function<Theme, Color> role) {
            super(0, true);
            this.role = role;
        }

        private Color now() {
            return role.apply(current);
        }

        @Override
        public int getRGB() {
            return now().getRGB();
        }

        @Override
        public float[] getRGBComponents(float[] a) {
            return now().getRGBComponents(a);
        }

        @Override
        public float[] getRGBColorComponents(float[] a) {
            return now().getRGBColorComponents(a);
        }

        @Override
        public float[] getComponents(float[] a) {
            return now().getComponents(a);
        }

        @Override
        public float[] getColorComponents(float[] a) {
            return now().getColorComponents(a);
        }

        @Override
        public int getTransparency() {
            return now().getTransparency();
        }

        @Override
        public java.awt.PaintContext createContext(java.awt.image.ColorModel cm, java.awt.Rectangle r, java.awt.geom.Rectangle2D r2d,
                                                   java.awt.geom.AffineTransform xform, java.awt.RenderingHints hints) {
            return now().createContext(cm, r, r2d, xform, hints);
        }

        @Override
        public Color brighter() {
            return now().brighter();
        }

        @Override
        public Color darker() {
            return now().darker();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Color c && c.getRGB() == getRGB();
        }

        @Override
        public int hashCode() {
            return getRGB();
        }
    }

    private static Color rgb(int v) {
        return new Color(v);
    }

    static void load() {
        String saved = PREFS.get("theme.v2", ALL.get(0).name);
        current = ALL.stream().filter(t -> t.name.equals(saved)).findFirst().orElse(ALL.get(0));
    }

    static void save(Theme t) {
        PREFS.put("theme.v2", t.name);
        current = t;
    }

    // colours have to go in as UIResource, or FlatLaf treats them as set by hand and keeps them across a theme switch
    private static void put(String key, Object value) {
        UIManager.put(key, value instanceof Color c && !(value instanceof javax.swing.plaf.UIResource)
                ? new javax.swing.plaf.ColorUIResource(c) : value);
    }

    private static String hex(Color c) {
        return String.format("#%06x", c.getRGB() & 0xffffff);
    }

    // sets up FlatLaf for the current theme. call before any window is built
    static void apply() {
        Theme t = current;
        FlatLaf.setGlobalExtraDefaults(Map.of(
                "@background", hex(t.island),
                "@foreground", hex(t.text),
                "@accentColor", hex(t.accent),
                "@selectionBackground", hex(t.select),
                "@selectionForeground", hex(t.strong),
                "@selectionInactiveBackground", hex(t.hover),
                "@selectionInactiveForeground", hex(t.text),
                "@textSelectionBackground", hex(t.textSelect),
                "@textSelectionForeground", hex(t.strong)));
        if (t.dark) FlatDarkLaf.setup();
        else FlatLightLaf.setup();

        put("TitlePane.unifiedBackground", true);
        put("TitlePane.background", t.frame);
        put("TitlePane.inactiveBackground", t.frame);
        put("TitlePane.foreground", t.text);
        put("MenuBar.background", t.frame);
        put("MenuBar.hoverBackground", t.hover);
        put("MenuBar.selectionBackground", t.hover);
        put("PopupMenu.background", t.raised);
        put("PopupMenu.borderColor", t.border);
        put("Popup.borderCornerRadius", 8);
        put("PopupMenu.borderCornerRadius", 8);
        put("MenuItem.selectionArc", 6);
        put("MenuItem.selectionInsets", new Insets(0, 4, 0, 4));
        put("ToolTip.borderCornerRadius", 6);
        put("ToolTip.background", t.raised);
        put("ToolTip.foreground", t.text);
        put("ToolTip.border", BorderFactory.createLineBorder(t.border));
        put("Component.arc", 8);
        put("Button.arc", 8);
        put("TextComponent.arc", 8);
        put("ComboBox.borderCornerRadius", 8);
        put("Component.focusWidth", 1);
        put("Component.innerFocusWidth", 0);
        put("Component.focusColor", t.accent);
        put("Component.focusedBorderColor", t.accent);
        put("Component.borderColor", t.border);
        put("Button.background", t.raised);
        put("Button.borderColor", t.border);
        put("Button.hoverBackground", t.hover);
        put("Button.default.background", t.accent);
        put("Button.default.foreground", t.onAccent);
        put("Button.default.hoverBackground", Fx.mix(t.accent, t.strong, 0.12));
        put("Button.default.pressedBackground", Fx.mix(t.accent, t.frame, 0.2));
        put("Button.default.focusedBackground", t.accent);
        put("Button.default.borderColor", t.accent);
        put("Button.toolbar.hoverBackground", t.hover);
        put("Button.toolbar.pressedBackground", t.select);
        put("Button.toolbar.selectedBackground", t.hover);
        put("CheckBox.icon.checkmarkColor", t.onAccent);
        put("CheckBox.icon.selectedBackground", t.accent);
        put("CheckBox.icon.selectedBorderColor", t.accent);
        put("TextField.background", t.editor);
        put("TextField.placeholderForeground", t.faint);
        put("TabbedPane.tabHeight", 34);
        put("TabbedPane.tabAreaInsets", new Insets(0, 4, 0, 4));
        put("TabbedPane.background", t.island);
        put("TabbedPane.foreground", t.dim);
        put("TabbedPane.selectedForeground", t.strong);
        put("TabbedPane.selectedBackground", t.island);
        put("TabbedPane.hoverColor", t.hover);
        put("TabbedPane.focusColor", t.island);
        put("TabbedPane.underlineColor", t.accent);
        put("TabbedPane.inactiveUnderlineColor", t.faint);
        put("TabbedPane.tabSelectionHeight", 2);
        put("TabbedPane.contentSeparatorHeight", 1);
        put("TabbedPane.contentAreaColor", t.border);
        put("TabbedPane.showTabSeparators", false);
        put("TabbedPane.tabInsets", new Insets(4, 12, 4, 12));
        put("TabbedPane.closeForeground", t.dim);
        put("TabbedPane.closeHoverForeground", t.strong);
        put("TabbedPane.closeHoverBackground", t.hover);
        put("TabbedPane.closeArc", 6);
        put("SplitPane.dividerSize", 6);
        put("SplitPaneDivider.gripDotCount", 0);
        put("SplitPane.background", t.frame);
        put("SplitPaneDivider.draggingColor", t.border);
        put("Tree.rowHeight", 24);
        put("Tree.background", t.island);
        put("Tree.selectionBackground", t.select);
        put("Tree.selectionInactiveBackground", t.hover);
        put("Tree.selectionArc", 6);
        put("Tree.selectionInsets", new Insets(0, 6, 0, 6));
        put("List.background", t.island);
        put("List.selectionArc", 6);
        put("List.selectionInsets", new Insets(0, 6, 0, 6));
        put("ScrollPane.background", t.island);
        put("ScrollBar.width", 10);
        put("ScrollBar.thumbArc", 999);
        put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        put("ScrollBar.track", new Color(0, 0, 0, 0));
        put("ScrollBar.thumb", Fx.mix(t.island, t.dim, 0.35));
        put("ScrollBar.hoverThumbColor", Fx.mix(t.island, t.dim, 0.55));
        put("ScrollBar.pressedThumbColor", Fx.mix(t.island, t.dim, 0.75));
        put("Separator.foreground", t.border);
        put("defaultFont", new Font("Segoe UI", Font.PLAIN, 13));
    }
}
