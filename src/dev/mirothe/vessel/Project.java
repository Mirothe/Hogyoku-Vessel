package dev.mirothe.vessel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Project {
    record Effect(String id, String file, int alpha, String item, Map<String, String> defines) {
    }

    final Path dir;
    String name = "My Skies";
    String description = "Custom skies";
    String author = "Mirothe";
    String namespace = "skyboxes";
    long seed = 4211;
    String discordImage = "";
    String discordImageFor = "";
    final List<Effect> effects = new ArrayList<>();

    private Project(Path dir) {
        this.dir = dir;
    }

    Path file() {
        return dir.resolve("project.json");
    }

    static Project load(Path dir) throws IOException {
        Project p = new Project(dir);
        @SuppressWarnings("unchecked")
        Map<String, Object> j = (Map<String, Object>) Json.parse(Files.readString(p.file(), StandardCharsets.UTF_8));
        p.name = str(j, "name", p.name);
        p.description = str(j, "description", p.description);
        p.author = str(j, "author", p.author);
        p.namespace = str(j, "namespace", p.namespace);
        if (j.get("seed") instanceof Number n) p.seed = n.longValue();
        p.discordImage = str(j, "discordImage", "");
        p.discordImageFor = str(j, "discordImageFor", "");
        if (j.get("effects") instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> e)) continue;
                Map<String, String> defs = new LinkedHashMap<>();
                if (e.get("defines") instanceof Map<?, ?> d) d.forEach((k, v) -> defs.put(k.toString(), String.valueOf(v)));
                Object alpha = e.get("alpha");
                if (!(alpha instanceof Number)) throw new IllegalArgumentException("effect " + e.get("id") + " is missing \"alpha\"");
                p.effects.add(new Effect(String.valueOf(e.get("id")), String.valueOf(e.get("file")), ((Number) alpha).intValue(),
                        String.valueOf(e.get("item")), defs));
            }
        }
        return p;
    }

    static Project create(Path dir, String name) throws IOException {
        Files.createDirectories(dir.resolve("effects"));
        Project p = new Project(dir);
        p.name = name;
        p.seed = System.nanoTime() & 0xffffff;
        Files.writeString(dir.resolve("effects/my_sky.glsl"), Templates.NEW_EFFECT, StandardCharsets.UTF_8);
        p.effects.add(new Effect("my_sky", "effects/my_sky.glsl", 254, "sky_1", new LinkedHashMap<>()));
        p.save();
        return p;
    }

    void save() throws IOException {
        Map<String, Object> j = new LinkedHashMap<>();
        j.put("name", name);
        j.put("description", description);
        j.put("author", author);
        j.put("namespace", namespace);
        j.put("seed", seed);
        if (!discordImage.isBlank()) j.put("discordImage", discordImage);
        if (!discordImageFor.isBlank()) j.put("discordImageFor", discordImageFor);
        List<Object> list = new ArrayList<>();
        for (Effect e : effects) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.id);
            m.put("file", e.file);
            m.put("alpha", e.alpha);
            m.put("item", e.item);
            if (!e.defines.isEmpty()) m.put("defines", new LinkedHashMap<>(e.defines));
            list.add(m);
        }
        j.put("effects", list);
        Files.writeString(file(), Json.write(j) + "\n", StandardCharsets.UTF_8);
    }

    int freeAlpha() {
        for (int a = 254; a > 200; a--) {
            final int c = a;
            if (effects.stream().noneMatch(e -> e.alpha == c)) return a;
        }
        return 200;
    }

    Effect effectFor(Path file) {
        for (Effect e : effects) if (dir.resolve(e.file).normalize().equals(file.normalize())) return e;
        return null;
    }

    // the project's icon is its pack.png, the same picture players see in the resource pack list
    static Path icon(Path dir) {
        return dir.resolve("pack.png");
    }

    private static final Map<String, Object[]> ICONS = new java.util.HashMap<>();

    // the icon scaled down, cached until the file changes
    static java.awt.Image icon(Path dir, int size) {
        Path file = icon(dir);
        try {
            if (!Files.exists(file)) return null;
            long stamp = Files.getLastModifiedTime(file).toMillis();
            String key = file + "@" + size;
            Object[] hit = ICONS.get(key);
            if (hit != null && (long) hit[0] == stamp) return (java.awt.Image) hit[1];
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(file.toFile());
            if (img == null) return null;
            java.awt.Image scaled = img.getScaledInstance(size, size, java.awt.Image.SCALE_SMOOTH);
            ICONS.put(key, new Object[]{stamp, scaled});
            return scaled;
        } catch (IOException e) {
            return null;
        }
    }

    // what the preview last showed for an effect, the home screen and the effect bar use these as thumbnails
    static Path shot(Path dir, String id) {
        return dir.resolve(".vessel/shots").resolve(id + ".png");
    }

    private static final Map<Path, Object[]> PICTURES = new java.util.HashMap<>();

    static java.awt.image.BufferedImage picture(Path file) {
        try {
            if (!Files.exists(file)) return null;
            long stamp = Files.getLastModifiedTime(file).toMillis();
            Object[] hit = PICTURES.get(file);
            if (hit != null && (long) hit[0] == stamp) return (java.awt.image.BufferedImage) hit[1];
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(file.toFile());
            PICTURES.put(file, new Object[]{stamp, img});
            return img;
        } catch (IOException e) {
            return null;
        }
    }

    // crops the picture to a square from the middle and saves it as the icon
    static void setIcon(Path dir, java.io.File picture) throws IOException {
        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(picture);
        if (img == null) throw new IOException(picture.getName() + " isn't a picture Java can read");
        int side = Math.min(img.getWidth(), img.getHeight());
        java.awt.image.BufferedImage square = img.getSubimage((img.getWidth() - side) / 2, (img.getHeight() - side) / 2, side, side);
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(128, 128, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = out.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(square, 0, 0, 128, 128, null);
        g.dispose();
        javax.imageio.ImageIO.write(out, "png", icon(dir).toFile());
    }

    private static String str(Map<String, Object> j, String k, String fallback) {
        Object v = j.get(k);
        return v == null ? fallback : v.toString();
    }
}
