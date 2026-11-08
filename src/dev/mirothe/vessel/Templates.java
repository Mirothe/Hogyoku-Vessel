package dev.mirothe.vessel;

final class Templates {
    record Example(String id, String name, String level, String blurb) {
        java.awt.image.BufferedImage thumb() {
            try (var in = Templates.class.getResourceAsStream("/examples/thumbs/" + id + ".png")) {
                return in == null ? null : javax.imageio.ImageIO.read(in);
            } catch (Exception e) {
                return null;
            }
        }

        Gallery.Choice choice() {
            return new Gallery.Choice(id, name, level, blurb, source(), thumb());
        }

        String source() {
            try (var in = Templates.class.getResourceAsStream("/examples/" + id + ".glsl")) {
                return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                return NEW_EFFECT;
            }
        }
    }

    static final java.util.List<Example> EXAMPLES = java.util.List.of(
            new Example("black_hole_object", "Black Hole", "Object", "a real one floating in the item"),
            new Example("hogyoku_crystal", "Hogyoku", "Object", "faceted crystal with energy inside"),
            new Example("hollow_purple", "Hollow Purple", "Object", "red and blue collide into purple"),
            new Example("ringed_planet", "Ringed Planet", "Object", "gas giant with shadowed rings"),
            new Example("plasma_core", "Plasma Core", "Object", "lightning ball in spinning rings"),
            new Example("tesseract", "Tesseract", "Object", "a 4D cube turning through itself"),
            new Example("mandelbulb", "Mandelbulb", "Object", "a 3D fractal that slowly breathes"),
            new Example("gyroscope", "Gyroscope", "Object", "brass rings spinning in a gimbal"),
            new Example("liquid_metal", "Liquid Metal", "Object", "rippling chrome that sheds drops"),
            new Example("lava_lamp", "Lava Lamp", "Object", "wax blobs melting into each other"),
            new Example("mini_sun", "Mini Sun", "Object", "a boiling star with a corona"),
            new Example("portal", "Portal", "Surface", "a round swirling gateway"),
            new Example("rune_circle", "Rune Circle", "Surface", "a magic circle on the floor"),
            new Example("hologram_screen", "Hologram Screen", "Surface", "a floating panel of readouts"),
            new Example("force_field", "Force Field", "Surface", "a hex shield wall with hit ripples"),
            new Example("glitch_screen", "Glitch Screen", "Surface", "tearing, RGB split and noise"),
            new Example("lava_flow", "Lava Flow", "Material", "molten cracks under a dark crust"),
            new Example("star_cloth", "Star Cloth", "Material", "deep space seen through the item"),
            new Example("gold_shine", "Gold Shine", "Material", "solid gold with a sweeping glint"),
            new Example("cursed_flame", "Cursed Flame", "Material", "blocky blue fire burning upward"),
            new Example("rain_on_lens", "Rain on Lens", "Overlay", "drops beading and running down"),
            new Example("frost_edges", "Frost", "Overlay", "ice creeping in from the edges"),
            new Example("heartbeat", "Heartbeat", "Overlay", "red pulsing in on every beat"),
            new Example("domain_veil", "Domain Veil", "Overlay", "dark red air full of embers"),
            new Example("moonlit_ocean", "Moonlit Ocean", "Sky", "waves, moon and milky way"),
            new Example("astral_sigil", "Astral Sigil", "Sky", "a huge glowing sigil overhead"),
            new Example("event_horizon", "Event Horizon", "Sky", "black hole that bends light"),
            new Example("cosmic_rift", "Cosmic Rift", "Sky", "a tear with plasma pouring out"),
            new Example("hyperspace", "Hyperspace", "Sky", "stretched starlight"));

    static final String NEW_SURFACE = """
            // the line below picks the item's shape: cube, panel, pillar or floor
            #pragma vessel shape panel

            // uv runs from 0 to 1 across each face of the item, pos is the same point in the item's -1 to 1 box,
            // normal points out of the face and view is the direction you're looking. return alpha 0 to cut holes
            vec4 surface(vec2 uv, vec3 pos, vec3 normal, vec3 view, float time) {
                vec2 grid = abs(fract(uv * 4.0) - 0.5);
                float line = 1.0 - smoothstep(0.42, 0.5, max(grid.x, grid.y));
                vec3 color = mix(vec3(0.05, 0.08, 0.12), vec3(0.4, 0.8, 1.0), line);
                color *= 0.8 + 0.2 * sin(time * 2.0 + uv.y * 10.0);
                return vec4(color, 1.0);
            }
            """;

