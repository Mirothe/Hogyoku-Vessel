package dev.mirothe.vessel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class Forge {
    static final String TOOL = "Hogyoku Vessel";
    static final String VERSION = "1.0.0";

    record Tier(String name, int min, int max, String folder, List<String> files) {
    }

    // 1.21 and 1.21.1 read the shader name without the core/ prefix, so they get their own json
    static final List<Tier> TIERS = List.of(
            new Tier("1_21", 34, 41, "overlay_1_21", List.of("rendertype_item_entity_translucent_cull.json")),
            new Tier("legacy", 34, 62, "", List.of("rendertype_item_entity_translucent_cull.vsh", "rendertype_item_entity_translucent_cull.fsh", "rendertype_item_entity_translucent_cull.json")),
            new Tier("ubo", 63, 83, "overlay_1_21_6", List.of("rendertype_item_entity_translucent_cull.vsh", "rendertype_item_entity_translucent_cull.fsh")),
            new Tier("26_1", 84, 87, "overlay_26_1", List.of("item.vsh", "item.fsh")),
            new Tier("26_2", 88, 96, "overlay_26_2", List.of("item.vsh", "item.fsh")),
            new Tier("26_3", 97, 99, "overlay_26_3", List.of("item.vsh", "item.fsh")));

    private static final Pattern INCLUDE = Pattern.compile("#(?:moj_import|include)\\s*<([^>]+)>");

    private final Project project;
    private final Consumer<String> log;
    private final java.util.Set<String> objects = new java.util.HashSet<>();
    private final java.util.Set<String> materials = new java.util.HashSet<>();
    private final java.util.Set<String> overlays = new java.util.HashSet<>();
    private final Map<String, float[]> shapes = new java.util.HashMap<>();

    // works out where the item's cube is from how the view position and texture position change across the screen,
    // then gives the object effects a ray that starts on the cube's surface in -1..1 box space
    private static final String OBJECT_FRAME = "vec3 dpx=dFdx(viewPos),dpy=dFdy(viewPos);vec2 dux=dFdx(atlasPx)/15.0,duy=dFdy(atlasPx)/15.0;"
            + "float det=dux.x*duy.y-duy.x*dux.y;if(abs(det)<1e-12)det=1e-12;"
            + "vec3 tu=(dpx*duy.y-dpy*dux.y)/det,tv=(dpy*dux.x-dpx*duy.x)/det;"
            + "vec2 face=(floor(texel.rg*255.0/16.0)+fract(atlasPx)-0.5)/15.0;"
            + "vec3 inward=normalize(cross(tu,tv));if(dot(inward,viewPos)<0.0)inward=-inward;float halfSize=max(length(tu)*0.5,1e-6);"
            + "vec3 boxCenter=viewPos-tu*face.x-tv*face.y+(tu+tv)*0.5+inward*halfSize;vec3 boxOrigin=(viewPos-boxCenter)/halfSize;";

    // shortcut: uv assumes the item's sprite is 16px and sits on a 16px boundary in the atlas, bigger textures would tile
    private static final String MATERIAL_FRAME = "vec3 flatNormal=normalize(cross(dFdx(viewPos),dFdy(viewPos)));if(dot(flatNormal,viewPos)>0.0)flatNormal=-flatNormal;";

    // the Skript spawns the sky cube turned 40 degrees around its corner to corner diagonal and rotates it straight over
    // a few seconds, the game smooths that out for the player. that turn tilts every wall by the same amount, so the
    // wall's direction gives the turn back. unlike the wall distance it doesn't change when the player runs ahead of the cube
    private static final String INTRO_FRAME = "vec3 inrm=abs(normalize(cross(dFdx(viewPos),dFdy(viewPos))));float imax=max(inrm.x,max(inrm.y,inrm.z));"
            + "float iturn=acos(clamp((3.0*imax-1.0)*0.5,-1.0,1.0));float introNow=clamp(1.0-(iturn-0.035)/0.6631317,0.0,1.0);";

    Forge(Project project, Consumer<String> log) {
        this.project = project;
        this.log = log;
    }

    Path writeSkript(Path dir) throws IOException {
        Path file = Skript.write(project, objects, materials, overlays, dir);
        log.accept("wrote " + file);
        return file;
    }

    Path build(boolean obfuscate, boolean check) throws IOException {
        // a fresh seed every build, so names and the number pool never line up between two packs
        Obfuscator ob = new Obfuscator(project.seed ^ System.nanoTime());
        StringBuilder body = new StringBuilder();
        StringBuilder dispatch = new StringBuilder();
        boolean skies = false;
        for (Project.Effect e : project.effects) {
            String raw = Files.readString(project.dir.resolve(e.file()), StandardCharsets.UTF_8);
            float[] box = Obfuscator.shapeSize(raw);
            float scale = Obfuscator.scale(raw);
            shapes.put(e.id(), new float[]{box[0] * scale, box[1] * scale, box[2] * scale});
            String src = Obfuscator.withoutShape(raw);
            Obfuscator.Result r = ob.compile(e.id(), src, e.defines(), obfuscate);
            body.append(r.code()).append('\n');
            if (obfuscate) body.append(ob.decoys(4));
            dispatch.append("if(abs(skyAlpha-").append(e.alpha()).append(".0)<0.5){");
            // objects and surfaces both need the cube worked out, and both get placed rather than worn as a sky
            if (r.kind().equals("object")) {
                objects.add(e.id());
                dispatch.append("vec4 o=").append(r.entry()).append("(boxOrigin,skyDir,skyTime);if(o.a<0.004)discard;skyColor=o;return true;}");
            } else if (r.kind().equals("surface")) {
                objects.add(e.id());
                dispatch.append("vec4 o=").append(r.entry()).append("(face,boxOrigin,-inward,skyDir,skyTime);if(o.a<0.004)discard;skyColor=o;return true;}");
            } else if (r.kind().equals("material")) {
                materials.add(e.id());
                dispatch.append("vec4 o=").append(r.entry()).append("(vec4(texel.rgb,1.0),fract(atlasPx/16.0),flatNormal,skyDir,skyTime);if(o.a<0.004)discard;skyColor=o;return true;}");
            } else if (r.kind().equals("overlay")) {
                overlays.add(e.id());
                dispatch.append("vec4 o=").append(r.entry()).append("(gl_FragCoord.xy/ScreenSize,skyDir,skyTime);if(o.a<0.004)discard;skyColor=o;return true;}");
            } else {
                skies = true;
                dispatch.append("intro=introNow;skyColor=vec4(").append(r.entry()).append("(skyDir,skyTime),1.0);return true;}");
            }
            log.accept("compiled " + e.id());
        }
        String frame = "bool drawSky(vec4 texel,vec3 viewPos,vec2 atlasPx,float skyTime,out vec4 skyColor){"
                + "float skyAlpha=texel.a*255.0;vec3 skyDir=normalize(viewPos);" + (obfuscate ? ob.decoyBranch("viewPos", "skyTime", "skyColor") : "")
                + (objects.isEmpty() ? "" : OBJECT_FRAME) + (materials.isEmpty() ? "" : MATERIAL_FRAME) + (skies ? INTRO_FRAME : "")
                + dispatch + "return false;}";
        String sky = "float intro=1.0;\n" + (obfuscate ? ob.seal(body.toString()) : body) + frame;
        java.util.Set<String> wrapperNames = new java.util.HashSet<>(Obfuscator.declared("float intro=1.0;\n" + frame));
        wrapperNames.addAll(java.util.List.of("drawSky", "intro", "texel", "skyColor", "color"));

        Path out = project.dir.resolve("build").resolve(project.name);
        wipe(out);
        for (Tier t : TIERS) {
            Path core = (t.folder.isEmpty() ? out : out.resolve(t.folder)).resolve("assets/minecraft/shaders/core");
            Files.createDirectories(core);
            for (String f : t.files) {
                String text = resource("/tiers/" + t.name + "/" + f);
                if (f.endsWith(".fsh")) text = text.replace("//%SKY%", sky);
                if (f.endsWith(".fsh") && obfuscate) text = ob.scrub(text, wrapperNames);
                if (f.endsWith(".fsh") || f.endsWith(".vsh")) text = stamp(text);
                Files.writeString(core.resolve(f), text, StandardCharsets.UTF_8);
            }
        }
        writeAssets(out);
        writeMeta(out);

        if (check) verify(out);

        Path zip = project.dir.resolve("build").resolve(project.name + ".zip");
        zip(out, zip);
        log.accept("built " + zip);
        return zip;
    }

    private String stamp(String text) {
        int cut = text.indexOf('\n') + 1;
        if (text.startsWith("#extension", cut)) cut = text.indexOf('\n', cut) + 1;
        String note = "\n// " + TOOL + " " + VERSION + " - All Rights reserved\n// Copyright 2026 - " + project.author
                + "\n// Do not edit or redistribute without permission\n";
        return text.substring(0, cut) + note + text.substring(cut);
    }

    private void writeAssets(Path out) throws IOException {
        Path assets = out.resolve("assets").resolve(project.namespace);
        for (Project.Effect e : project.effects) {
            Path tex = assets.resolve("textures/item/" + e.item() + ".png");
            Files.createDirectories(tex.getParent());
            String ref = project.namespace + ":item/" + e.item();
            if (materials.contains(e.id())) {
                ImageIO.write(materialTexture(e), "png", tex.toFile());
                Map<String, Object> model = new LinkedHashMap<>();
                model.put("parent", "minecraft:item/generated");
                model.put("textures", Map.of("layer0", ref));
                write(assets.resolve("models/item/" + e.item() + ".json"), model);
            } else {
                BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                // object textures also store each pixel's own position, the shader reads it back to find the cube
                boolean object = objects.contains(e.id());
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        int rgb = object ? ((x * 16 + 8) << 16) | ((y * 16 + 8) << 8) | 0xff : 0xffffff;
                        img.setRGB(x, y, (e.alpha() << 24) | rgb);
                    }
                }
                ImageIO.write(img, "png", tex.toFile());
                write(assets.resolve("models/item/" + e.item() + ".json"),
                        overlays.contains(e.id()) ? overlayModel(ref) : cubeModel(ref, shapes.getOrDefault(e.id(), new float[]{1f, 1f, 1f})));
            }

            Map<String, Object> tint = new LinkedHashMap<>();
            tint.put("type", "minecraft:custom_model_data");
            tint.put("index", 0);
            tint.put("default", 16777215);
            Map<String, Object> inner = new LinkedHashMap<>();
            inner.put("type", "minecraft:model");
            inner.put("model", ref);
            inner.put("tints", List.of(tint));
            write(assets.resolve("items/" + e.item() + ".json"), Map.of("model", inner));
        }
    }

    private static Map<String, Object> cubeModel(String ref, float[] box) {
        Map<String, Object> face = new LinkedHashMap<>();
        face.put("uv", List.of(0.5, 0.5, 15.5, 15.5));
        face.put("texture", "#sky");
        face.put("tintindex", 0);
        Map<String, Object> faces = new LinkedHashMap<>();
        // flat shapes only get their two big faces, the thin edges would show a squashed copy of the effect
        float widest = Math.max(box[0], Math.max(box[1], box[2]));
        List<String> sides = box[2] < 0.1f * widest ? List.of("north", "south") : box[1] < 0.1f * widest ? List.of("up", "down")
                : List.of("north", "east", "south", "west", "up", "down");
        for (String side : sides) faces.put(side, face);
        Map<String, Object> cube = new LinkedHashMap<>();
        cube.put("from", List.of(8 - box[0] * 8, 8 - box[1] * 8, 8 - box[2] * 8));
        cube.put("to", List.of(8 + box[0] * 8, 8 + box[1] * 8, 8 + box[2] * 8));
        cube.put("shade", false);
        cube.put("faces", faces);
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("textures", Map.of("sky", ref, "particle", ref));
        model.put("elements", List.of(cube));
        if (widest > 1f) {
            // scaled up items still fit their inventory slot and look normal sized when dropped
            double shrink = Math.round(1000.0 / widest) / 1000.0;
            Map<String, Object> fit = Map.of("scale", List.of(shrink, shrink, shrink));
            model.put("display", Map.of("gui", fit, "ground", fit, "fixed", fit));
        }
        return model;
    }

    // an overlay is one huge flat sheet held in the hand. the hand is drawn last on a cleared depth buffer,
    // so the sheet lands on top of everything, and the shader colours it by screen position
    private static Map<String, Object> overlayModel(String ref) {
        Map<String, Object> face = new LinkedHashMap<>();
        face.put("uv", List.of(0.5, 0.5, 15.5, 15.5));
        face.put("texture", "#sky");
        Map<String, Object> sheet = new LinkedHashMap<>();
        sheet.put("from", List.of(-16, -16, 8));
        sheet.put("to", List.of(32, 32, 8));
        sheet.put("shade", false);
        sheet.put("faces", Map.of("north", face, "south", face));
        Map<String, Object> hand = Map.of("rotation", List.of(0, 0, 0), "translation", List.of(0, 0, 0), "scale", List.of(4, 4, 4));
        // other players and the third person camera get a zero sized item so nobody sees the sheet from outside
        Map<String, Object> hidden = Map.of("scale", List.of(0, 0, 0));
        Map<String, Object> display = new LinkedHashMap<>();
        display.put("firstperson_righthand", hand);
        display.put("firstperson_lefthand", hand);
        display.put("thirdperson_righthand", hidden);
        display.put("thirdperson_lefthand", hidden);
        display.put("head", hidden);
        display.put("gui", Map.of("scale", List.of(0.33, 0.33, 0.33)));
        display.put("ground", Map.of("scale", List.of(0.15, 0.15, 0.15)));
        display.put("fixed", Map.of("scale", List.of(0.33, 0.33, 0.33)));
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("textures", Map.of("sky", ref, "particle", ref));
        model.put("elements", List.of(sheet));
        model.put("display", display);
        return model;
    }

    // a png next to the effect file gets used as the item's texture, otherwise it's a plain gem to show the material on
    private BufferedImage materialTexture(Project.Effect e) throws IOException {
        String file = e.file().replaceAll("\\.[^./\\\\]+$", "") + ".png";
        Path own = project.dir.resolve(file);
        BufferedImage img;
        if (Files.exists(own)) {
            BufferedImage read = ImageIO.read(own.toFile());
            if (read == null) throw new IOException(file + " isn't a readable png");
            img = new BufferedImage(read.getWidth(), read.getHeight(), BufferedImage.TYPE_INT_ARGB);
            img.getGraphics().drawImage(read, 0, 0, null);
        } else {
            img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    if (Math.abs(x - 7.5) + Math.abs(y - 7.5) > 7.5) continue;
                    int shade = 150 + (15 - y) * 6;
                    img.setRGB(x, y, 0xff000000 | shade << 16 | shade << 8 | shade);
                }
            }
        }
        // every pixel you can see gets the effect's code, see through ones stay see through
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int argb = img.getRGB(x, y);
                if (argb >>> 24 > 0) img.setRGB(x, y, e.alpha() << 24 | (argb & 0xffffff));
            }
        }
        return img;
    }

    private void writeMeta(Path out) throws IOException {
        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("pack_format", 34);
        pack.put("description", project.description);
        pack.put("supported_formats", Map.of("min_inclusive", 34, "max_inclusive", 99));
        pack.put("min_format", 34);
        pack.put("max_format", 99);
        List<Object> entries = new ArrayList<>();
        for (Tier t : TIERS) {
            if (t.folder.isEmpty()) continue;
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("directory", t.folder);
            e.put("formats", Map.of("min_inclusive", t.min, "max_inclusive", t.max));
            e.put("min_format", t.min);
            e.put("max_format", t.max);
            entries.add(e);
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("pack", pack);
        meta.put("overlays", Map.of("entries", entries));
        write(out.resolve("pack.mcmeta"), meta);
        Path icon = project.dir.resolve("pack.png");
        if (Files.exists(icon)) Files.copy(icon, out.resolve("pack.png"));
    }

    private void verify(Path out) throws IOException {
        Path glslang = glslang();
        if (glslang == null) {
            log.accept("glslang isn't available, so the shader check was skipped");
            return;
        }
        Map<String, List<List<String>>> modes = Map.of(
                "legacy", List.of(List.of()), "ubo", List.of(List.of()),
                "26_1", List.of(List.of(), List.of("ALPHA_CUTOUT=0.1")),
                "26_2", List.of(List.of(), List.of("ALPHA_CUTOUT=0.1")),
                "26_3", List.of(List.of(), List.of("OIT_ALPHA_ONLY"), List.of("OIT_ACCUMULATE"), List.of("GLINT"),
                        List.of("GLINT", "GLINT_SPECIAL"), List.of("ALPHA_CUTOUT=0.1")));
        Path tmp = Files.createTempDirectory("vessel");
        int failed = 0;
        try {
            for (Tier t : TIERS) {
                Path core = (t.folder.isEmpty() ? out : out.resolve(t.folder)).resolve("assets/minecraft/shaders/core");
                for (String f : t.files) {
                    if (f.endsWith(".json")) continue;
                    String stage = f.endsWith(".vsh") ? "vert" : "frag";
                    String src = Files.readString(core.resolve(f), StandardCharsets.UTF_8);
                    for (List<String> defs : modes.get(t.name)) {
                        Path file = tmp.resolve("check." + stage);
                        Files.writeString(file, expand(src, t.name, defs), StandardCharsets.UTF_8);
                        String gl = run(List.of(glslang.toString(), "-S", stage, file.toString()));
                        if (gl != null) {
                            failed++;
                            log.accept("FAILED " + t.name + " " + f + " " + String.join(" ", defs) + "\n" + gl);
                        }
                        // 26.3 can run on Vulkan, which wants valid SPIR-V on top of plain GLSL
                        if (t.name.equals("26_3")) {
                            String vk = run(List.of(glslang.toString(), "-V", "--amb", "--aml", "-S", stage, file.toString(), "-o", tmp.resolve("out.spv").toString()));
                            if (vk != null) {
                                failed++;
                                log.accept("FAILED vulkan " + f + " " + String.join(" ", defs) + "\n" + vk);
                            }
                        }
                    }
                }
            }
        } finally {
            wipe(tmp);
        }
        if (failed > 0) throw new IOException(failed + " shader checks failed, see the log above");
        log.accept("shader check passed for every version");
    }

    private String expand(String src, String tier, List<String> defs) throws IOException {
        Matcher m = INCLUDE.matcher(src);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String name = m.group(1).substring(m.group(1).lastIndexOf(':') + 1);
            String stub = resource("/stubs/" + tier + "/" + name).replaceAll("#version.*", "");
            m.appendReplacement(sb, Matcher.quoteReplacement(stub));
        }
        m.appendTail(sb);
        String text = sb.toString();
        int cut = text.indexOf('\n') + 1;
        if (text.startsWith("#extension", cut)) cut = text.indexOf('\n', cut) + 1;
        StringBuilder head = new StringBuilder();
        for (String d : defs) head.append("#define ").append(d.replace('=', ' ')).append('\n');
        return text.substring(0, cut) + head + text.substring(cut);
    }

    private static String run(List<String> cmd) throws IOException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        StringBuilder errors = new StringBuilder();
        for (String line : out.split("\\R")) if (line.contains("ERROR")) errors.append(line).append('\n');
        return p.exitValue() == 0 && errors.isEmpty() ? null : errors.toString().strip();
    }

    private static Path cachedGlslang;

    static synchronized Path glslang() throws IOException {
        if (cachedGlslang != null) return cachedGlslang;
        // only the Windows build is bundled, Linux and Mac use the one their package manager installs
        if (!System.getProperty("os.name").toLowerCase().contains("win")) return cachedGlslang = onPath("glslangValidator", "glslang");
        try (InputStream in = Forge.class.getResourceAsStream("/tools/glslang.exe")) {
            if (in == null) return null;
            Path exe = Files.createTempDirectory("vessel-tools").resolve("glslang.exe");
            Files.copy(in, exe);
            exe.toFile().deleteOnExit();
            cachedGlslang = exe;
            return exe;
        }
    }

    private static Path onPath(String... names) {
        String path = System.getenv("PATH");
        if (path == null) return null;
        for (String name : names) {
            for (String dir : path.split(java.io.File.pathSeparator)) {
                Path p = Path.of(dir, name);
                if (Files.isExecutable(p)) return p;
            }
        }
        return null;
    }

    static String resource(String path) throws IOException {
        try (InputStream in = Forge.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void write(Path file, Object json) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, Json.write(json) + "\n", StandardCharsets.UTF_8);
    }

    private static void wipe(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }

    private static void zip(Path dir, Path zip) throws IOException {
        try (OutputStream os = Files.newOutputStream(zip); ZipOutputStream z = new ZipOutputStream(os);
             Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                z.putNextEntry(new ZipEntry(dir.relativize(p).toString().replace('\\', '/')));
                Files.copy(p, z);
                z.closeEntry();
            }
        }
    }
}
