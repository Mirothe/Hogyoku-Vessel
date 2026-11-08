package dev.mirothe.vessel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Obfuscator {
    static final Set<String> TYPES = Set.of("void", "bool", "int", "uint", "float", "double", "vec2", "vec3", "vec4", "ivec2",
            "ivec3", "ivec4", "uvec2", "uvec3", "uvec4", "bvec2", "bvec3", "bvec4", "mat2", "mat3", "mat4", "mat2x2", "mat2x3",
            "mat2x4", "mat3x2", "mat3x3", "mat3x4", "mat4x2", "mat4x3", "mat4x4", "sampler2D");
    static final Set<String> KEYWORDS = new HashSet<>(TYPES);

    static {
        KEYWORDS.addAll(List.of("const", "in", "out", "inout", "uniform", "struct", "if", "else", "for", "while", "do", "return",
                "break", "continue", "discard", "true", "false", "highp", "mediump", "lowp", "flat", "smooth", "layout", "switch",
                "case", "default", "precision", "attribute", "varying", "sample", "centroid", "invariant"));
    }

    private static final Pattern TOKEN = Pattern.compile(
            "(?<comment>//[^\\n]*|/\\*.*?\\*/)|(?<pre>^[ \\t]*#[^\\n]*)|(?<ident>[A-Za-z_]\\w*)"
                    + "|(?<num>\\d+\\.?\\d*(?:[eE][-+]?\\d+)?[fFuU]?|\\.\\d+(?:[eE][-+]?\\d+)?[fF]?)|(?<ws>\\s+)"
                    // two character operators stay one token, so "a - -b" (two tokens) and "a--" (one) can be told apart
                    + "|(?<op>\\+\\+|--|[-+*/<>=!]=|&&|\\|\\||\\^\\^|<<|>>|.)",
            Pattern.DOTALL | Pattern.MULTILINE);
    private static final Pattern DEFINE = Pattern.compile("\\s*#\\s*define\\s+([A-Za-z_]\\w*)");
    // directives whose text is read by the game or the driver, not by us. nothing inside them may be renamed
    private static final Pattern VERBATIM_DIRECTIVE = Pattern.compile("#\\s*(moj_import|version|extension|pragma|include|line|error)\\b");
    // built-in functions. a variable that happens to share one of these names must not drag the real calls along with it
    static final Set<String> BUILTINS = Set.of("radians", "degrees", "sin", "cos", "tan", "asin", "acos", "atan", "sinh", "cosh",
            "tanh", "asinh", "acosh", "atanh", "pow", "exp", "log", "exp2", "log2", "sqrt", "inversesqrt", "abs", "sign", "floor",
            "trunc", "round", "roundEven", "ceil", "fract", "mod", "modf", "min", "max", "clamp", "mix", "step", "smoothstep",
            "isnan", "isinf", "fma", "frexp", "ldexp", "floatBitsToInt", "floatBitsToUint", "intBitsToFloat", "uintBitsToFloat",
            "packUnorm2x16", "packSnorm2x16", "packUnorm4x8", "packSnorm4x8", "unpackUnorm2x16", "unpackSnorm2x16",
            "unpackUnorm4x8", "unpackSnorm4x8", "packHalf2x16", "unpackHalf2x16", "length", "distance", "dot", "cross",
            "normalize", "faceforward", "reflect", "refract", "matrixCompMult", "outerProduct", "transpose", "determinant",
            "inverse", "lessThan", "lessThanEqual", "greaterThan", "greaterThanEqual", "equal", "notEqual", "any", "all", "not",
            "texture", "textureLod", "textureSize", "textureProj", "textureGrad", "textureOffset", "texelFetch", "textureGather",
            "textureQueryLod", "textureQueryLevels", "dFdx", "dFdy", "dFdxFine", "dFdyFine", "dFdxCoarse", "dFdyCoarse", "fwidth",
            "fwidthFine", "fwidthCoarse", "bitfieldExtract", "bitfieldInsert", "bitfieldReverse", "bitCount", "findLSB",
            "findMSB", "uaddCarry", "usubBorrow", "umulExtended", "imulExtended", "emitVertex", "endPrimitive");
    private static final Pattern WORD = Pattern.compile("[A-Za-z_]\\w*");
    // characters that look alike in most fonts. each build mixes two of these sets, so no two packs share a naming style
    private static final String[] LOOKALIKES = {"lI1", "O0Q", "S5", "Il1i", "oO0", "vVwW", "Z2z", "B8"};
    private static final Pattern FLOAT = Pattern.compile("[0-9.]*[.eE][0-9.eE+-]*[fF]?");

    record Token(String kind, String text) {
    }

    record Result(String code, String entry, String kind) {
    }

    // which kind of effect a file is, going by the entry function it defines
    static String kind(String src) {
        if (MATERIAL_ENTRY.matcher(src).find()) return "material";
        if (OVERLAY_ENTRY.matcher(src).find()) return "overlay";
        if (SURFACE_ENTRY.matcher(src).find()) return "surface";
        if (OBJECT_ENTRY.matcher(src).find()) return "object";
        return "sky";
    }

    private static final Pattern SHAPE = Pattern.compile("(?m)^[ \\t]*#pragma\\s+vessel\\s+shape\\s+(\\w+)[^\\n]*\\n?");

    // half the size of the item's box on each axis, for the shapes a surface effect can ask for
    static float[] shapeSize(String src) {
        Matcher m = SHAPE.matcher(src);
        String name = m.find() ? m.group(1) : "cube";
        return switch (name) {
            case "panel" -> new float[]{1f, 1f, 0.06f};
            case "pillar" -> new float[]{0.45f, 1f, 0.45f};
            case "floor" -> new float[]{1f, 0.06f, 1f};
            default -> new float[]{1f, 1f, 1f};
        };
    }

    private static final Pattern SCALE = Pattern.compile("(?m)^[ \\t]*#pragma\\s+vessel\\s+scale\\s+([\\d.]+)[^\\n]*\\n?");

    // how many blocks wide the item is. models can't reach past -16 and 32, so 3 is as big as it gets
    static float scale(String src) {
        Matcher m = SCALE.matcher(src);
        if (!m.find()) return 1f;
        try {
            return Math.max(0.1f, Math.min(3f, Float.parseFloat(m.group(1))));
        } catch (NumberFormatException e) {
            return 1f;
        }
    }

    // the shape and scale lines are for the build, not the GPU, so they come out before compiling
    static String withoutShape(String src) {
        return SCALE.matcher(SHAPE.matcher(src).replaceAll("\n")).replaceAll("\n");
    }

    private static final Pattern OBJECT_ENTRY = Pattern.compile("\\bvec4\\s+object\\s*\\(");
    private static final Pattern SURFACE_ENTRY = Pattern.compile("\\bvec4\\s+surface\\s*\\(");
    private static final Pattern MATERIAL_ENTRY = Pattern.compile("\\bvec4\\s+material\\s*\\(");
    private static final Pattern OVERLAY_ENTRY = Pattern.compile("\\bvec4\\s+overlay\\s*\\(");

    private final Random rng;
    private final Set<String> used = new HashSet<>();
    private final Map<String, Integer> pool = new java.util.LinkedHashMap<>();
    private String poolName;
    private final String letters;
    private final int shortest;
    private final List<String> decoyUses = new ArrayList<>();
    private final Map<String, String> wrapper = new HashMap<>();

    Obfuscator(long seed) {
        rng = new Random(seed);
        String a = LOOKALIKES[rng.nextInt(LOOKALIKES.length)], b = LOOKALIKES[rng.nextInt(LOOKALIKES.length)];
        letters = a.equals(b) ? a : a + b;
        shortest = 5 + rng.nextInt(5);
    }

    String freshName() {
        while (true) {
            String starts = letters.replaceAll("[0-9]", "");
            StringBuilder b = new StringBuilder().append(starts.charAt(rng.nextInt(starts.length())));
            int length = shortest + rng.nextInt(6);
            for (int k = 1; k < length; k++) b.append(letters.charAt(rng.nextInt(letters.length())));
            String n = b.toString();
            if (!KEYWORDS.contains(n) && used.add(n)) return n;
        }
    }

    static List<Token> tokenize(String src) {
        List<Token> out = new ArrayList<>();
        Matcher m = TOKEN.matcher(src);
        while (m.find()) {
            for (String k : new String[]{"comment", "pre", "ident", "num", "ws", "op"}) {
                if (m.group(k) != null) {
                    out.add(new Token(k, m.group(k)));
                    break;
                }
            }
        }
        return out;
    }

    private static void declarations(List<Token> all, Set<String> names, Set<String> fields) {
        Set<String> types = new HashSet<>(TYPES);
        List<Token> sig = new ArrayList<>();
        for (Token t : all) if (!t.kind.equals("ws") && !t.kind.equals("comment")) sig.add(t);
        int depth = 0, brace = 0, declDepth = 0;
        boolean declActive = false;
        Integer structBrace = null;
        for (int i = 0; i < sig.size(); i++) {
            Token t = sig.get(i);
            if (t.kind.equals("pre")) {
                Matcher m = DEFINE.matcher(t.text);
                if (m.lookingAt()) names.add(m.group(1));
                continue;
            }
            Token prev = i > 0 ? sig.get(i - 1) : new Token("", "");
            Token next = i + 1 < sig.size() ? sig.get(i + 1) : new Token("", "");
            switch (t.text) {
                case "(" -> depth++;
                case ")" -> {
                    depth--;
                    if (depth < declDepth) declActive = false;
                }
                case "{" -> {
                    brace++;
                    declActive = false;
                }
                case "}" -> {
                    if (structBrace != null && brace == structBrace) structBrace = null;
                    brace--;
                }
                case ";" -> declActive = false;
                default -> {
                    if (!t.kind.equals("ident")) break;
                    if (prev.text.equals("struct")) {
                        types.add(t.text);
                        names.add(t.text);
                        if (next.text.equals("{")) structBrace = brace + 1;
                        break;
                    }
                    if (KEYWORDS.contains(t.text)) break;
                    boolean afterType = prev.kind.equals("ident") && types.contains(prev.text);
                    boolean afterComma = prev.text.equals(",") && declActive && depth == declDepth;
                    if (!afterType && !afterComma) break;
                    if (structBrace != null && brace == structBrace) fields.add(t.text);
                    else names.add(t.text);
                    declActive = !next.text.equals("(");
                    declDepth = depth;
                }
            }
        }
    }

    Result compile(String id, String src, Map<String, String> defines, boolean obfuscate) {
        StringBuilder head = new StringBuilder();
        defines.forEach((k, v) -> head.append("#define ").append(k).append(' ').append(v).append('\n'));
        src = head + src;
        List<Token> toks = tokenize(src);
        Set<String> names = new HashSet<>(), fields = new HashSet<>();
        declarations(toks, names, fields);
        String kind = names.contains("material") ? "material" : names.contains("overlay") ? "overlay"
                : names.contains("surface") ? "surface" : names.contains("object") ? "object" : "sky";
        if (kind.equals("sky") && !names.contains("sky")) {
            throw new IllegalArgumentException(id + ": needs one of  vec3 sky(vec3 dir, float time),  vec4 object(vec3 origin, vec3 dir, float time)"
                    + ",  vec4 surface(vec2 uv, vec3 pos, vec3 normal, vec3 view, float time)"
                    + ",  vec4 material(vec4 texel, vec2 uv, vec3 normal, vec3 view, float time)  or  vec4 overlay(vec2 screen, vec3 dir, float time)");
        }
        if (obfuscate) toks = tokenize(inline(toks, kind));
        names.clear();
        fields.clear();
        declarations(toks, names, fields);
        Map<String, String> map = new HashMap<>();
        String prefix = id.replaceAll("\\W", "_") + "_";
        Set<String> all = new java.util.TreeSet<>(names);
        all.addAll(fields);
        // a local called "mix" or "length" is renamed on its own, but the built-in of that name keeps working everywhere else
        for (String n : all) if (!BUILTINS.contains(n)) map.put(n, obfuscate ? freshName() : prefix + n);
        return new Result(rename(toks, map, fields, obfuscate), map.get(kind), kind);
    }

    // text of the nearest token before (step -1) or after (step 1) index from, skipping whitespace and comments
    private static String sigText(List<Token> toks, int from, int step) {
        for (int k = from + step; k >= 0 && k < toks.size(); k += step) {
            Token t = toks.get(k);
            if (!t.kind.equals("ws") && !t.kind.equals("comment")) return t.text;
        }
        return "";
    }

    // "a - -b" and "a + +b" must keep their space when squeezed, or they turn into the decrement and increment operators
    private static boolean signClash(String prev, String next) {
        return (prev.endsWith("+") && next.startsWith("+")) || (prev.endsWith("-") && next.startsWith("-"));
    }

    private String rename(List<Token> toks, Map<String, String> map, Set<String> fields, boolean minify) {
        StringBuilder out = new StringBuilder();
        String prevSig = "";
        int depth = 0;
        boolean inConst = false;
        for (int ti = 0; ti < toks.size(); ti++) {
            Token t = toks.get(ti);
            if (t.text.equals("{")) depth++;
            else if (t.text.equals("}")) depth--;
            else if (t.text.equals("const")) inConst = true;
            else if (t.text.equals(";")) inConst = false;
            // numbers inside functions go into one shared array. globals and consts have to stay literal,
            // GLSL wants constant expressions there and an array lookup doesn't count
            if (minify && t.kind.equals("num") && depth > 0 && !inConst && FLOAT.matcher(t.text).matches()) {
                out.append('\u0000').append(pool.computeIfAbsent(t.text, k -> pool.size())).append('\u0000');
                prevSig = t.text;
                continue;
            }
            switch (t.kind) {
                case "comment" -> {
                    if (!minify) out.append(t.text);
                }
                case "pre" -> {
                    String directive = t.text.strip();
                    StringBuilder line = new StringBuilder();
                    if (VERBATIM_DIRECTIVE.matcher(directive).lookingAt()) {
                        line.append(directive);
                    } else {
                        Matcher m = WORD.matcher(directive);
                        while (m.find()) m.appendReplacement(line, Matcher.quoteReplacement(map.getOrDefault(m.group(), m.group())));
                        m.appendTail(line);
                    }
                    out.append('\n').append(line).append('\n');
                    prevSig = "";
                }
                case "ws" -> {
                    if (!minify) out.append(t.text);
                    else out.append(signClash(sigText(toks, ti, -1), sigText(toks, ti, 1)) ? '\u0001' : ' ');
                }
                case "ident" -> {
                    String v = t.text;
                    if (prevSig.equals(".")) v = fields.contains(v) ? map.getOrDefault(v, v) : v;
                    else v = map.getOrDefault(v, v);
                    out.append(v);
                    prevSig = v;
                }
                default -> {
                    out.append(t.text);
                    prevSig = t.text;
                }
            }
        }
        if (!minify) return out.toString();
        StringBuilder text = new StringBuilder();
        for (String line : out.toString().split("\n")) {
            line = line.strip();
            if (line.isEmpty()) continue;
            if (!line.startsWith("#")) {
                line = line.replaceAll("\\s*([{}();,=+\\-*/<>!&|?:\\[\\]])\\s*", "$1");
                line = line.replaceAll("\\b(else|return)(?=[A-Za-z_\\d.(\\u0000-])", "$1 ");
            }
            text.append(line).append('\n');
        }
        // \u0001 marked the spaces that have to survive the squeezing above
        return text.toString().replace('\u0001', ' ');
    }

    // every use of a number becomes two slots of the pool put back together with + - or *, split differently each time.
    // only splits that land on exactly the same float are used, so noise and hashes come out bit for bit the same
    String seal(String body) {
        if (pool.isEmpty()) return body;
        if (poolName == null) poolName = freshName();
        Map<Integer, String> literal = new HashMap<>();
        pool.forEach((text, id) -> literal.put(id, text));
        Map<String, Integer> slots = new java.util.LinkedHashMap<>();
        List<String[]> uses = new ArrayList<>();
        Matcher m = Pattern.compile("\u0000(\\d+)\u0000").matcher(body);
        while (m.find()) {
            String[] parts = split(literal.get(Integer.parseInt(m.group(1))));
            for (int k = 1; k < parts.length; k++) slots.putIfAbsent(parts[k], slots.size());
            uses.add(parts);
        }
        List<String> values = new ArrayList<>(slots.keySet());
        java.util.Collections.shuffle(values, rng);
        Map<String, Integer> at = new HashMap<>();
        for (int k = 0; k < values.size(); k++) at.put(values.get(k), k);
        m.reset();
        StringBuilder sb = new StringBuilder();
        int u = 0;
        while (m.find()) {
            String[] parts = uses.get(u++);
            String a = poolName + "[" + at.get(parts[1]) + "]";
            String expr = parts.length == 2 ? a : "(" + a + parts[0] + poolName + "[" + at.get(parts[2]) + "])";
            m.appendReplacement(sb, Matcher.quoteReplacement(expr));
        }
        m.appendTail(sb);
        return "const float " + poolName + "[" + values.size() + "]=float[" + values.size() + "](" + String.join(",", values) + ");\n" + sb;
    }

    // {op, a, b} with a op b equal to the literal exactly, or {"", literal} when no exact split turns up
    String[] split(String text) {
        float f = Float.parseFloat(text.replaceAll("[fF]$", ""));
        if (!Float.isFinite(f)) return new String[]{"", text};
        double want = f;
        for (int tries = 0; tries < 12; tries++) {
            int op = rng.nextInt(3);
            if (op == 2) {
                float b = (float) Math.scalb(1.0, rng.nextBoolean() ? 1 + rng.nextInt(4) : -1 - rng.nextInt(4));
                float a = f / b;
                if ((double) a * b == want && Float.isFinite(a) && a != 0) return new String[]{"*", lit(a), lit(b)};
                continue;
            }
            float a = (float) (Math.round((rng.nextDouble() * 2 - 1) * (Math.abs(f) + 4) * 64) / 64.0);
            float b = op == 0 ? f - a : a - f;
            double back = op == 0 ? (double) a + b : (double) a - b;
            if (back == want && a != 0 && b != 0) return new String[]{op == 0 ? "+" : "-", lit(a), lit(b)};
        }
        return new String[]{"", text};
    }

    private static String lit(float v) {
        String s = Float.toString(v);
        return s.contains(".") || s.contains("E") ? s : s + ".0";
    }

    // fake constants and helpers. they're wired into a branch that never runs in game, so tools that strip unused code
    // keep them, and their numbers go through the pool like everything else
    String decoys(int count) {
        StringBuilder b = new StringBuilder();
        String[] kinds = {"float", "vec2", "vec3", "int"};
        for (int k = 0; k < count; k++) {
            String kind = kinds[rng.nextInt(kinds.length)];
            String val = switch (kind) {
                case "float" -> String.format(java.util.Locale.ROOT, "%.6f", rng.nextDouble() * 2000);
                case "int" -> String.valueOf(100 + rng.nextInt(1900));
                default -> {
                    int n = kind.charAt(3) - '0';
                    StringBuilder v = new StringBuilder(kind).append('(');
                    for (int c = 0; c < n; c++) v.append(c > 0 ? "," : "").append(String.format(java.util.Locale.ROOT, "%.6f", rng.nextDouble()));
                    yield v.append(')').toString();
                }
            };
            String name = freshName();
            b.append("const ").append(kind).append(' ').append(name).append('=').append(val).append(";\n");
            decoyUses.add(switch (kind) {
                case "float" -> name;
                case "int" -> "float(" + name + ")";
                default -> name + ".y";
            });
        }
        // a couple of dead helpers that look like the hash and noise functions real effects are full of
        for (int k = 0; k < 2; k++) {
            String name = freshName(), a = freshName(), c = freshName();
            String x = String.format(java.util.Locale.ROOT, "%.4f", 10 + rng.nextDouble() * 300);
            String y = String.format(java.util.Locale.ROOT, "%.4f", 1000 + rng.nextDouble() * 50000);
            boolean flat = rng.nextBoolean();
            b.append(flat
                    ? "float " + name + "(vec2 " + a + "){return fract(sin(dot(" + a + ",vec2(" + x + "," + y + ")))*" + y + ");}\n"
                    : "vec3 " + name + "(vec3 " + a + ",float " + c + "){" + a + "=fract(" + a + "*" + x + ");return " + a
                    + "*" + a + "*(3.0-2.0*" + a + ")+sin(" + c + "*" + x + ");}\n");
            decoyUses.add(flat ? name + "(%P.xy)" : name + "(%P,%T).y");
        }
        return rename(tokenize(b.toString()), Map.of(), Set.of(), true);
    }

    // the branch that keeps the decoys alive. time is never below zero in game, but nothing can prove that from the code
    String decoyBranch(String pos, String time, String color) {
        if (decoyUses.isEmpty()) return "";
        String sum = String.join("+", decoyUses).replace("%P", pos).replace("%T", time);
        return "if(" + time + "<-1.0){" + color + "=vec4(vec3(" + sum + "),1.0);return true;}";
    }

    // Vessel's own wrapper code gets the same treatment as the effects, with one set of names for every version
    String scrub(String text, Set<String> names) {
        StringBuilder out = new StringBuilder();
        String prev = "";
        for (Token t : tokenize(text)) {
            String v = t.text;
            if (t.kind.equals("ident") && !prev.equals(".") && names.contains(v)) v = wrapper.computeIfAbsent(v, k -> freshName());
            out.append(v);
            if (!t.kind.equals("ws") && !t.kind.equals("comment")) prev = v;
        }
        return out.toString();
    }

    static Set<String> declared(String src) {
        Set<String> names = new HashSet<>(), fields = new HashSet<>();
        declarations(tokenize(src), names, fields);
        return names;
    }

    // pastes one line helpers like hashes into wherever they're called, so there are fewer functions to recognise.
    // only helpers whose body is a single return, with plain types and nothing a call site could shadow
    static String inline(List<Token> toks, String entry) {
        List<Token> sig = new ArrayList<>();
        for (Token t : toks) {
            if (t.kind.equals("ws") || t.kind.equals("comment")) continue;
            sig.add(t);
        }
        for (int round = 0; round < 6; round++) {
            String text = join(sig);
            Set<String> local = locals(sig);
            boolean changed = false;
            for (int i = 0; i + 2 < sig.size() && !changed; i++) {
                Helper h = helper(sig, i, entry, local, text);
                if (h == null) continue;
                List<Token> next = new ArrayList<>(sig.subList(0, i));
                next.addAll(sig.subList(h.end, sig.size()));
                List<Token> done = new ArrayList<>();
                int calls = 0;
                for (int k = 0; k < next.size(); k++) {
                    Token t = next.get(k);
                    boolean call = t.text.equals(h.name) && k + 1 < next.size() && next.get(k + 1).text.equals("(")
                            && (k == 0 || !next.get(k - 1).text.equals("."));
                    if (!call) {
                        done.add(t);
                        continue;
                    }
                    int close = matching(next, k + 1);
                    List<List<Token>> args = arguments(next, k + 2, close);
                    String body = h.substitute(args);
                    if (body == null) {
                        done = null;
                        break;
                    }
                    done.addAll(tokenize(body).stream().filter(x -> !x.kind.equals("ws")).toList());
                    calls++;
                    k = close;
                }
                if (done == null || calls == 0) continue;
                sig = done;
                changed = true;
            }
            if (!changed) break;
        }
        return join(sig);
    }

    private record Helper(String name, String type, List<String[]> params, List<Token> expr, int end) {
        // null when an argument would have to be pasted twice and isn't a plain name or number
        String substitute(List<List<Token>> args) {
            if (args.size() != params.size()) return null;
            StringBuilder b = new StringBuilder(type).append("((");
            for (int ei = 0; ei < expr.size(); ei++) {
                Token t = expr.get(ei);
                // a name right after a dot is a field or swizzle (v.x), not the parameter that happens to be called x
                boolean member = ei > 0 && expr.get(ei - 1).text.equals(".");
                int p = -1;
                for (int k = 0; k < params.size(); k++) if (params.get(k)[1].equals(t.text)) p = k;
                if (p < 0 || !t.kind.equals("ident") || member) {
                    b.append(t.text).append(' ');
                    continue;
                }
                List<Token> arg = args.get(p);
                String param = params.get(p)[1];
                long uses = 0;
                for (int k = 0; k < expr.size(); k++) {
                    if (expr.get(k).text.equals(param) && !(k > 0 && expr.get(k - 1).text.equals("."))) uses++;
                }
                if (uses > 1 && !(arg.size() == 1 && !arg.get(0).kind.equals("op"))) return null;
                b.append(params.get(p)[0]).append("(").append(join(arg)).append(")");
            }
            return b.append("))").toString();
        }
    }

    private static Helper helper(List<Token> sig, int i, String entry, Set<String> local, String text) {
        if (depthAt(sig, i) != 0 || !TYPES.contains(sig.get(i).text) || sig.get(i).text.equals("void")) return null;
        if (i > 0 && !List.of(";", "}").contains(sig.get(i - 1).text) && !sig.get(i - 1).kind.equals("pre")) return null;
        String name = sig.get(i + 1).text;
        if (!sig.get(i + 1).kind.equals("ident") || !sig.get(i + 2).text.equals("(") || KEYWORDS.contains(name)) return null;
        if (name.equals("main") || name.equals(entry)) return null;
        List<String[]> params = new ArrayList<>();
        int k = i + 3;
        while (!sig.get(k).text.equals(")")) {
            if (sig.get(k).text.equals("in")) k++;
            if (!TYPES.contains(sig.get(k).text) || !sig.get(k + 1).kind.equals("ident")) return null;
            params.add(new String[]{sig.get(k).text, sig.get(k + 1).text});
            k += 2;
            if (sig.get(k).text.equals(",")) k++;
            else if (!sig.get(k).text.equals(")")) return null;
        }
        if (!sig.get(k + 1).text.equals("{") || !sig.get(k + 2).text.equals("return")) return null;
        int semi = k + 3, depth = 0;
        while (semi < sig.size() && !(depth == 0 && sig.get(semi).text.equals(";"))) {
            String s = sig.get(semi).text;
            if (s.equals("(")) depth++;
            if (s.equals(")")) depth--;
            if (s.equals("{") || s.equals("}")) return null;
            semi++;
        }
        if (semi + 1 >= sig.size() || !sig.get(semi + 1).text.equals("}")) return null;
        List<Token> expr = new ArrayList<>(sig.subList(k + 3, semi));
        Set<String> own = new HashSet<>();
        for (String[] p : params) own.add(p[1]);
        for (Token t : expr) {
            if (t.kind.equals("pre") || t.text.equals(name)) return null;
            if (t.kind.equals("ident") && !own.contains(t.text) && local.contains(t.text)) return null;
        }
        // overloads, prototypes and macros that mention it would all break
        int defs = 0;
        for (int d = 0; d + 2 < sig.size(); d++) {
            if (sig.get(d + 1).text.equals(name) && sig.get(d + 2).text.equals("(") && TYPES.contains(sig.get(d).text)) defs++;
            if (sig.get(d).kind.equals("pre") && WORD.matcher(sig.get(d).text).results().anyMatch(r -> r.group().equals(name))) return null;
        }
        if (defs != 1) return null;
        return new Helper(name, sig.get(i).text, params, expr, semi + 2);
    }

    // every name declared inside a function, parameters included
    private static Set<String> locals(List<Token> sig) {
        Set<String> out = new HashSet<>();
        int brace = 0, paren = 0;
        for (int i = 1; i < sig.size(); i++) {
            String s = sig.get(i).text;
            if (s.equals("{")) brace++;
            else if (s.equals("}")) brace--;
            else if (s.equals("(")) paren++;
            else if (s.equals(")")) paren--;
            else if ((brace > 0 || paren > 0) && sig.get(i).kind.equals("ident") && TYPES.contains(sig.get(i - 1).text)) out.add(s);
        }
        return out;
    }

    private static int depthAt(List<Token> sig, int i) {
        int d = 0;
        for (int k = 0; k < i; k++) {
            if (sig.get(k).text.equals("{")) d++;
            if (sig.get(k).text.equals("}")) d--;
        }
        return d;
    }

    private static int matching(List<Token> sig, int open) {
        int d = 0;
        for (int k = open; k < sig.size(); k++) {
            if (sig.get(k).text.equals("(")) d++;
            if (sig.get(k).text.equals(")") && --d == 0) return k;
        }
        throw new IllegalArgumentException("unbalanced brackets");
    }

    private static List<List<Token>> arguments(List<Token> sig, int from, int close) {
        List<List<Token>> args = new ArrayList<>();
        List<Token> cur = new ArrayList<>();
        int d = 0;
        for (int k = from; k < close; k++) {
            String s = sig.get(k).text;
            if (s.equals("(") || s.equals("[")) d++;
            if (s.equals(")") || s.equals("]")) d--;
            if (d == 0 && s.equals(",")) {
                args.add(cur);
                cur = new ArrayList<>();
            } else {
                cur.add(sig.get(k));
            }
        }
        if (!cur.isEmpty()) args.add(cur);
        return args;
    }

    private static String join(List<Token> sig) {
        StringBuilder b = new StringBuilder();
        for (Token t : sig) {
            if (t.kind.equals("pre")) b.append('\n').append(t.text.strip()).append('\n');
            else b.append(t.text).append(' ');
        }
        return b.toString();
    }
}