    static final String PREVIEW_SURFACE_TAIL = """

            vec2 previewBox(vec3 ro, vec3 rd) {
                vec3 inv = 1.0 / rd;
                vec3 a = (-1.0 - ro) * inv;
                vec3 b = (1.0 - ro) * inv;
                vec3 lo = min(a, b);
                vec3 hi = max(a, b);
                return vec2(max(max(lo.x, lo.y), lo.z), min(min(hi.x, hi.y), hi.z));
            }

            void main() {
                float f = 1.0 / tan(radians(35.0));
                vec3 rd = normalize(uView * vec3(screen.x * uAspect, screen.y, -f));
                vec3 ro = uView * vec3(0.0, 0.0, 3.0);
                vec3 back = mix(vec3(0.05), vec3(0.11), screen.y * 0.5 + 0.5);
                vec2 hit = previewBox(ro / SHAPE_SIZE, rd / SHAPE_SIZE);
                if (hit.x < hit.y && hit.y > 0.0) {
                    vec3 p = (ro + rd * max(hit.x, 0.0)) / SHAPE_SIZE;
                    vec3 a = abs(p);
                    // the game only gives flat shapes their two big faces, so the preview skips the thin edges too
                    bool edge = (SHAPE_SIZE.z < 0.1 && a.z < 0.999) || (SHAPE_SIZE.y < 0.1 && a.y < 0.999);
                    vec3 n;
                    vec2 uv;
                    if (a.x >= a.y && a.x >= a.z) { n = vec3(sign(p.x), 0.0, 0.0); uv = p.zy; }
                    else if (a.y >= a.z) { n = vec3(0.0, sign(p.y), 0.0); uv = p.xz; }
                    else { n = vec3(0.0, 0.0, sign(p.z)); uv = p.xy; }
                    vec4 o = edge ? vec4(0.0) : surface(uv * 0.5 + 0.5, p, n, rd, uTime);
                    back = mix(back, clamp(o.rgb, 0.0, 1.0), clamp(o.a, 0.0, 1.0));
                }
                outColor = vec4(back, 1.0);
            }
            """;

    static final String NEW_MATERIAL = """
            // texel is the item's own pixel colour, uv goes 0 to 1 across the sprite, normal faces out of the item
            // and view is the direction you're looking. every pixel you can see on the item runs through this
            vec4 material(vec4 texel, vec2 uv, vec3 normal, vec3 view, float time) {
                float wave = 0.5 + 0.5 * sin(uv.x * 12.0 + uv.y * 6.0 - time * 3.0);
                vec3 color = texel.rgb * mix(vec3(0.2, 0.4, 1.0), vec3(0.7, 1.0, 1.0), wave);
                return vec4(color, 1.0);
            }
            """;

    static final String NEW_OVERLAY = """
            // screen goes from 0,0 in the bottom left corner to 1,1 in the top right, dir is where you're looking.
            // whatever you return is laid over the player's view, so keep alpha low where they still need to see
            vec4 overlay(vec2 screen, vec3 dir, float time) {
                float edge = length(screen - 0.5) * 1.4;
                float pulse = 0.85 + 0.15 * sin(time * 2.0);
                return vec4(0.25, 0.0, 0.05, smoothstep(0.45, 1.0, edge) * pulse);
            }
            """;

    static final String PREVIEW_MATERIAL_TAIL = """

            void main() {
                float f = 1.0 / tan(radians(35.0));
                vec3 rd = normalize(uView * vec3(screen.x * uAspect, screen.y, -f));
                vec3 ro = uView * vec3(0.0, 0.0, 1.9);
                vec3 back = mix(vec3(0.05), vec3(0.11), screen.y * 0.5 + 0.5);
                float t = -ro.z / rd.z;
                vec2 p = (ro + rd * t).xy;
                // the same gem the build uses when there's no png of your own, so you see it on real pixels
                vec2 pixel = floor((vec2(p.x, -p.y) * 0.5 + 0.5) * 16.0);
                if (t > 0.0 && abs(p.x) < 1.0 && abs(p.y) < 1.0 && abs(pixel.x - 7.5) + abs(pixel.y - 7.5) <= 7.5) {
                    vec3 shade = vec3(150.0 + (15.0 - pixel.y) * 6.0) / 255.0;
                    vec3 n = vec3(0.0, 0.0, rd.z < 0.0 ? 1.0 : -1.0);
                    vec4 o = material(vec4(shade, 1.0), vec2(p.x, -p.y) * 0.5 + 0.5, n, rd, uTime);
                    back = mix(back, clamp(o.rgb, 0.0, 1.0), clamp(o.a, 0.0, 1.0));
                }
                outColor = vec4(back, 1.0);
            }
            """;

