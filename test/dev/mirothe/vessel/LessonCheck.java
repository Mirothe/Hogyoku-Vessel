package dev.mirothe.vessel;

// every check should stay unticked on the starter code, tick for its own example, and accept the common rewrites
public final class LessonCheck {
    static int bad;

    public static void main(String[] args) {
        for (Learn.Lesson l : Learn.LESSONS) {
            String sofar = l.starter();
            for (int i = 0; i < l.steps().size(); i++) {
                Learn.Step s = l.steps().get(i);
                String where = l.id() + " step " + (i + 1) + " \"" + s.task() + "\"";
                if (s.done().test(l.starter()) && !s.task().startsWith("Change the")) fail(where + " ticks on the untouched starter");
                sofar += "\n" + s.example();
                if (!s.done().test(sofar) && !s.done().test(s.example())) fail(where + " doesn't tick for its own example");
            }
        }
        Learn.Lesson sun = lesson("lesson_sun");
        expect(sun, 0, "float sun = max(dot(normalize(dir), sunDirection), 0.0);", true);
        expect(sun, 0, "float s = max(dot(sun_dir, dir), 0.0);", true);
        expect(sun, 4, "vec3 sunDirection = normalize(vec3(0.0, sin(time), -1.0));", true);
        expect(lesson("lesson_first_sky"), 4, "", false);
        expect(lesson("lesson_first_sky"), 2, "float up = clamp(0.5 + dir.y, 0.0, 1.0);", true);
        expect(lesson("lesson_palettes"), 1, "vec3 pal(float x) { return cos(vec3(x)); }\nvec3 color = pal(t);", true);
        expect(lesson("lesson_shapes"), 2, "float blend(float a, float b, float k) { return min(a, b) - k; }", true);
        expect(lesson("lesson_lighting"), 1, "vec3 color = vec3(0.8) * (0.2 + diffuse);", true);
        // a comment can't tick a step
        expect(sun, 4, "// sunDir = time", false);
        System.out.println(bad == 0 ? "all lesson checks behave" : bad + " problems");
        System.exit(bad == 0 ? 0 : 1);
    }

    static Learn.Lesson lesson(String id) {
        return Learn.LESSONS.stream().filter(l -> l.id().equals(id)).findFirst().orElseThrow();
    }

    static void expect(Learn.Lesson l, int step, String extra, boolean want) {
        boolean got = l.steps().get(step).done().test(l.starter() + "\n" + extra);
        if (got != want) fail(l.id() + " step " + (step + 1) + " gave " + got + " for: " + extra.replace('\n', ' '));
    }

    static void fail(String s) {
        bad++;
        System.out.println("FAIL " + s);
    }
}
