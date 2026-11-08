package dev.mirothe.vessel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    static Object parse(String text) {
        Json j = new Json(text);
        Object v = j.value();
        j.skip();
        if (j.i != j.s.length()) throw j.error("unexpected text");
        return v;
    }

    private RuntimeException error(String what) {
        int line = 1;
        for (int k = 0; k < Math.min(i, s.length()); k++) if (s.charAt(k) == '\n') line++;
        return new IllegalArgumentException("project.json line " + line + ": " + what);
    }

    private void skip() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private Object value() {
        skip();
        if (i >= s.length()) throw error("unexpected end");
        char c = s.charAt(i);
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (s.startsWith("true", i)) { i += 4; return true; }
        if (s.startsWith("false", i)) { i += 5; return false; }
        if (s.startsWith("null", i)) { i += 4; return null; }
        return number();
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        skip();
        if (s.charAt(i) == '}') { i++; return m; }
        while (true) {
            skip();
            String k = string();
            skip();
            if (s.charAt(i++) != ':') throw error("expected ':'");
            m.put(k, value());
            skip();
            char c = s.charAt(i++);
            if (c == '}') return m;
            if (c != ',') throw error("expected ',' or '}'");
        }
    }

    private List<Object> array() {
        List<Object> a = new ArrayList<>();
        i++;
        skip();
        if (s.charAt(i) == ']') { i++; return a; }
        while (true) {
            a.add(value());
            skip();
            char c = s.charAt(i++);
            if (c == ']') return a;
            if (c != ',') throw error("expected ',' or ']'");
        }
    }

    private String string() {
        if (s.charAt(i) != '"') throw error("expected a string");
        StringBuilder b = new StringBuilder();
        i++;
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> b.append('\n');
                    case 't' -> b.append('\t');
                    case 'r' -> b.append('\r');
                    case 'u' -> { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                    default -> b.append(e);
                }
            } else {
                b.append(c);
            }
        }
    }

    private Number number() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (start == i) throw error("unexpected character '" + s.charAt(i) + "'");
        String n = s.substring(start, i);
        if (n.contains(".") || n.contains("e") || n.contains("E")) return Double.parseDouble(n);
        return Long.parseLong(n);
    }

    static String write(Object v) {
        StringBuilder b = new StringBuilder();
        write(v, b, 0);
        return b.toString();
    }

    private static void write(Object v, StringBuilder b, int depth) {
        String pad = "  ".repeat(depth + 1);
        if (v instanceof Map<?, ?> m) {
            b.append("{\n");
            int n = 0;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                b.append(pad).append(quote(e.getKey().toString())).append(": ");
                write(e.getValue(), b, depth + 1);
                if (++n < m.size()) b.append(',');
                b.append('\n');
            }
            b.append("  ".repeat(depth)).append('}');
        } else if (v instanceof List<?> l) {
            b.append('[');
            for (int k = 0; k < l.size(); k++) {
                if (k > 0) b.append(", ");
                write(l.get(k), b, depth + 1);
            }
            b.append(']');
        } else if (v instanceof String str) {
            b.append(quote(str));
        } else {
            b.append(v);
        }
    }

    private static String quote(String str) {
        return '"' + str.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