    static final String PREVIEW_OVERLAY_TAIL = """

            void main() {
                float f = 1.0 / tan(radians(35.0));
                vec3 dir = normalize(uView * vec3(screen.x * uAspect, screen.y, -f));
                // a stand in world with a horizon and a grid floor, just so the overlay has something to sit on
                vec3 world = mix(vec3(0.45, 0.6, 0.8), vec3(0.15, 0.3, 0.6), clamp(dir.y * 2.0, 0.0, 1.0));
                if (dir.y < 0.0) {
                    vec2 floorAt = dir.xz / -dir.y * 1.6;
                    vec2 cell = abs(fract(floorAt) - 0.5);
                    world = mix(vec3(0.2, 0.32, 0.18), vec3(0.28, 0.42, 0.24), step(0.47, max(cell.x, cell.y)));
                    world = mix(world, vec3(0.45, 0.6, 0.8), exp(dir.y * 12.0));
                }
                vec4 o = overlay(screen * 0.5 + 0.5, dir, uTime);
                outColor = vec4(mix(world, clamp(o.rgb, 0.0, 1.0), clamp(o.a, 0.0, 1.0)), 1.0);
            }
            """;

    static final String NEW_OBJECT = """
            // origin is where the ray enters the item's box (-1 to 1 on every axis), dir is the ray direction.
            // return alpha 0 wherever there's nothing, so the world shows through
            vec4 object(vec3 origin, vec3 dir, float time) {
                float t = 0.0;
                for (int i = 0; i < 64; i++) {
                    vec3 p = origin + dir * t;
                    float d = length(p) - (0.5 + 0.05 * sin(time * 2.0));
                    if (d < 0.001) {
                        vec3 n = normalize(p);
                        float light = max(dot(n, normalize(vec3(0.5, 0.8, 0.3))), 0.0);
                        return vec4(vec3(0.3, 0.6, 1.0) * (0.2 + light), 1.0);
                    }
                    t += d;
                    if (t > 3.5) break;
                }
                return vec4(0.0);
            }
            """;

    static final String NEW_EFFECT = """
            float hash(vec3 p) {
                return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453);
            }

            float noise(vec3 p) {
                vec3 i = floor(p);
                vec3 f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                return mix(mix(mix(hash(i), hash(i + vec3(1, 0, 0)), f.x),
                               mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
                           mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x),
                               mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
            }

            // dir is where the player is looking, time is in seconds
            vec3 sky(vec3 dir, float time) {
                float n = noise(dir * 4.0 + time * 0.2);
                vec3 low = vec3(0.05, 0.02, 0.12);
                vec3 high = vec3(0.45, 0.25, 0.85);
                return mix(low, high, n * (0.6 + 0.4 * dir.y));
            }
            """;

    static final String PREVIEW_VERTEX = """
            #version 330
            out vec2 screen;
            void main() {
                vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                screen = p * 2.0 - 1.0;
                gl_Position = vec4(screen, 0.0, 1.0);
            }
            """;

    static final String PREVIEW_HEAD = """
            #version 330
            in vec2 screen;
            out vec4 outColor;
            uniform float uTime;
            uniform float uAspect;
            uniform mat3 uView;
            float intro = 1.0;
            """;

    static final String PREVIEW_TAIL = """

            void main() {
                float f = 1.0 / tan(radians(35.0));
                vec3 dir = normalize(uView * vec3(screen.x * uAspect, screen.y, -f));
                // replays the intro every 15 seconds so you can watch it without going in game
                intro = clamp(mod(uTime, 15.0) / 5.0, 0.0, 1.0);
                outColor = vec4(clamp(sky(dir, uTime), 0.0, 1.0), 1.0);
            }
            """;

    static final String PREVIEW_OBJECT_TAIL = """

            vec2 previewBox(vec3 ro, vec3 rd) {
                vec3 inv = 1.0 / rd;
                vec3 a = (-1.0 - ro) * inv;
                vec3 b = (1.0 - ro) * inv;
                vec3 lo = min(a, b);
                vec3 hi = max(a, b);
                return vec2(max(max(lo.x, lo.y), lo.z), min(min(hi.x, hi.y), hi.z));
            }

            void main() {
                float f = 1.0 / tan(radians(35.0));
                vec3 rd = normalize(uView * vec3(screen.x * uAspect, screen.y, -f));
                vec3 ro = uView * vec3(0.0, 0.0, 2.5);
                vec3 back = mix(vec3(0.05), vec3(0.11), screen.y * 0.5 + 0.5);
                vec2 hit = previewBox(ro, rd);
                if (hit.x < hit.y && hit.y > 0.0) {
                    vec4 o = object(ro + rd * max(hit.x, 0.0), rd, uTime);
                    back = mix(back, clamp(o.rgb, 0.0, 1.0), clamp(o.a, 0.0, 1.0));
                }
                outColor = vec4(back, 1.0);
            }
            """;

    static int headLines(String defines) {
        return (int) (PREVIEW_HEAD + defines).chars().filter(c -> c == '\n').count();
    }

    private Templates() {
    }
}
