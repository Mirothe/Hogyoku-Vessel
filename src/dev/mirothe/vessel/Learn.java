package dev.mirothe.vessel;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// the guides and the interactive lessons. a lesson is starter code plus tasks that get ticked off
// as soon as the code in the editor does what the task asks
final class Learn {
    record Step(String task, String hint, String example, Predicate<String> done) {
    }

    record Lesson(String id, String title, String blurb, int minutes, String theory, String starter, List<Step> steps) {
    }

    record Guide(String title, String blurb, String html) {
    }

    private static final Preferences PREFS = Preferences.userNodeForPackage(Learn.class);

    private Learn() {
    }

    static boolean finished(Lesson lesson) {
        return PREFS.getBoolean("done." + lesson.id(), false);
    }

    static void markFinished(Lesson lesson) {
        PREFS.putBoolean("done." + lesson.id(), true);
    }

    static int finishedCount() {
        return (int) LESSONS.stream().filter(Learn::finished).count();
    }

    // the checks only look at code, so a task can't be "done" by writing the answer in a comment
    static String code(String src) {
        return src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }

    private static Predicate<String> has(String regex) {
        Pattern p = Pattern.compile(regex);
        return src -> p.matcher(code(src)).find();
    }

    private static Predicate<String> count(String regex, int atLeast) {
        Pattern p = Pattern.compile(regex);
        return src -> {
            Matcher m = p.matcher(code(src));
            int n = 0;
            while (m.find()) n++;
            return n >= atLeast;
        };
    }

    // the parameter itself doesn't count, the task wants the name used somewhere in the code
    private static Predicate<String> uses(String name) {
        Pattern declared = Pattern.compile("\\bfloat\\s+" + name + "\\b");
        Pattern used = Pattern.compile("\\b" + name + "\\b");
        return src -> used.matcher(declared.matcher(code(src)).replaceAll("")).find();
    }

    private static Predicate<String> lacks(String text) {
        return src -> !code(src).contains(text);
    }

    private static Step step(String task, String hint, String example, Predicate<String> done) {
        return new Step(task, hint, example, done);
    }

    static final List<Lesson> LESSONS = List.of(
            new Lesson("lesson_basics", "GLSL basics", "semicolons, floats, vectors and functions", 6, """
                    <p>Shaders are written in <b>GLSL</b>. It looks a lot like C or Java, and it's strict: every line that does
                    something ends with a <code>;</code>, and when one is missing the whole shader stops working. The error
                    shows up under the preview with the line it's on.</p>
                    <p>Numbers come in two kinds. <code>1</code> is a whole number (an int) and <code>1.0</code> is a decimal
                    (a float). Shaders almost always want floats, and GLSL won't quietly turn one into the other:
                    <code>float x = 1;</code> is an error, and <code>1 / 2</code> is 0 because it's whole number division.
                    Writing <code>1.0</code> and <code>0.5</code> avoids both problems.</p>
                    <p><code>vec2</code>, <code>vec3</code> and <code>vec4</code> hold 2, 3 or 4 floats. You can pick parts out of
                    them by name, <code>.x .y .z .w</code> or <code>.r .g .b .a</code>, and even reorder them:
                    <code>color.bgr</code> is the same color with red and blue swapped. That's called <b>swizzling</b>.</p>
                    <p>A <b>function</b> takes some values and gives one back. <code>sky</code> is a function, and you can write
                    your own above it to keep things tidy.</p>
                    <h2>Reading an error</h2>
                    <p>When the code breaks, the line under the preview names a line number and a short message. The message often points at the line after the real mistake: a missing <code>;</code> is only noticed when GLSL reaches the next line and
                    can't make sense of the two together. So check the named line and the one above it.</p>
                    <table>
                    <tr><td><code>syntax error</code></td><td>a missing <code>;</code>, bracket or comma</td></tr>
                    <tr><td><code>undeclared identifier</code></td><td>a name spelled differently from where it was made, or used before it's made</td></tr>
                    <tr><td><code>cannot convert</code></td><td>mixing an int with a float, or a vec2 with a vec3</td></tr>
                    <tr><td><code>no matching function</code></td><td>a function given the wrong number or types of values</td></tr>
                    </table>
                    <h2>The types you'll use</h2>
                    <table>
                    <tr><td><code>float</code></td><td>a decimal number, like <code>0.5</code></td></tr>
                    <tr><td><code>int</code></td><td>a whole number, mostly for loop counters</td></tr>
                    <tr><td><code>bool</code></td><td><code>true</code> or <code>false</code></td></tr>
                    <tr><td><code>vec2 vec3 vec4</code></td><td>2, 3 or 4 floats: positions, directions and colors</td></tr>
                    </table>
                    <h2>Maths on whole vectors</h2>
                    <p>Most maths works on every part of a vector at once. <code>vec3(1.0, 0.5, 0.2) * 2.0</code> doubles all three parts,
                    and adding two vec3s adds them part by part. Functions like <code>sin</code>, <code>max</code> and <code>mix</code>
                    work part by part too. Both sides have to be the same size: you can't add a vec2 to a vec3.</p>
                    <h2>Try this</h2>
                    <ul>
                    <li>Delete a <code>;</code> on purpose and read the message it gives.</li>
                    <li>Return <code>dir</code> itself as the color. Every direction gets its own color, which is a quick way to see what a value looks like.</li>
                    <li>Return <code>vec3(dir.y)</code> to see height as gray, black at the horizon and white overhead.</li>
                    </ul>
                    """, """
                    // glsl basics
                    // this sky has a mistake in it on purpose, the error under the preview says which line.
                    // fix that first, then work through the tasks

                    vec3 sky(vec3 dir, float time) {
                        float height = dir.y
                        vec3 color = vec3(0.2, 0.4, 0.8);
                        return color;
                    }
                    """, List.of(
                    step("Fix the missing semicolon", "One line is missing its ; at the end, so GLSL reads it and the next line as one broken line.",
                            "float height = dir.y;", has("float\\s+height\\s*=[^;\\n]*;")),
                    step("Use the variable", "height is worked out but never used. Make the color brighter the higher you look by multiplying with it.",
                            "vec3 color = vec3(0.2, 0.4, 0.8) * (0.5 + height);", count("\\bheight\\b", 2)),
                    step("Make a float of your own", "Make a variable that holds a decimal number and use it to change the color. Remember the .0, a plain 2 won't work for a float.",
                            "float brightness = 1.5;\nreturn color * brightness;", has("float\\s+\\w+\\s*=\\s*(\\d+\\.\\d*|\\.\\d+)\\s*;")),
                    step("Swizzle a vector", "Pick the parts of color in a different order, like .bgr or .grb, and return that. The sky changes color.",
                            "return color.bgr * brightness;", has("\\w\\.([rgba]{2,4}|[xyzw]{2,4})\\b")),
                    step("Write your own function", "Above sky, write a small function that takes a color and gives a changed one back, then use it in the return.",
                            "vec3 warm(vec3 c) {\n    return c * vec3(1.2, 0.9, 0.7);\n}\n\n// and in sky():\nreturn warm(color.bgr * brightness);",
                            has("(vec[234]|float)\\s+(?!sky\\b)\\w+\\s*\\([^)]*\\)\\s*\\{")))),

            new Lesson("lesson_first_sky", "Your first sky", "colors, mix and the horizon", 6, """
                    <p>Everything in a sky effect happens inside one function, <code>sky</code>. Minecraft calls it once for
                    every pixel of the sky, every frame, and paints that pixel whatever color you give back.</p>
                    <p>The only thing that changes from pixel to pixel is <code>dir</code>, the direction that pixel looks in.
                    It's a vector of length 1, so <code>dir.y</code> is 1 when you look straight up, 0 at the horizon and -1
                    straight down. <code>dir.y</code> alone is enough for a simple sky.</p>
                    <p>Colors are <code>vec3(red, green, blue)</code> with each part from 0 to 1. <code>mix(a, b, t)</code> blends
                    two of them: all <code>a</code> when t is 0, all <code>b</code> when t is 1.</p>
                    <h2>Why one number is enough</h2>
                    <p>The sky is the inside of a huge ball around you. Every pixel is one point on that ball, and
                    <code>dir</code> is the arrow from you to that point. Because the arrow always has length 1, its <code>y</code> part
                    says exactly how high up the point is, no matter which way you face.</p>
                    <h2>What mix really does</h2>
                    <p><code>mix(a, b, t)</code> is <code>a * (1.0 - t) + b * t</code>. At t = 0.5 you get the color halfway between. If t
                    goes outside 0 to 1, mix keeps going past the colors you gave it, which is where the odd colors under the horizon
                    come from. <code>max</code> or <code>clamp</code> keeps t in range.</p>
                    <h2>Shaping the gradient</h2>
                    <p>A straight fade looks flat. Bend it before mixing:</p>
                    <table>
                    <tr><td><code>pow(up, 0.5)</code></td><td>the top color reaches down further</td></tr>
                    <tr><td><code>pow(up, 2.0)</code></td><td>the horizon color climbs higher</td></tr>
                    <tr><td><code>smoothstep(0.0, 0.3, up)</code></td><td>a quick band at the horizon, then even color above</td></tr>
                    </table>
                    <p>Try each one on the lesson's gradient and compare.</p>
                    <h2>Common mistakes</h2>
                    <ul>
                    <li>Colors above 1.0 all show as the same white, so a bright mix loses its detail.</li>
                    <li><code>float up = 1;</code> is an error, write <code>1.0</code>.</li>
                    <li>Without its own ground color, everything below the horizon is just the horizon color again.</li>
                    </ul>
                    <h2>Try this</h2>
                    <ul>
                    <li>Use two mixes for three colors: one from the horizon to the middle, one from the middle to the top.</li>
                    <li>Make a night sky with a faint glow along the horizon.</li>
                    </ul>
                    """, """
                    // your first sky
                    // sky() runs once for every pixel of the sky. dir is the direction that pixel looks in,
                    // so dir.y is 1 straight up, 0 at the horizon and -1 straight down. time counts seconds.
                    // whatever color you return is what that pixel shows

                    vec3 sky(vec3 dir, float time) {
                        return vec3(0.1, 0.2, 0.4);
                    }
                    """, List.of(
                    step("Change the color", "Every number in vec3 is red, green and blue from 0 to 1. Try a warm orange.",
                            "return vec3(1.0, 0.5, 0.2);", lacks("vec3(0.1, 0.2, 0.4)")),
                    step("Fade between two colors with mix", "Use dir.y as the mix amount, so the first color sits on the horizon and the second straight up.",
                            "return mix(vec3(1.0, 0.6, 0.3), vec3(0.1, 0.2, 0.5), dir.y);", has("mix\\s*\\(").and(has("dir\\.y"))),
                    step("Keep the mix from going below zero", "Below the horizon dir.y is negative, and mix with a negative amount pushes colors past the first one into odd shades. max(dir.y, 0.0) stops at zero.",
                            "float up = max(dir.y, 0.0);\nreturn mix(vec3(1.0, 0.6, 0.3), vec3(0.1, 0.2, 0.5), up);", has("(max|clamp)\\s*\\([^;]*dir\\.y")),
                    step("Give the ground its own color", "Check dir.y and send back a different color for everything below the horizon.",
                            "if (dir.y < 0.0) return vec3(0.12, 0.09, 0.08);", has("dir\\.y\\s*<|dir\\.y\\s*<=").or(count("mix\\s*\\(", 2))),
                    step("Make it breathe with time", "sin(time) swings from -1 to 1 and back every few seconds. Add a little of it to the mix amount.",
                            "float up = max(dir.y + sin(time) * 0.1, 0.0);", uses("time")))),

            new Lesson("lesson_sun", "A sun", "dot, pow and glow", 8, """
                    <p><code>dot(a, b)</code> of two directions tells you how much they agree: 1 when they point the same way,
                    0 when they're at a right angle, -1 when they're opposite. Take the dot of <code>dir</code> with the
                    direction of the sun and you get a value that's 1 right on the sun and falls off around it.</p>
                    <p>That falloff is far too wide to look like a sun, so you sharpen it with <code>pow</code>. Raising a number
                    a bit under 1 to a big power crushes it toward 0, and only the pixels almost exactly on the sun survive.
                    A big power gives a small disc and a small power gives a wide glow.</p>
                    <p>Light adds up, so suns, glows and halos are usually added on top of the sky with <code>+=</code>.</p>
                    <h2>Directions must have length 1</h2>
                    <p><code>dot</code> only means "how much two directions agree" when both have length 1, so the sun's direction goes through <code>normalize</code> first. In the preview, <code>-z</code> is straight ahead and <code>y</code> is up, so
                    <code>vec3(0.0, 0.3, -1.0)</code> is ahead and a little above the horizon.</p>
                    <h2>How big is the sun?</h2>
                    <p>The power decides the size. <code>pow(sun, 500.0)</code> is still half bright about 3 degrees from the center, which
                    reads as a big soft sun. The real sun is half a degree across, which would be a power near 20000. For a disc with a
                    hard edge instead of a soft one, use <code>smoothstep(0.9995, 0.9998, sun)</code>.</p>
                    <h2>Light stacks up</h2>
                    <p>A convincing sun is several layers added together: a small bright disc, a halo around it and a wide warm glow. Each
                    layer is dimmer and wider than the last. Adding them can push colors past 1.0, which lesson 7 fixes with tone
                    mapping.</p>
                    <h2>A sky that reacts</h2>
                    <p><code>sunDir.y</code> says how high the sun is, so the rest of the sky can follow it, getting darker and warmer as the sun goes down, with stars once it's gone. Mixing the sky color toward orange by how low the sun is makes a simple sunset in one line.</p>
                    <h2>Common mistakes</h2>
                    <ul>
                    <li>Behind you, <code>dot</code> is negative, and <code>pow</code> of a negative number is undefined, which shows up as
                    black or flickering pixels. Keeping it above zero with <code>max</code> prevents that.</li>
                    <li>Forgetting <code>normalize</code> makes the sun the wrong size, or missing completely.</li>
                    </ul>
                    <h2>Try this</h2>
                    <ul>
                    <li>Swap the soft disc for a hard edged one with smoothstep.</li>
                    <li>Tint the sun itself red when it's close to the horizon.</li>
                    </ul>
                    """, """
                    // a sun
                    // dot(a, b) of two directions is 1 when they point the same way and gets smaller as they turn apart.
                    // a sun is just the pixels whose dir is very close to the sun's direction

                    vec3 sky(vec3 dir, float time) {
                        vec3 sunDir = normalize(vec3(0.0, 0.3, -1.0));
                        vec3 color = mix(vec3(0.9, 0.5, 0.3), vec3(0.1, 0.15, 0.4), max(dir.y, 0.0));
                        return color;
                    }
                    """, List.of(
                    step("Measure how close each pixel is to the sun", "dot can go negative on the far side of the sky, so wrap it in max(..., 0.0).",
                            "float sun = max(dot(dir, sunDir), 0.0);", has("(?i)dot\\s*\\([^;]*(\\bdir\\b[^;]*sun|sun[^;]*\\bdir\\b)")),
                    step("Turn it into a small disc with pow", "pow(sun, 500.0) is almost 0 everywhere except right on the sun. Add it to color.",
                            "color += vec3(1.0, 0.95, 0.85) * pow(sun, 500.0);", has("pow\\s*\\(")),
                    step("Add a soft glow around it", "A second pow with a small power spreads out wide. Keep it dim so it doesn't wash the sky out.",
                            "color += vec3(1.0, 0.6, 0.3) * pow(sun, 8.0) * 0.5;", count("pow\\s*\\(", 2)),
                    step("Warm up the sky near the sun", "A third, even wider glow tinted orange makes the air around the sun look lit.",
                            "color += vec3(1.0, 0.5, 0.2) * pow(sun, 2.0) * 0.25;", count("pow\\s*\\(", 3)),
                    step("Let it rise and set", "Put time into sunDir, so its height swings up and down.",
                            "vec3 sunDir = normalize(vec3(0.0, sin(time * 0.2) * 0.4, -1.0));", has("(?i)sun\\w*\\s*=[^;]*\\btime\\b")),
                    step("Darken the sky when the sun is low", "sunDir.y tells you how high the sun is. Scale the sky color by it on the line right after color is made, before any of the sun is added, so the sun itself stays bright.",
                            "vec3 color = mix(vec3(0.9, 0.5, 0.3), vec3(0.1, 0.15, 0.4), max(dir.y, 0.0));\ncolor *= 0.25 + 0.75 * clamp(sunDir.y * 3.0, 0.0, 1.0);", has("(?i)sun\\w*\\.y")))),

            new Lesson("lesson_clouds", "Noise clouds", "noise, octaves and a cloud ceiling", 12, """
                    <p>Random numbers per pixel only give you static. <b>Value noise</b> picks random numbers at the corners of a
                    grid and blends smoothly between them, so neighbouring pixels get similar values.</p>
                    <p>One layer of noise looks like blurry blobs. Add a second layer at twice the detail and half the
                    strength, then a third, and so on. That stack is called <b>fbm</b>, and it's the usual starting point for clouds and smoke. Each layer is one <b>octave</b>.</p>
                    <p>Real clouds sit on a ceiling above you, so near the horizon you see a lot of them squashed together.
                    Dividing <code>dir.xz</code> by <code>dir.y</code> gives you exactly that: positions on a flat ceiling.</p>
                    <h2>Inside the noise</h2>
                    <p><code>hash</code> turns a position into a number that looks random: a sine wave squeezed so hard that its digits
                    jump around. <code>noise</code> asks hash for a value at the eight corners of the grid cube around the point, then
                    blends them with the curve <code>f * f * (3.0 - 2.0 * f)</code>, which eases in and out so there are no creases along
                    the grid lines.</p>
                    <h2>Scale and octaves</h2>
                    <p>Multiplying the position zooms the noise: <code>noise(p * 8.0)</code> has smaller blobs than
                    <code>noise(p * 2.0)</code>. Each octave doubles the scale and halves the strength, so big shapes stay big and the
                    small ones only add detail. Five octaves add up to almost 1.0. More octaves mean more detail but cost more, and four
                    to six is plenty for a sky.</p>
                    <h2>Why the ceiling trick works</h2>
                    <p>Near the horizon <code>dir.y</code> is tiny, so <code>dir.xz / dir.y</code> becomes huge. Clouds far away get
                    squashed together like a real cloud layer seen from the ground. Right at the horizon they squash so much that they
                    flicker, which is why the lesson fades them out there. <code>max(dir.y, 0.05)</code> stops a division by zero.</p>
                    <h2>Coverage and softness</h2>
                    <p>In <code>smoothstep(a, b, n)</code>, raising <code>a</code> means fewer clouds, and moving <code>a</code> and
                    <code>b</code> closer gives harder edges. For a lit look, look the noise up a second time a little toward the sun:
                    where the second value is lower, the cloud's edge faces the light.</p>
                    <h2>Common mistakes</h2>
                    <ul>
                    <li>No horizon fade leaves a shimmering line where the clouds meet the ground.</li>
                    <li>Adding time inside the loop makes every octave drift at a different speed. Sometimes that looks good, but it's easy to do by accident.</li>
                    </ul>
                    <h2>Try this</h2>
                    <ul>
                    <li>Two cloud layers at different heights drifting at different speeds.</li>
                    <li>Gray, heavy storm clouds with almost no sky between them.</li>
                    </ul>
                    """, """
                    // noise clouds
                    // noise() gives smooth random values between 0 and 1. adding a few layers of it, each one twice as
                    // detailed and half as strong, is how almost every cloud, smoke and gas effect is made

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

                    vec3 sky(vec3 dir, float time) {
                        vec3 blue = mix(vec3(0.5, 0.7, 0.9), vec3(0.15, 0.35, 0.75), max(dir.y, 0.0));
                        return blue;
                    }
                    """, List.of(
                    step("Use noise for the color", "Feed noise a scaled direction and blend toward white with the result.",
                            "float n = noise(dir * 4.0);\nreturn mix(blue, vec3(1.0), n);", count("\\bnoise\\s*\\(", 2)),
                    step("Stack a few octaves in a loop", "Each pass adds noise, doubles the scale and halves the strength. This replaces the single noise line, the return stays the same.",
                            "float n = 0.0;\nfloat amp = 0.5;\nvec3 p = dir * 3.0;\nfor (int i = 0; i < 5; i++) {\n    n += noise(p) * amp;\n    p *= 2.0;\n    amp *= 0.5;\n}\nreturn mix(blue, vec3(1.0), n);",
                            has("for\\s*\\(")),
                    step("Put the clouds on a ceiling", "Divide dir.xz by dir.y (kept above a small number so it never divides by zero) and use that as the noise position, in place of vec3 p = dir * 3.0.",
                            "vec2 ceiling = dir.xz / max(dir.y, 0.05);\nvec3 p = vec3(ceiling, 0.0);", has("dir\\.xz\\s*/")),
                    step("Let the clouds drift", "Add time to the position before the loop so the whole layer slides along.",
                            "vec3 p = vec3(ceiling + vec2(time * 0.1, 0.0), 0.0);", uses("time")),
                    step("Cut out cloud shapes", "smoothstep(0.45, 0.7, n) turns the soft noise into clouds with clear gaps of sky between them.",
                            "float cover = smoothstep(0.45, 0.7, n);\nreturn mix(blue, vec3(1.0), cover);", has("smoothstep\\s*\\(")),
                    step("Fade them out at the horizon", "A second smoothstep on dir.y hides the squashed clouds right at the edge.",
                            "float cover = smoothstep(0.45, 0.7, n);\ncover *= smoothstep(0.0, 0.25, dir.y);\nreturn mix(blue, vec3(1.0), cover);", has("smoothstep\\s*\\([^;]*dir\\.y")))),

            new Lesson("lesson_stars", "Stars", "cells, floor and fract", 10, """
                    <p>Stars are a different kind of random: a few sharp points scattered across the sky. The trick is to cut
                    space into <b>cells</b>. <code>floor(dir * 80.0)</code> gives every little patch of sky its own whole number
                    id, and <code>fract(dir * 80.0)</code> tells you where inside that patch the pixel sits.</p>
                    <p>Run the id through a <b>hash</b>, a function that turns any input into a random looking number between
                    0 and 1, and only light the cells whose number lands above 0.97. About 3 cells in 100 get a star.</p>
                    <p>The same hash is handy for anything that should differ star by star: brightness, color, twinkle speed.</p>
                    <h2>Why cells?</h2>
                    <p>Every pixel runs on its own and can't see the others. Checking a list of a thousand stars for every pixel would be
                    far too slow. Cells solve it: each pixel works out which cell it's in, asks that one cell whether it has a star, and
                    is done.</p>
                    <h2>Size and density</h2>
                    <p>The number in <code>floor(dir * 80.0)</code> sets how big the cells are. A bigger number means smaller cells, so
                    smaller and more numerous stars. The threshold sets how many cells get a star: 0.97 lights about 3 in 100, 0.99
                    about 1 in 100.</p>
                    <h2>Hiding the grid</h2>
                    <p>A star always sitting in the middle of its cell lines up in rows you can spot. Move it to a random spot inside the
                    cell with three more hashes:</p>
                    <pre>vec3 jitter = vec3(hash(cell + 1.0),
                        hash(cell + 2.0), hash(cell + 3.0)) - 0.5;</pre>
                    <p>Then measure the distance from the cell's middle plus <code>jitter * 0.6</code> instead of from the middle
                    itself.</p>
                    <h2>One hash per property</h2>
                    <p>Use a different offset for each thing a star needs: whether it exists, how bright it is, its color, how fast it
                    twinkles. Reusing the same hash links them, so for example only the brightest cells would ever get stars.</p>
                    <h2>Common mistakes</h2>
                    <ul>
                    <li>Stars that look like a grid: add jitter.</li>
                    <li>Stars that blink together: each one needs its own phase from the hash.</li>
                    </ul>
                    <h2>Try this</h2>
                    <ul>
                    <li>A milky way: multiply the chance of a star by a band, like <code>exp(-abs(dir.z) * 4.0)</code>.</li>
                    <li>Hide the stars wherever a sun from lesson 3 would be up.</li>
                    </ul>
                    """, """
                    // stars
                    // floor() cuts space into numbered cells and fract() tells you where you are inside one.
                    // give every cell a random number and only some of them get a star

                    float hash(vec3 p) {
                        return fract(sin(dot(p, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
                    }

                    vec3 sky(vec3 dir, float time) {
                        vec3 night = vec3(0.01, 0.01, 0.04);
                        return night;
                    }
                    """, List.of(
                    step("Split the sky into cells", "floor gives every patch of sky its own id.", "vec3 cell = floor(dir * 80.0);", has("floor\\s*\\(")),
                    step("Pick a few cells to hold a star", "step(0.97, x) is 1 only when x is above 0.97. Add the star onto night in the return.",
                            "vec3 cell = floor(dir * 80.0);\nfloat star = step(0.97, hash(cell));\nreturn night + vec3(star);", has("step\\s*\\(|>\\s*0\\.9")),
                    step("Make them round", "Distance from the middle of the cell, run through smoothstep, gives a soft dot instead of a square. It has to change star before the return uses it.",
                            "float star = step(0.97, hash(cell));\nstar *= 1.0 - smoothstep(0.0, 0.5, length(fract(dir * 80.0) - 0.5));\nreturn night + vec3(star);", has("fract\\s*\\(").and(has("length\\s*\\("))),
                    step("Make them twinkle", "sin with a different speed per star, picked by the hash, so they don't blink together. Put it on the line before the return.",
                            "star *= 0.6 + 0.4 * sin(time * 3.0 + hash(cell) * 50.0);\nreturn night + vec3(star);", has("sin\\s*\\(").and(uses("time"))),
                    step("Give each star its own color", "Hash the cell again with an offset and use it to pick between blue white and warm white, then multiply the star by that tint in the return.",
                            "vec3 tint = mix(vec3(0.7, 0.8, 1.0), vec3(1.0, 0.85, 0.7), hash(cell + 7.0));\nreturn night + tint * star;", count("\\bhash\\s*\\(", 3)),
                    step("Add a second, finer layer", "Do it all again at a higher cell count and half the brightness for faint background stars, and add them in the return too.",
                            "vec3 fine = floor(dir * 200.0);\nfloat faint = step(0.98, hash(fine)) * (1.0 - smoothstep(0.0, 0.5, length(fract(dir * 200.0) - 0.5)));\nreturn night + tint * star + vec3(faint * 0.5);", count("floor\\s*\\(", 2)))),

            new Lesson("lesson_warp", "Domain warping", "noise that bends itself", 12, """
                    <p>fbm on its own looks like fog. To get swirls and gas that looks like it's flowing, you
                    <b>warp</b> the position before you look up the noise. You compute a few fbm values first, use them as an
                    offset, and feed the shifted position into fbm again.</p>
                    <p>Because the offset itself is smooth noise, the result stays smooth but gets twisted into shapes that
                    plain noise never makes. Animate the offset and the shapes start to churn.</p>
                    <h2>What the offset does</h2>
                    <p>Warping moves the point before the noise is looked up, so the pattern gets dragged along with it. How far it moves
                    decides the look:</p>
                    <table>
                    <tr><td><code>warp * 0.3</code></td><td>a gentle wobble, like heat haze</td></tr>
                    <tr><td><code>warp * 1.5</code></td><td>proper swirls and folds</td></tr>
                    <tr><td><code>warp * 4.0</code></td><td>chaotic, almost marbled</td></tr>
                    </table>
                    <h2>Three different offsets</h2>
                    <p>Each axis of the warp gets its own fbm with its own shift, like <code>p + vec3(5.2, 1.3, 2.8)</code>. If all three
                    used the same position they'd always be equal, and the pattern would just slide along one diagonal.</p>
                    <h2>Going two levels deep</h2>
                    <p>You can warp the warp: build a first offset, use it to build a second, and feed the second into the final lookup.
                    Each level adds another layer of folding, and two levels is usually enough for a smoky, folded look.</p>
                    <h2>Color from the warp</h2>
                    <p><code>length(warp)</code> is large where the space was pushed hardest, and mixing
                    colors by it lights up the edges of the swirls, which reads as veins or filaments.</p>
                    <h2>Cost</h2>
                    <p>Every fbm is five noise calls, and every noise call is eight hashes. Three fbms for the warp plus the final one is
                    160 hashes per pixel. That's fine for a sky, but it adds up fast if you go two levels deep, so give the warp fewer
                    octaves than the final lookup.</p>
                    <h2>Common mistakes</h2>
                    <ul>
                    <li>Animating everything turns it to mush. Move only the warp and let the final pattern follow.</li>
                    <li>A huge strength makes the pattern noisy rather than swirly.</li>
                    </ul>
                    <h2>Try this</h2>
                    <ul>
                    <li>Marble: warp a pattern of stripes, <code>sin(p.x * 10.0)</code>, instead of fbm.</li>
                    <li>Color by <code>length(warp)</code> and see the veins appear.</li>
                    </ul>
                    """, """
                    // domain warping
                    // fbm here is the same octave loop from the clouds lesson, already written for you

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

                    float fbm(vec3 p) {
                        float sum = 0.0;
                        float amp = 0.5;
                        for (int i = 0; i < 5; i++) {
                            sum += noise(p) * amp;
                            p = p * 2.03 + vec3(1.7, 9.2, 4.1);
                            amp *= 0.5;
                        }
                        return sum;
                    }

                    vec3 sky(vec3 dir, float time) {
                        vec3 p = dir * 2.5;
                        float n = fbm(p);
                        return vec3(n);
                    }
                    """, List.of(
                    step("Build a warp offset out of noise", "Three fbm calls with different offsets, one for each axis.",
                            "vec3 warp = vec3(fbm(p), fbm(p + vec3(5.2, 1.3, 2.8)), fbm(p + vec3(2.1, 7.7, 3.3)));", count("\\bfbm\\s*\\(", 4)),
                    step("Feed the warp back in", "Shift the position by the warp before the final fbm. A bigger multiplier twists it harder.",
                            "float n = fbm(p + warp * 1.6);", has("fbm\\s*\\([^;]*warp")),
                    step("Make it churn", "Add time to the position used for the warp so the swirls move. Make a moving copy of p and build the warp from that.",
                            "vec3 q = p + vec3(0.0, time * 0.05, 0.0);\nvec3 warp = vec3(fbm(q), fbm(q + vec3(5.2, 1.3, 2.8)), fbm(q + vec3(2.1, 7.7, 3.3)));", uses("time")),
                    step("Color it in two tones", "mix a dark color and a bright one using n, and return that instead of vec3(n).",
                            "vec3 color = mix(vec3(0.05, 0.0, 0.1), vec3(0.6, 0.2, 0.8), n);\nreturn color;", has("mix\\s*\\(\\s*vec3")),
                    step("Light up the thickest parts", "pow of n with a high power only lets the brightest spots through. Add them on top in a warm color.",
                            "color += vec3(1.0, 0.7, 0.4) * pow(n, 4.0) * 3.0;\nreturn color;", has("pow\\s*\\(")))),

            new Lesson("lesson_palettes", "Color like a pro", "cosine palettes and tone mapping", 8, """
                    <p>Picking every color by hand gets old. A <b>cosine palette</b> turns one number into a whole smooth range
                    of colors: <code>a + b * cos(6.28318 * (c * t + d))</code>, where <code>a</code>, <code>b</code>,
                    <code>c</code> and <code>d</code> are vec3s. Changing <code>d</code> shifts the hues, which makes it easy
                    to find a set that looks good together.</p>
                    <p>When you add a lot of light, colors go past 1 and clip to flat white. <b>Tone mapping</b> squeezes them
                    back. <code>1.0 - exp(-color)</code> leaves dark values almost alone and bends bright ones smoothly toward
                    1, so highlights keep their shape.</p>
                    <h2>Reading the four vectors</h2>
                    <table>
                    <tr><td><code>a</code></td><td>the middle brightness of each channel</td></tr>
                    <tr><td><code>b</code></td><td>how far each channel swings, the contrast</td></tr>
                    <tr><td><code>c</code></td><td>how many times it cycles as t goes from 0 to 1</td></tr>
                    <tr><td><code>d</code></td><td>where each channel starts its cycle, which sets the hues</td></tr>
                    </table>
                    <h2>Sets to start from</h2>
                    <table>
                    <tr><td>rainbow</td><td><code>d = vec3(0.0, 0.33, 0.67)</code></td></tr>
                    <tr><td>fire</td><td><code>a = vec3(0.5), b = vec3(0.5), c = vec3(1.0, 0.7, 0.4), d = vec3(0.0, 0.15, 0.2)</code></td></tr>
                    <tr><td>ice</td><td><code>d = vec3(0.5, 0.6, 0.7)</code> with <code>b = vec3(0.3)</code></td></tr>
                    </table>
                    <p>Change one vector at a time to see what each one does.</p>
                    <h2>Why tone mapping</h2>
                    <p>A screen can only show 0 to 1, but light in the real world just adds up. When you stack suns, glows and stars,
                    values pass 1.0 and every bright part becomes the same flat white. <code>1.0 - exp(-color * k)</code> bends the top
                    end so bright values approach 1 without reaching it. The <code>k</code> works like exposure: bigger is brighter.</p>
                    <h2>Brighter blends</h2>
                    <p>Mixing two colors straight gives a slightly dark middle. Mixing their squares and taking the square root,
                    <code>sqrt(mix(a * a, b * b, t))</code>, keeps the middle bright, which helps sunsets and gradients.</p>
                    <h2>Common mistakes</h2>
                    <ul>
                    <li>Adding more light after tone mapping brings the clipping back. Tone map last.</li>
                    <li>A big <code>c</code> makes the colors cycle so fast they look like stripes.</li>
                    </ul>
                    <h2>Try this</h2>
                    <ul>
                    <li>Color the clouds from lesson 4 with a palette.</li>
                    <li>Try exposures of 0.5, 1.5 and 4.0 and compare.</li>
                    </ul>
                    """, """
                    // color like a pro
                    // this sky is way too bright on purpose, half of it clips straight to white

                    vec3 sky(vec3 dir, float time) {
                        float t = dir.y * 0.5 + 0.5;
                        vec3 color = vec3(t * 2.0, 0.6, 2.0 - t * 2.0) * 1.5;
                        return color;
                    }
                    """, List.of(
                    step("Write a cosine palette function", "Above sky, add a function that takes a float and returns a vec3 using cos.",
                            "vec3 palette(float t) {\n    return vec3(0.5) + vec3(0.5) * cos(6.28318 * (vec3(1.0) * t + vec3(0.0, 0.33, 0.67)));\n}",
                            has("vec3\\s+\\w+\\s*\\(\\s*float").and(has("cos\\s*\\("))),
                    step("Use it to color the sky", "Call your palette with t instead of building the color by hand.",
                            "vec3 color = palette(t) * 1.5;", count("palette\\s*\\(", 2).or(has("color\\s*=\\s*(?!vec3\\b)\\w+\\s*\\([^;]*\\bt\\b"))),
                    step("Animate the hues", "Add time to what you pass in, and the colors cycle around.",
                            "vec3 color = palette(t + time * 0.05) * 1.5;", uses("time")),
                    step("Tone map the result", "Return 1.0 - exp(-color) instead of the raw color and watch the clipping go away.",
                            "return 1.0 - exp(-color * 1.5);", has("exp\\s*\\(\\s*-")))),

            new Lesson("lesson_object", "Your first object", "raymarching inside the item", 10, """
                    <p>An object draws a 3D shape inside the item's box, which goes from -1 to 1 on every axis. For every pixel
                    you get a ray: <code>origin</code> is where it enters the box and <code>dir</code> is where it's heading.</p>
                    <p>To find what the ray hits you use a <b>distance function</b>: give it a point and it says how far that
                    point is from the surface. For a sphere that's just <code>length(p) - radius</code>. You step along the ray
                    by exactly that distance, because nothing can be closer. Near the surface the steps get tiny, and when
                    they're small enough you've hit it. This is called <b>raymarching</b>.</p>
                    <p>Return alpha 1 for solid, 0 for empty space so the world shows through, or anything between for glass and glow.</p>
                    <h2>What a distance function returns</h2>
                    <p>A distance function gives a positive number outside the shape, zero on its surface and a negative number inside.
                    As long as it never says a surface is further away than it really is, stepping forward by that amount can't skip past anything. Raymarching depends on that rule.</p>
                    <h2>The loop, line by line</h2>
                    <ul>
                    <li><code>t</code> is how far along the ray you've walked.</li>
                    <li><code>p = origin + dir * t</code> is where that puts you.</li>
                    <li><code>d</code> is how far the closest surface is from there.</li>
                    <li>If d is tiny you've hit it. If not, walk d further and try again.</li>
                    <li>Once t passes 3.5 you've left the box, so the ray missed.</li>
                    </ul>
                    <h2>Moving, turning and scaling</h2>
                    <p>To move a shape, you move the space it's measured in instead. To put a sphere to the right, measure from a point
                    to the right: <code>length(p - vec3(0.3, 0.0, 0.0)) - 0.2</code>. To turn it, turn <code>p</code> the other way. To
                    scale it, divide p and multiply the result: <code>shape(p / s) * s</code>.</p>
                    <h2>Shapes to know</h2>
                    <table>
                    <tr><td>sphere</td><td><code>length(p) - r</code></td></tr>
                    <tr><td>box</td><td><code>length(max(abs(p) - size, 0.0))</code></td></tr>
                    <tr><td>torus</td><td><code>length(vec2(length(p.xz) - R, p.y)) - r</code></td></tr>
                    <tr><td>capsule</td><td>distance to a line segment, minus the radius</td></tr>
                    </table>
                    <h2>Common mistakes</h2>
                    <ul>
                    <li>Too few steps leaves holes around the edges where rays brush past.</li>
                    <li>A hit threshold that's too big makes shapes look swollen and blobby.</li>
                    <li>A shape bigger than the box gets cut off flat at its walls.</li>
                    <li>Returning a color with alpha 1 when the ray misses draws a black box around everything.</li>
                    </ul>
                    <h2>Try this</h2>
                    <ul>
                    <li>Move the sphere round in a circle with <code>cos(time)</code> and <code>sin(time)</code>.</li>
                    <li>Try the torus formula from the table.</li>
                    </ul>
                    """, """
                    // your first object
                    // the box goes from -1 to 1 on every axis and origin is where the ray enters it.
                    // step along the ray by the distance to the surface until that distance gets tiny

                    vec4 object(vec3 origin, vec3 dir, float time) {
                        float t = 0.0;
                        for (int i = 0; i < 64; i++) {
                            vec3 p = origin + dir * t;
                            float d = length(p) - 0.5;
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
                    """, List.of(
                    step("Change the size of the sphere", "The 0.5 in length(p) - 0.5 is the radius.", "float d = length(p) - 0.3;", lacks("length(p) - 0.5")),
                    step("Turn it into a box", "abs folds every side onto one corner, so one formula covers all six faces.",
                            "float d = length(max(abs(p) - 0.4, 0.0));", has("abs\\s*\\(\\s*p\\s*\\)")),
                    step("Spin it", "Rotate p right after it's worked out and before the distance is measured. Spinning the space spins the shape.",
                            "vec3 p = origin + dir * t;\nfloat a = time;\np.xz = mat2(cos(a), -sin(a), sin(a), cos(a)) * p.xz;", has("mat2\\s*\\(|cos\\s*\\([^;]*\\btime\\b").and(uses("time"))),
                    step("Light it from a second side", "A second dot with another light direction, in another color, adds depth. Work it out next to the first light and add it into the color you return.",
                            "float back = max(dot(n, normalize(vec3(-0.6, 0.2, -0.5))), 0.0);\nreturn vec4(vec3(0.3, 0.6, 1.0) * (0.2 + light) + vec3(1.0, 0.4, 0.2) * back * 0.5, 1.0);", count("\\bdot\\s*\\(", 2)),
                    step("Give it a glow when the ray misses", "Keep the smallest distance the ray ever had. Three lines: one before the loop, one in the loop right after d, and the last return swapped for a soft alpha.",
                            "float closest = 9.0;\nfor (int i = 0; i < 64; i++) {\n    vec3 p = origin + dir * t;\n    float d = length(p) - 0.5;\n    closest = min(closest, d);\n    // ... the rest of the loop stays the same\n}\nreturn vec4(0.3, 0.6, 1.0, exp(-closest * 12.0) * 0.6);",
                            has("min\\s*\\(").and(has("exp\\s*\\("))))),

            new Lesson("lesson_shapes", "Combining shapes", "union, cut and smooth blends", 12, """
                    <p>Distance functions combine with plain math. <code>min(a, b)</code> keeps whichever surface is closer,
                    which glues two shapes together. <code>max(a, -b)</code> keeps <code>a</code> only where you're outside
                    <code>b</code>, which carves <code>b</code> out of it.</p>
                    <p>A plain min leaves a hard crease where shapes meet. A <b>smooth min</b> blends the two distances over a
                    small range, so the shapes melt into each other like clay.</p>
                    <p>Keeping the shape in its own function means the normal and the march both use the same thing.</p>
                    <h2>The three ways to combine</h2>
                    <table>
                    <tr><td><code>min(a, b)</code></td><td>both shapes together</td></tr>
                    <tr><td><code>max(a, b)</code></td><td>only where they overlap</td></tr>
                    <tr><td><code>max(a, -b)</code></td><td>a with b carved out of it</td></tr>
                    </table>
                    <h2>Smooth min, explained</h2>
                    <p><code>k</code> is how wide the blend is, measured in the same units as the shapes. 0.05 rounds the joint like a
                    fillet, 0.3 melts the shapes into one blob. There's a smooth version of carving too: <code>-smin(-a, b, k)</code>
                    cuts with a soft, rounded edge.</p>
                    <h2>Repeating a shape</h2>
                    <p>Folding space repeats whatever you measure in it. <code>q = mod(p + 0.5 * c, c) - 0.5 * c</code> makes copies every
                    <code>c</code> units along each axis, all for the price of one shape. Keep the result inside the box with a
                    <code>max</code> against a big box, or the copies fill everything.</p>
                    <h2>Twisting and bending</h2>
                    <p>Turning <code>p.xz</code> by an angle that depends on <code>p.y</code> twists the shape like a towel. Deformations
                    like this break the "never too far" rule a little, so step by <code>d * 0.7</code> instead of the full d, or you'll
                    see holes.</p>
                    <h2>Keeping it readable</h2>
                    <p>Give each part its own named distance (<code>ball</code>, <code>ring</code>, <code>stem</code>) and combine them
                    at the end. When something looks wrong, you can return a single part to see it on its own.</p>
                    <h2>Common mistakes</h2>
                    <ul>
                    <li>Carving with the wrong sign removes everything outside the cutter instead of inside it.</li>
                    <li>Heavy twisting with full steps leaves holes and missing chunks.</li>
                    </ul>
                    <h2>Try this</h2>
                    <ul>
                    <li>A twisted box.</li>
                    <li>A row of rings using repetition along one axis only.</li>
                    </ul>
                    """, """
                    // combining shapes
                    // shape() is the distance to the surface. everything you build goes in here

                    float shape(vec3 p) {
                        return length(p) - 0.45;
                    }

                    vec4 object(vec3 origin, vec3 dir, float time) {
                        float t = 0.0;
                        for (int i = 0; i < 80; i++) {
                            vec3 p = origin + dir * t;
                            float d = shape(p);
                            if (d < 0.001) {
                                vec2 e = vec2(0.001, 0.0);
                                vec3 n = normalize(vec3(shape(p + e.xyy) - shape(p - e.xyy),
                                                        shape(p + e.yxy) - shape(p - e.yxy),
                                                        shape(p + e.yyx) - shape(p - e.yyx)));
                                float light = max(dot(n, normalize(vec3(0.5, 0.8, 0.3))), 0.0);
                                return vec4(vec3(0.9, 0.7, 0.4) * (0.15 + light), 1.0);
                            }
                            t += d;
                            if (t > 3.5) break;
                        }
                        return vec4(0.0);
                    }
                    """, List.of(
                    step("Glue a box onto the sphere", "Work out both distances and keep the smaller one with min.",
                            "float ball = length(p) - 0.45;\nfloat box = length(max(abs(p) - vec3(0.6, 0.15, 0.15), 0.0));\nreturn min(ball, box);", has("min\\s*\\(")),
                    step("Carve a hole through it", "max with the negative of another shape cuts that shape away.",
                            "float hole = length(p.xy) - 0.2;\nreturn max(min(ball, box), -hole);", has("max\\s*\\([^;]*-")),
                    step("Blend shapes smoothly", "Write a smooth min above shape, then use it in shape instead of min. k is how wide the blend is.",
                            "float smin(float a, float b, float k) {\n    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);\n    return mix(b, a, h) - k * h * (1.0 - h);\n}\n\n// and in shape():\nreturn max(smin(ball, box, 0.15), -hole);",
                            has("float\\s+\\w+\\s*\\(\\s*float\\s+\\w+\\s*,\\s*float\\s+\\w+\\s*,\\s*float").or(has("0\\.5\\s*\\+\\s*0\\.5\\s*\\*"))),
                    step("Add a ring", "A torus is a circle in the xz plane with thickness. length(p.xz) gives the distance from the middle.",
                            "vec2 q = vec2(length(p.xz) - 0.7, p.y);\nfloat ring = length(q) - 0.06;\nreturn min(max(smin(ball, box, 0.15), -hole), ring);", has("length\\s*\\(\\s*p\\.xz\\s*\\)")),
                    step("Animate it", "shape() can't see time, and it's called seven times, so instead of passing it everywhere keep it in a variable above shape. Set it first thing in object, then move a piece with it, like the ring bobbing up and down.",
                            "float now;   // above shape()\n\nnow = time;   // first line of object()\n\nvec2 q = vec2(length(p.xz) - 0.7, p.y - sin(now) * 0.2);   // in shape()", uses("time")))),

            new Lesson("lesson_lighting", "Lighting objects", "diffuse, specular, rim and occlusion", 10, """
                    <p>The <b>normal</b> is the direction a surface faces. You can get it from any distance function by checking
                    how the distance changes when you nudge the point a tiny bit along each axis.</p>
                    <p>With a normal you can light anything. <b>Diffuse</b> is <code>dot(normal, light)</code>, bright where the
                    surface faces the light. <b>Specular</b> is the shiny highlight, found by reflecting the view ray and seeing
                    how close it bounces toward the light. A <b>rim</b> or fresnel term brightens edges that face away from
                    you, which makes things pop off the background.</p>
                    <p>The number of steps the march took is a decent stand in for <b>ambient occlusion</b>,
                    because rays that brush past creases take more steps.</p>
                    <h2>How the normal is found</h2>
                    <p>Move the point a tiny bit along x and measure the distance on both sides. The difference says how fast the
                    distance grows in that direction. Do it for y and z too and you have the direction the distance grows fastest, which
                    points straight out of the surface. The nudge <code>e</code> matters: too small and the normal gets noisy, too big
                    and sharp edges go soft.</p>
                    <h2>Diffuse</h2>
                    <p>A surface turned away from the light catches less of it, because the same light spreads over more area.
                    <code>dot(n, light)</code> is exactly that falloff: 1 facing the light, 0 side on.</p>
                    <h2>Specular</h2>
                    <p>Bounce the view ray off the surface with <code>reflect(dir, n)</code> and see how close it lands to the light. The
                    power is the shininess: 8 looks like rough plastic, 64 like polished metal. A common alternative uses the
                    <b>half vector</b>, <code>normalize(light - dir)</code>, against the normal.</p>
                    <h2>Fresnel</h2>
                    <p>Almost everything reflects more at grazing angles. Glass looks clear face on and like a mirror side on. A good
                    approximation is <code>f0 + (1.0 - f0) * pow(1.0 - cosTheta, 5.0)</code>, with f0 around 0.04 for most materials.</p>
                    <h2>Shadows</h2>
                    <p>From the point you hit, march a second ray toward the light. If it hits something, the point is in shadow. For
                    soft shadows, keep the smallest <code>k * d / t</code> seen along the way and use it as the amount of light.</p>
                    <h2>Common mistakes</h2>
                    <ul>
                    <li>A shadow ray that starts exactly on the surface hits that surface straight away and shadows everything. Start a
                    little outside it: <code>p + n * 0.01</code>.</li>
                    <li>Forgetting ambient light makes the dark side pure black.</li>
                    </ul>
                    <h2>Try this</h2>
                    <ul>
                    <li>Add a hard shadow on the ring from the ball.</li>
                    <li>A second, blue light from the other side.</li>
                    </ul>
                    """, """
                    // lighting objects
                    // the shape and its normal are done, right now it's lit flat. light it properly

                    float shape(vec3 p) {
                        float ball = length(p) - 0.45;
                        vec2 q = vec2(length(p.xz) - 0.7, p.y);
                        return min(ball, length(q) - 0.08);
                    }

                    vec4 object(vec3 origin, vec3 dir, float time) {
                        vec3 light = normalize(vec3(0.6, 0.7, 0.4));
                        float t = 0.0;
                        for (int i = 0; i < 80; i++) {
                            vec3 p = origin + dir * t;
                            float d = shape(p);
                            if (d < 0.001) {
                                vec2 e = vec2(0.001, 0.0);
                                vec3 n = normalize(vec3(shape(p + e.xyy) - shape(p - e.xyy),
                                                        shape(p + e.yxy) - shape(p - e.yxy),
                                                        shape(p + e.yyx) - shape(p - e.yyx)));
                                vec3 color = vec3(0.8);
                                return vec4(color, 1.0);
                            }
                            t += d;
                            if (t > 3.5) break;
                        }
                        return vec4(0.0);
                    }
                    """, List.of(
                    step("Add diffuse light", "dot of the normal with the light direction, kept above zero.",
                            "float diffuse = max(dot(n, light), 0.0);\nvec3 color = vec3(0.8) * diffuse;   // replaces vec3 color = vec3(0.8);", has("dot\\s*\\(\\s*(n\\s*,|light\\s*,\\s*n\\b)")),
                    step("Add some ambient light", "Without it the dark side is pure black. Add a small amount of light that reaches everywhere.",
                            "float ambient = 0.15;\nvec3 color = vec3(0.8) * (ambient + diffuse);", has("\\bambient\\b|\\d\\.\\d+\\s*\\+\\s*diffuse|diffuse\\s*\\+\\s*\\d\\.\\d+")),
                    step("Add a specular highlight", "reflect the view ray off the surface and pow how close it lands to the light.",
                            "float spec = pow(max(dot(reflect(dir, n), light), 0.0), 40.0);\ncolor += vec3(1.0) * spec;", has("reflect\\s*\\(")),
                    step("Add a rim light", "Edges that face away from the camera get brighter.",
                            "float rim = pow(1.0 - max(dot(n, -dir), 0.0), 3.0);\ncolor += vec3(0.4, 0.6, 1.0) * rim * 0.6;", has("pow\\s*\\(\\s*1\\.0\\s*-|1\\.0\\s*-\\s*(max|abs|clamp)\\s*\\(\\s*dot")),
                    step("Fake ambient occlusion", "Scale everything by how many steps the ray took to get here.",
                            "float occlusion = 1.0 - float(i) / 80.0;\ncolor *= occlusion;", has("float\\s*\\(\\s*\\w+\\s*\\)\\s*/")))),

            new Lesson("lesson_glow", "Glow and see-through", "light that isn't a surface", 10, """
                    <p>Not everything has a surface. Plasma, magic and energy are light floating in the air. Instead of stopping
                    when the ray hits something, you walk the ray in small steps and add up how much light there is at each
                    point. That's <b>volumetric glow</b>.</p>
                    <p><code>exp(-distance * k)</code> is the go to shape for it: 1 right at the source and fading smoothly away.
                    A big <code>k</code> gives a tight hot core, a small one a wide haze.</p>
                    <p>Glow doesn't block the world behind it, so it needs an alpha. A simple one is the brightest color channel,
                    and dividing the color by that alpha keeps it from going dim when it's blended in.</p>
                    <h2>Walking through a volume</h2>
                    <p>With a surface you stop at the first hit. With glow there's nothing to hit, so the ray takes fixed steps through the
                    whole box and adds a little light at each one. 90 steps of 0.04 cover 3.6 units, just enough to cross the box. Smaller
                    steps give finer detail and cost more.</p>
                    <h2>Shapes of glow</h2>
                    <table>
                    <tr><td><code>exp(-d * k)</code></td><td>smooth falloff, the usual choice</td></tr>
                    <tr><td><code>1.0 / (1.0 + d * d * k)</code></td><td>a bright core with a long tail, like real light</td></tr>
                    <tr><td>noise times either</td><td>broken up glow for plasma, smoke and fire</td></tr>
                    </table>
                    <h2>Glow that hides the background</h2>
                    <p>Pure glow only adds. Real smoke also hides what's behind it. Keep a running <code>transmittance</code> that starts
                    at 1 and shrinks with every step, <code>transmittance *= exp(-density * step)</code>, and multiply the light you add
                    by it. That makes thick parts look solid and lets clouds and fire have a shape.</p>
                    <h2>Scale by the step</h2>
                    <p>Multiply what you add at each step by the step size. Then changing the step size changes the detail but not the
                    brightness. Without it, halving the step doubles the glow.</p>
                    <h2>Hiding the rings</h2>
                    <p>Fixed steps leave rings where the samples line up. Start each ray at a slightly different spot, by adding a random
                    fraction of a step to <code>t</code> from a hash of <code>gl_FragCoord.xy</code>, and the rings turn into fine grain
                    that's much harder to see.</p>
                    <h2>Common mistakes</h2>
                    <ul>
                    <li>Too few steps make the glow look like stacked slices.</li>
                    <li>Forgetting to tone map turns the core into a flat white blob.</li>
                    </ul>
                    <h2>Try this</h2>
                    <ul>
                    <li>Plasma: multiply the core's glow by a moving noise.</li>
                    <li>Smoke: add transmittance and use a gray color.</li>
                    </ul>
                    """, """
                    // glow and see-through
                    // this walks the ray in small steps through the box. right now nothing adds any light

                    vec4 object(vec3 origin, vec3 dir, float time) {
                        vec3 glow = vec3(0.0);
                        float t = 0.0;
                        for (int i = 0; i < 90; i++) {
                            vec3 p = origin + dir * t;
                            t += 0.04;
                            if (t > 3.5) break;
                        }
                        return vec4(glow, 0.0);
                    }
                    """, List.of(
                    step("Add a glowing core", "Inside the loop, add light that fades with the distance from the middle.",
                            "glow += vec3(1.0, 0.6, 0.2) * exp(-length(p) * 8.0) * 0.05;", has("glow\\s*\\+=").and(has("exp\\s*\\("))),
                    step("Turn the glow into alpha", "Use the brightest channel as alpha and divide the color by it.",
                            "float alpha = clamp(max(glow.r, max(glow.g, glow.b)), 0.0, 1.0);\nreturn vec4(glow / max(alpha, 0.001), alpha);",
                            lacks("return vec4(glow, 0.0)").and(has("max\\s*\\("))),
                    step("Add a ring of light", "abs(length(p) - radius) is the distance to a sphere's shell, so its glow is a ring.",
                            "glow += vec3(0.3, 0.6, 1.0) * exp(-abs(length(p) - 0.6) * 30.0) * 0.03;", has("abs\\s*\\(\\s*length")),
                    step("Make it pulse", "Make a pulse from sin(time) and use it to grow and shrink the core. This replaces the core line in the loop.",
                            "float pulse = 0.8 + 0.2 * sin(time * 3.0);\nglow += vec3(1.0, 0.6, 0.2) * exp(-length(p) * 8.0 / pulse) * 0.05;", uses("time")),
                    step("Tone map before returning", "1.0 - exp(-glow) keeps the core from clipping into a flat white blob. Put it after the loop, before the alpha is worked out.",
                            "glow = 1.0 - exp(-glow * 1.5);\nfloat alpha = clamp(max(glow.r, max(glow.g, glow.b)), 0.0, 1.0);", has("1\\.0\\s*-\\s*exp\\s*\\(")))),

            new Lesson("lesson_surface", "Your first surface", "uv, grids, holes and edge glow", 10, """
                    <p>A surface paints the faces of the item itself, so the item becomes a screen, a sign, a portal or a wall.
                    Instead of a direction like a sky, every pixel gets <code>uv</code>: where it sits on the face, from 0 to 1
                    across and 0 to 1 up, like coordinates on a sheet of paper.</p>
                    <p>Patterns come from repeating uv. <code>fract(uv * 8.0)</code> counts from 0 to 1 eight times across the
                    face, so anything you draw with it repeats in an 8 by 8 grid.</p>
                    <p>Alpha cuts holes: return 0 and that pixel is gone, so a square panel can become a round portal.
                    The <code>#pragma vessel shape</code> line picks the item's shape: <code>cube</code>, <code>panel</code>,
                    <code>pillar</code> or <code>floor</code>.</p>
                    <h2>uv or pos?</h2>
                    <p><code>uv</code> starts over on every face, so a pattern drawn with it repeats the same way on all six sides.
                    <code>pos</code> is the point's place in the item's box, which carries on across the corners. Use uv for screens,
                    signs and anything that belongs on one face. Use pos for patterns that should wrap round the whole item without
                    seams.</p>
                    <h2>Drawing with distances</h2>
                    <p>Flat shapes work just like the 3D ones in the object lessons, only in 2D:</p>
                    <table>
                    <tr><td>circle</td><td><code>length(uv - 0.5) - r</code></td></tr>
                    <tr><td>ring</td><td><code>abs(length(uv - 0.5) - r) - thickness</code></td></tr>
                    <tr><td>box</td><td><code>max(abs(uv.x - 0.5), abs(uv.y - 0.5)) - size</code></td></tr>
                    </table>
                    <p>Turn a distance into a shape with <code>smoothstep</code>: negative inside, positive outside.</p>
                    <h2>Smooth edges</h2>
                    <p><code>step</code> makes jagged pixel stairs. <code>fwidth(d)</code> tells you how much d changes from one pixel to
                    the next, so <code>smoothstep(w, -w, d)</code> with <code>w = fwidth(d)</code> gives an edge exactly one pixel soft at
                    any distance from the camera.</p>
                    <h2>Fake depth</h2>
                    <p>Look something up at <code>pos + view * depth</code> and it seems to sit behind the face. Stack a few at different
                    depths and the item looks hollow, like a window into a room.</p>
                    <h2>Common mistakes</h2>
                    <ul>
                    <li>A pattern flipped on some faces: that face's uv runs the other way, use pos for it instead.</li>
                    <li>On panel and floor shapes the thin edges are skipped, so don't put anything important there.</li>
                    </ul>
                    <h2>Try this</h2>
                    <ul>
                    <li>A screen with moving scanlines.</li>
                    <li>A swirl for a portal using <code>atan(uv.y - 0.5, uv.x - 0.5)</code>, the angle around the middle.</li>
                    </ul>
                    """, """
                    // your first surface
                    // uv goes 0 to 1 across each face of the item. right now it's shown as red and green so you can see it
                    #pragma vessel shape panel

                    vec4 surface(vec2 uv, vec3 pos, vec3 normal, vec3 view, float time) {
                        vec3 color = vec3(uv, 0.0);
                        return vec4(color, 1.0);
                    }
                    """, List.of(
                    step("Draw a grid", "fract repeats uv, and the distance from the middle of each cell is biggest at its border. step turns the border into a line.",
                            "vec2 cell = abs(fract(uv * 8.0) - 0.5);\nfloat line = step(0.45, max(cell.x, cell.y));\nvec3 color = mix(vec3(0.02, 0.05, 0.1), vec3(0.3, 0.9, 1.0), line);\nreturn vec4(color, 1.0);",
                            has("fract\\s*\\([^;]*\\buv\\b")),
                    step("Scroll it", "Add time to uv before the fract and the whole grid slides.",
                            "vec2 cell = abs(fract((uv + vec2(0.0, time * 0.2)) * 8.0) - 0.5);", uses("time")),
                    step("Cut it into a circle", "length(uv - 0.5) is the distance from the middle of the face. Return alpha 0 when it's past 0.5 and the corners disappear. Put it first in the function.",
                            "if (length(uv - 0.5) > 0.5) return vec4(0.0);", has("length\\s*\\(\\s*uv\\s*-")),
                    step("Glow at the edges", "dot(normal, view) is near 1 when you look straight at the face and near 0 when you see it side on. One minus that lights up the edges as you turn it.",
                            "float rim = pow(1.0 - abs(dot(normal, view)), 2.0);\ncolor += vec3(0.3, 0.9, 1.0) * rim;\nreturn vec4(color, 1.0);",
                            has("dot\\s*\\(\\s*(normal\\s*,\\s*view|view\\s*,\\s*normal)")),
                    step("Make it see-through", "Return an alpha under 1 and the world shows through, like a hologram.",
                            "return vec4(color, 0.75);", has("return\\s+vec4\\s*\\([^;]*,\\s*0?\\.\\d+\\s*\\)\\s*;")))),

            new Lesson("lesson_material", "Your first material", "repainting an item's pixels", 10, """
                    <p>A material keeps the item's normal flat sprite and repaints every pixel of it. <code>texel</code> is that
                    pixel's own color from the texture, so you can keep the outline and shading of the item and change
                    everything else. <code>uv</code> goes 0 to 1 across the sprite.</p>
                    <p>Item sprites are 16 by 16 pixels. If your effect is smooth it looks out of place next to them, so it's
                    common to snap uv to the sprite's grid first: <code>(floor(uv * 16.0) + 0.5) / 16.0</code> gives the middle of
                    whichever pixel you're in, and everything you draw with it comes out blocky.</p>
                    <p>Without a png of your own the preview uses a plain gem. Put a png with the same name beside the effect
                    and it'll use that.</p>
                    <h2>What runs through it</h2>
                    <p>Only the pixels you can see on the item run through the material. See-through pixels around the sprite stay
                    see-through as long as you pass <code>texel.a</code> along as the alpha. Return alpha 1 for everything and those
                    empty pixels turn into solid squares.</p>
                    <h2>Brightness that looks right</h2>
                    <p>Eyes see green as much brighter than blue. For a brightness that matches what you see, weight the channels:
                    <code>dot(texel.rgb, vec3(0.299, 0.587, 0.114))</code>. Use it to keep the sprite's shading, or as a mask.</p>
                    <h2>Masks</h2>
                    <p>A mask decides where the effect goes. <code>smoothstep(0.6, 0.8, brightness)</code> picks only the bright pixels,
                    so a sword's blade can glow while its dark handle stays put. <code>uv.y</code> works as a mask too, for an effect
                    that only covers the top of the item.</p>
                    <h2>Staying blocky</h2>
                    <p>Snapping to the 16 by 16 grid makes the effect move a whole pixel at a time, like vanilla textures do. You can snap
                    time as well, <code>floor(time * 8.0) / 8.0</code>, for choppy animation in the style of the game's own animated
                    blocks.</p>
                    <h2>Depth as you turn</h2>
                    <p>Offsetting uv by <code>view.xz * depth</code> moves a layer as the item turns. Several layers at different depths
                    slide past each other and give the item depth, like the end portal.</p>
                    <h2>Common mistakes</h2>
                    <ul>
                    <li>Returning alpha 1 everywhere turns the transparent area into a solid square.</li>
                    <li>Smooth, unsnapped effects look out of place next to blocky vanilla items.</li>
                    </ul>
                    <h2>Try this</h2>
                    <ul>
                    <li>Lava only on the bright parts, using a brightness mask.</li>
                    <li>An enchantment style glint: a diagonal band sweeping across with time.</li>
                    </ul>
                    """, """
                    // your first material
                    // texel is the item's own pixel color and uv goes 0 to 1 across the sprite.
                    // right now every pixel is given back unchanged

                    vec4 material(vec4 texel, vec2 uv, vec3 normal, vec3 view, float time) {
                        return texel;
                    }
                    """, List.of(
                    step("Tint it", "Multiply the texel's color by a color of your own. Keep texel.a as the alpha so see-through pixels stay see-through.",
                            "return vec4(texel.rgb * vec3(1.0, 0.5, 0.2), texel.a);", has("texel\\.rgb\\s*\\*|\\*\\s*texel\\.rgb")),
                    step("Snap to the sprite's pixels", "floor counts which of the 16 pixels you're in, the + 0.5 moves to its middle.",
                            "vec2 px = (floor(uv * 16.0) + 0.5) / 16.0;", has("floor\\s*\\(\\s*uv\\s*\\*\\s*16")),
                    step("Run a wave across it", "A sin of the snapped position minus time moves across the item one pixel step at a time.",
                            "float wave = 0.5 + 0.5 * sin(px.x * 10.0 + px.y * 6.0 - time * 3.0);\nvec3 color = mix(vec3(0.6, 0.1, 0.0), vec3(1.0, 0.8, 0.2), wave);\nreturn vec4(color, texel.a);",
                            has("sin\\s*\\(").and(uses("time"))),
                    step("Keep the sprite's shading", "Multiply by how bright the texel is, so the dark outline and shading still show through your colors.",
                            "float shade = dot(texel.rgb, vec3(0.333));\nvec3 color = mix(vec3(0.6, 0.1, 0.0), vec3(1.0, 0.8, 0.2), wave) * shade * 1.6;\nreturn vec4(color, texel.a);",
                            has("dot\\s*\\(\\s*texel|texel\\.[rgb]\\b(?!g)")),
                    step("Make it glint as you turn", "Reflect the view off the item and see how close it bounces toward a light. Add that as a highlight before the return.",
                            "float glint = pow(max(dot(reflect(view, normal), normalize(vec3(0.4, 0.8, 0.4))), 0.0), 16.0);\ncolor += vec3(1.0) * glint;\nreturn vec4(color, texel.a);",
                            has("reflect\\s*\\(|dot\\s*\\([^;]*\\bview\\b")))),

            new Lesson("lesson_overlay", "Your first overlay", "vignettes, pulses and the aspect trap", 10, """
                    <p>An overlay is laid over everything the player sees, like rain on the lens or the red edges you get at low
                    health. <code>screen</code> is where the pixel is on the window, 0,0 in the bottom left to 1,1 in the top right.</p>
                    <p>Here alpha is how much of the world you cover. 0 leaves the view clear,
                    1 hides it completely. Keep the middle of the screen low or the player can't play.</p>
                    <p>One trap: <code>screen</code> is squashed into a square, but windows are wide, so a circle measured with it
                    comes out as an oval. Stretch x by about 1.8 before measuring and circles stay round.</p>
                    <p><code>dir</code> is the direction the pixel looks into the world. Things placed with <code>screen</code>
                    stick to your view, things placed with <code>dir</code> stay put when you turn your head.</p>
                    <h2>Two ways to place things</h2>
                    <p>Anything built from <code>screen</code> sticks to the glass, like rain on goggles, and moves with your head. Anything
                    built from <code>dir</code> stays in the world: turn around and it's behind you. Mixing both is how a domain effect
                    can tint your view and also have embers hanging in the air.</p>
                    <h2>The aspect trap, exactly</h2>
                    <p>A normal window is about 16 by 9, so screen x covers 1.78 times as much space as screen y. Multiplying x by about
                    1.8 before measuring makes circles round on most screens. It's not exact on unusual monitors, but it's close enough
                    for soft effects.</p>
                    <h2>Keep it playable</h2>
                    <ul>
                    <li>Keep alpha under about 0.15 in the middle of the screen, where the player aims.</li>
                    <li>Edges can go much higher, up to around 0.8, without getting in the way.</li>
                    <li>Avoid flashing faster than three times a second. Fast flashing is uncomfortable and can be dangerous for people
                    with photosensitive epilepsy.</li>
                    </ul>
                    <h2>Textures on the glass</h2>
                    <p>Noise and cells from the sky lessons work on screen coordinates too: cells make raindrops, noise makes frost and
                    grime. Scale <code>screen</code> up, around 10 to 30, for detail that matches the size of the window.</p>
                    <h2>Fading in</h2>
                    <p><code>clamp(time / 2.0, 0.0, 1.0)</code> grows from 0 to 1 over two seconds. Multiply your alpha by it so the
                    overlay eases in instead of snapping on.</p>
                    <h2>Common mistakes</h2>
                    <ul>
                    <li>Returning a color with alpha 1 everywhere covers the whole world.</li>
                    <li>Forgetting the aspect fix stretches every circle into an oval.</li>
                    </ul>
                    <h2>Try this</h2>
                    <ul>
                    <li>Snow drifting past, placed with <code>dir</code> so it stays in the world.</li>
                    <li>Raindrops on the glass, made from cells on <code>screen</code>.</li>
                    </ul>
                    """, """
                    // your first overlay
                    // screen goes 0,0 bottom left to 1,1 top right. alpha is how much of the world you cover,
                    // so this one covers nothing yet

                    vec4 overlay(vec2 screen, vec3 dir, float time) {
                        return vec4(0.0);
                    }
                    """, List.of(
                    step("Darken the edges", "length(screen - 0.5) is the distance from the middle. smoothstep turns it into an alpha that's 0 in the middle and rises toward the corners.",
                            "float edge = length(screen - 0.5);\nreturn vec4(0.0, 0.0, 0.0, smoothstep(0.3, 0.8, edge));", has("length\\s*\\(\\s*\\(?\\s*screen")),
                    step("Keep it round", "Stretch the x of the position by 1.8 before measuring. The dark ring stops being an oval on a wide window.",
                            "vec2 p = (screen - 0.5) * vec2(1.8, 1.0);\nfloat edge = length(p);", has("\\*\\s*vec2\\s*\\(\\s*\\d*\\.\\d+\\s*,\\s*1\\.0\\s*\\)|\\.x\\s*\\*=\\s*\\d")),
                    step("Pulse like a heartbeat", "pow of a sin wave with a big power makes short sharp beats with rests between. Multiply the alpha by it.",
                            "float beat = pow(0.5 + 0.5 * sin(time * 6.0), 8.0);\nreturn vec4(0.0, 0.0, 0.0, smoothstep(0.6, 1.2, edge) * (0.6 + 0.4 * beat));", has("sin\\s*\\(").and(uses("time"))),
                    step("Tint it red", "Give the overlay a color instead of black.",
                            "return vec4(0.6, 0.0, 0.05, smoothstep(0.6, 1.2, edge) * (0.6 + 0.4 * beat));", has("return\\s+vec4\\s*\\(\\s*(0?\\.0*[1-9]|[1-9])")),
                    step("Pin something to the world", "dot(dir, d) is close to 1 when you look toward d. A glowing spot made from it stays in one place in the world while the vignette follows your view. Add it to the alpha.",
                            "float spot = pow(max(dot(dir, normalize(vec3(0.0, 0.2, -1.0))), 0.0), 300.0);\nfloat alpha = smoothstep(0.6, 1.2, edge) * (0.6 + 0.4 * beat) + spot;\nreturn vec4(0.6, 0.0, 0.05, alpha);",
                            has("dot\\s*\\(\\s*dir\\b|dot\\s*\\([^;]*,\\s*dir\\s*\\)")))));

    // one open task per lesson with no example, where you have to work it out yourself
    static final java.util.Map<String, String> CHALLENGES = java.util.Map.ofEntries(
            java.util.Map.entry("lesson_basics", "Make the sky go from one color at the bottom to another at the top using only +, * and variables, no mix."),
            java.util.Map.entry("lesson_first_sky", "Add a warm band of light just above the horizon that fades out by the time dir.y reaches 0.3."),
            java.util.Map.entry("lesson_sun", "Put a moon on the opposite side of the sky from the sun, smaller and pale blue. What direction is opposite sunDir?"),
            java.util.Map.entry("lesson_clouds", "Make the clouds darker underneath. One way: look the noise up a second time a little higher and use the difference."),
            java.util.Map.entry("lesson_stars", "Hide the stars near the horizon, and make a rare few of them much bigger and brighter than the rest."),
            java.util.Map.entry("lesson_warp", "Warp the warp: build a second offset from the already warped position and feed that in too."),
            java.util.Map.entry("lesson_palettes", "Go back to the clouds or the stars and color them with your palette instead of hand picked colors."),
            java.util.Map.entry("lesson_object", "Make the sphere squash as it lands and stretch as it rises, by scaling p.y with time before measuring the distance."),
            java.util.Map.entry("lesson_shapes", "Build something real out of spheres, boxes and smooth blends: a snowman, a mushroom or a lamp."),
            java.util.Map.entry("lesson_lighting", "Add a second, colored light from below, like sunlight bouncing up off the ground."),
            java.util.Map.entry("lesson_glow", "Add a small spark that circles the core. cos(time) and sin(time) make a point go round in a circle."),
            java.util.Map.entry("lesson_surface", "Switch the shape to floor and draw rings that keep spreading out from the middle, like ripples on water."),
            java.util.Map.entry("lesson_material", "Make the color depend on how high the pixel is, so the bottom of the item burns hot and the top cools down."),
            java.util.Map.entry("lesson_overlay", "Make frost: white, see-through noise that only shows near the edges and creeps further in over the first few seconds."));

    // swing's html only takes a single plain family name, and the bundled mono font is registered by the editor
    private static final String MONO = CodeEditor.mono(12).getFamily();

    private static String css() {
        Theme t = Theme.current;
        String text = Fx.hex(t.text()), strong = Fx.hex(t.strong()), dim = Fx.hex(t.dim()), box = Fx.hex(t.editor());
        return "body{font-family:'Segoe UI';font-size:13px;color:" + text + ";margin:0}"
                + "h1{font-family:'Segoe UI';font-size:22px;color:" + strong + ";font-weight:bold;margin:0 0 4px 0}"
                + "h2{font-family:'Segoe UI';font-size:15px;color:" + strong + ";font-weight:bold;margin:18px 0 6px 0}"
                + "p{margin:0 0 9px 0}b{color:" + strong + "}code{font-family:" + MONO + ";color:" + Fx.hex(t.keyword()) + ";font-size:10px}"
                + "pre{font-family:" + MONO + ";font-size:10px;color:" + text + ";background:" + box + ";padding:10px;margin:4px 0 12px 0}"
                + "td{color:" + text + ";padding:3px 14px 3px 0;vertical-align:top}.dim{color:" + dim + "}.lead{font-size:14px;color:" + dim + "}"
                + ".tip{background:" + box + ";padding:10px 12px;margin:6px 0 12px 0;color:" + text + "}";
    }

    static final List<Guide> GUIDES = List.of(
            new Guide("Getting started", "a tour of the window", """
                    <h1>Getting started</h1>
                    <p class="lead">Everything you need to go from an empty window to a pack you can load in Minecraft.</p>
                    <h2>The window</h2>
                    <table>
                    <tr><td><b>Files</b></td><td>the project on the left. Right click a file for its options, like effect settings, duplicate and copy give command.</td></tr>
                    <tr><td><b>Editor</b></td><td>the middle. Every shader is one tab. Lines with a vec3 color show a swatch next to the line number.</td></tr>
                    <tr><td><b>Preview</b></td><td>the right. It recompiles as you type. Drag to look around. The fps and resolution show under it.</td></tr>
                    <tr><td><b>Console</b></td><td>the bottom. Problems lists compile errors, click one to jump to the line. Log shows build output.</td></tr>
                    </table>
                    <h2>Your first pack in four steps</h2>
                    <p><b>1.</b> File &gt; New Project and answer the questions. <b>2.</b> Ctrl+N and pick an example from the gallery.
                    <b>3.</b> Change something and watch the preview. <b>4.</b> Ctrl+B builds the pack into the project's build folder.</p>
                    <h2>Shortcuts worth knowing</h2>
                    <table>
                    <tr><td><code>Ctrl+Shift+P</code></td><td>command palette, type part of any command</td></tr>
                    <tr><td><code>Ctrl+P</code></td><td>jump to a file by name</td></tr>
                    <tr><td><code>Ctrl+N</code></td><td>new effect from the gallery</td></tr>
                    <tr><td><code>Ctrl+B</code></td><td>build the pack</td></tr>
                    <tr><td><code>Ctrl+,</code></td><td>project settings</td></tr>
                    <tr><td><code>Ctrl+Shift+E / V, Ctrl+J</code></td><td>show or hide files, preview and console</td></tr>
                    <tr><td><code>F1</code></td><td>this window</td></tr>
                    </table>
                    """),
            new Guide("How packs work", "alpha codes, items and versions", """
                    <h1>How packs work</h1>
                    <p class="lead">What actually happens when you press Build.</p>
                    <h2>One shader for everything</h2>
                    <p>Minecraft draws items with a core shader. The build takes every effect in your project, compiles them into
                    that one shader, and adds a small dispatcher in front: for each pixel of an item it checks the texture's
                    alpha, and if it matches one of your effects it runs that effect instead of drawing the texture.</p>
                    <h2>Alpha codes</h2>
                    <p>Every effect has an alpha code from 1 to 254, set in Effect Settings. The build makes an item for each
                    effect whose texture is filled with exactly that alpha, so the item and the effect find each other. Two
                    effects can't share a code. Normal items almost never use these exact values, so they're left alone.</p>
                    <div class="tip">Object textures also store each pixel's position in their colors. That's how an object effect
                    works out where the item's box is on screen.</div>
                    <h2>Versions</h2>
                    <p>The shader system changed several times between 1.21 and 26.3, so the pack carries a copy for each era
                    in overlays: 1.21 to 1.21.5, 1.21.6 to 1.21.11, 26.1, 26.2 and 26.3, plus a fix for 1.21 and 1.21.1. The
                    pack metadata lists formats 34 to 99 the way big multi version packs do, so the game picks the right copy.</p>
                    <h2>Checking and obfuscating</h2>
                    <p>With Check Shaders on, every copy is compiled with glslang before the zip is made, and 26.3 is also
                    compiled to Vulkan SPIR-V. Obfuscate renames every function and variable to random letters and adds decoy
                    code, so the shipped pack is hard to read. Your project files stay as you wrote them.</p>
                    """),
            new Guide("The sky function", "directions, projections and time", """
                    <h1>The sky function</h1>
                    <pre>vec3 sky(vec3 dir, float time)</pre>
                    <p class="lead">Runs once per pixel of the sky. Gets a direction, gives back a color.</p>
                    <h2>The direction</h2>
                    <p><code>dir</code> has length 1. <code>dir.y</code> is up, <code>dir.x</code> is to the side, and
                    <code>-dir.z</code> is straight ahead in the preview. In game the sky doesn't turn with the player, so
                    "ahead" is a fixed compass direction.</p>
                    <h2>Ways to lay things out</h2>
                    <table>
                    <tr><td><code>dir.y</code></td><td>height above the horizon, for gradients</td></tr>
                    <tr><td><code>dot(dir, d)</code></td><td>closeness to a direction, for suns, moons and spotlights</td></tr>
                    <tr><td><code>dir.xz / dir.y</code></td><td>a flat ceiling, for cloud layers and grids that stretch to the horizon</td></tr>
                    <tr><td><code>atan(dir.x, -dir.z)</code></td><td>the compass angle around you, for mountain lines and horizon bands</td></tr>
                    <tr><td><code>dir.xz / (1.0 + dir.y)</code></td><td>the dome flattened into a disk with the zenith in the middle, for patterns overhead</td></tr>
                    <tr><td><code>floor(dir * n)</code></td><td>cells for stars, sparks and particles</td></tr>
                    </table>
                    <div class="tip">The angle from atan jumps from +pi to -pi right behind you, which leaves a seam in any noise
                    fed with it. Feed noise <code>vec2(cos(angle), sin(angle))</code> instead and the seam is gone.</div>
                    <h2>Time</h2>
                    <p><code>time</code> counts seconds. In game it comes from the world clock, so it wraps around now and then.
                    Prefer <code>sin(time)</code> and <code>fract(time)</code> to huge multiples of it.</p>
                    <h2>Intro</h2>
                    <p><code>intro</code> goes from 0 to 1 over the first five seconds after the sky is turned on, then stays at 1.
                    Use it for an entrance: cracks that open and fade, a flash, colour bleeding in. The preview replays it
                    every 15 seconds. It needs the generated Skript's sky command, anywhere else it's just 1.</p>
                    <h2>Brightness</h2>
                    <p>Colors clip at 1. When you stack suns, glows and stars, end with <code>return 1.0 - exp(-color * 1.5);</code>
                    so bright parts roll off smoothly instead of flattening into white.</p>
                    """),
            new Guide("Objects and raymarching", "distance functions, shapes and blends", """
                    <h1>Objects and raymarching</h1>
                    <pre>vec4 object(vec3 origin, vec3 dir, float time)</pre>
                    <p class="lead">Draws a 3D thing inside the item's box, which runs from -1 to 1 on every axis.</p>
                    <h2>The loop</h2>
                    <pre>float t = 0.0;
                    for (int i = 0; i &lt; 80; i++) {
                        vec3 p = origin + dir * t;
                        float d = shape(p);
                        if (d &lt; 0.001) return shade(p);
                        t += d;
                        if (t &gt; 3.5) break;
                    }
                    return vec4(0.0);</pre>
                    <p><code>shape(p)</code> returns the distance from p to the surface. Since nothing is closer than that, it's
                    always a safe step. 3.5 is a little over the longest line through the box, so the ray can stop there.</p>
                    <h2>Shapes</h2>
                    <table>
                    <tr><td>sphere</td><td><code>length(p) - r</code></td></tr>
                    <tr><td>box</td><td><code>length(max(abs(p) - size, 0.0))</code></td></tr>
                    <tr><td>rounded box</td><td><code>length(max(abs(p) - size, 0.0)) - radius</code></td></tr>
                    <tr><td>torus</td><td><code>length(vec2(length(p.xz) - R, p.y)) - r</code></td></tr>
                    <tr><td>cylinder</td><td><code>max(length(p.xz) - r, abs(p.y) - h)</code></td></tr>
                    </table>
                    <h2>Combining</h2>
                    <table>
                    <tr><td><code>min(a, b)</code></td><td>both shapes together</td></tr>
                    <tr><td><code>max(a, b)</code></td><td>only where they overlap</td></tr>
                    <tr><td><code>max(a, -b)</code></td><td>a with b carved out</td></tr>
                    <tr><td>smooth min</td><td>a and b melted together, see lesson 8</td></tr>
                    </table>
                    <h2>Moving the space</h2>
                    <p>To move, rotate or copy a shape, change <code>p</code> before measuring. <code>p - offset</code> moves it,
                    a mat2 on two of its axes rotates it, <code>abs(p)</code> mirrors it, and <code>mod(p, s) - s * 0.5</code>
                    repeats it forever.</p>
                    <h2>Alpha</h2>
                    <p>Return 1 for solid, 0 where there's nothing so the world shows through, and in between for glass and glow.
                    For glow, add up light along the ray instead of stopping at a surface, see lesson 10.</p>
                    <h2>Size</h2>
                    <p><code>#pragma vessel scale 3</code> near the top makes the item 3 blocks wide instead of 1. Your code
                    doesn't change, the -1 to 1 box just covers more of the world. 3 is the most an item model allows,
                    and the Skript's place and size commands still scale on top of it.</p>
                    <div class="tip">Objects stay upright to the world even when the item spins, because the box is worked
                    out in world space.</div>
                    """),
            new Guide("Surfaces", "painting the item's own faces", """
                    <h1>Surfaces</h1>
                    <pre>vec4 surface(vec2 uv, vec3 pos, vec3 normal, vec3 view, float time)</pre>
                    <p class="lead">Paints the faces of the item itself, so the item becomes the screen, the block or the shield.</p>
                    <h2>What you get</h2>
                    <table>
                    <tr><td><code>uv</code></td><td>0 to 1 across the face, the same on every face of the cube</td></tr>
                    <tr><td><code>pos</code></td><td>the same point in the item's -1 to 1 box, handy for patterns that wrap around corners</td></tr>
                    <tr><td><code>normal</code></td><td>the direction the face points, outward</td></tr>
                    <tr><td><code>view</code></td><td>the direction you're looking</td></tr>
                    </table>
                    <h2>Shapes</h2>
                    <p>A surface can ask for the shape of the item it paints with a line near the top of the file:</p>
                    <pre>#pragma vessel shape panel</pre>
                    <table>
                    <tr><td><code>cube</code></td><td>a full block, the default</td></tr>
                    <tr><td><code>panel</code></td><td>a thin standing sheet, for screens, signs, portals and walls</td></tr>
                    <tr><td><code>pillar</code></td><td>tall and narrow, for columns and totems</td></tr>
                    <tr><td><code>floor</code></td><td>a thin tile lying flat, for magic circles, pools and markings</td></tr>
                    </table>
                    <p>Combine a shape with alpha cutouts to get anything flat: the Portal is a panel with everything outside a
                    circle cut away, and the Rune Circle is a floor tile with the same trick.</p>
                    <h2>Tricks</h2>
                    <p><b>Rim light.</b> <code>pow(1.0 - abs(dot(normal, view)), 2.0)</code> is strong at grazing angles and weak
                    face on, which is how the Force Field gets brighter at its edges.</p>
                    <p><b>Fake depth.</b> Sample something at <code>pos + view * depth</code> and it looks like it sits behind the
                    face. Stack a few of those at different depths and the item looks hollow and far deeper than it is.</p>
                    <p><b>Holes.</b> Return alpha 0 to cut a pixel out, or anything between 0 and 1 for glass and holograms.</p>
                    <div class="tip">Put a surface on a thin, flat item and it's a screen or a sign. The Skript's place command
                    with a size works for that, and so does any item display you scale yourself.</div>
                    """),
            new Guide("Materials", "animating an item's own pixels", """
                    <h1>Materials</h1>
                    <pre>vec4 material(vec4 texel, vec2 uv, vec3 normal, vec3 view, float time)</pre>
                    <p class="lead">Keeps the item's normal flat sprite and repaints every pixel of it, so a sword can run with lava
                    or a gem can hold a starfield.</p>
                    <h2>What you get</h2>
                    <table>
                    <tr><td><code>texel</code></td><td>the pixel's own colour from the texture, so you can keep the item's shading</td></tr>
                    <tr><td><code>uv</code></td><td>0 to 1 across the sprite, 0,0 is the top left corner</td></tr>
                    <tr><td><code>normal</code></td><td>the direction the item faces, worked out on the GPU</td></tr>
                    <tr><td><code>view</code></td><td>the direction you're looking</td></tr>
                    </table>
                    <h2>Your own texture</h2>
                    <p>Put a png with the same name next to the effect file, like <code>lava_flow.png</code> beside
                    <code>lava_flow.glsl</code>. Every pixel you can see in it runs through the material and see through pixels
                    stay see through. Without one you get a plain gem to try things on. Keep it 16 by 16, bigger textures
                    end up with the effect tiled across them.</p>
                    <h2>Tricks</h2>
                    <p><b>Stay blocky.</b> <code>(floor(uv * 16.0) + 0.5) / 16.0</code> snaps to the sprite's pixels, which makes
                    the effect sit nicely next to vanilla items. Cursed Flame does this.</p>
                    <p><b>Keep the shape.</b> Multiply by <code>texel.r</code> or the texel's brightness so the dark outline and
                    shading of the sprite still show through.</p>
                    <p><b>Depth.</b> Offset the uv by <code>view.xz</code> a little per layer and the layers slide past each other
                    as you turn, like the end portal. Star Cloth works this way.</p>
                    <div class="tip">Materials can't be placed or worn as a sky, use <code>/ns give</code> from the Skript or
                    any command that hands out the item.</div>
                    """),
            new Guide("Overlays", "drawing over the player's view", """
                    <h1>Overlays</h1>
                    <pre>vec4 overlay(vec2 screen, vec3 dir, float time)</pre>
                    <p class="lead">Lays colour over everything the player sees: rain on the lens, frost, a low health pulse,
                    the tint of a domain.</p>
                    <h2>What you get</h2>
                    <table>
                    <tr><td><code>screen</code></td><td>0,0 in the bottom left of the window to 1,1 in the top right</td></tr>
                    <tr><td><code>dir</code></td><td>the direction that pixel looks into the world</td></tr>
                    </table>
                    <p>Anything tied to <code>screen</code> sticks to the glass and follows your head. Anything placed with
                    <code>dir</code> stays put in the world when you turn, which is how the embers in Domain Veil hang in the air.</p>
                    <h2>Alpha</h2>
                    <p>Alpha is how much of the world you cover. Keep it low in the middle so the player can still play,
                    and return 0 wherever there's nothing at all.</p>
                    <h2>Aspect</h2>
                    <p><code>screen</code> is squashed into a square, so circles come out stretched on a wide window.
                    Multiply x by about 1.8 before measuring distances to keep them round.</p>
                    <h2>In game</h2>
                    <p>The overlay is an item in the offhand. The hand is drawn after the world, so it covers everything, and
                    nobody else can see it. <code>/ns overlay name</code> puts it on and keeps the old offhand item safe,
                    <code>/ns overlay off</code> gives it back.</p>
                    """),
            new Guide("Noise and randomness", "hash, fbm, warping and cells", """
                    <h1>Noise and randomness</h1>
                    <p class="lead">Shaders have no random function, so you make your own.</p>
                    <h2>Hash</h2>
                    <pre>float hash(vec3 p) {
                        return fract(sin(dot(p, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
                    }</pre>
                    <p>Turns any position into a random looking number from 0 to 1. Same input, same output, which is what you
                    want: a star stays put frame after frame.</p>
                    <h2>Value noise</h2>
                    <p>Hash the corners of a grid and blend between them with a smooth curve. Neighbouring points get similar
                    values, which gives soft blobs instead of static. Every example in the gallery has one you can copy.</p>
                    <h2>Fbm</h2>
                    <p>Add several layers of noise, each at about twice the detail and half the strength. Five layers is plenty
                    for most things. Rotating or offsetting each layer a little hides the grid.</p>
                    <h2>Ridges</h2>
                    <p><code>1.0 - abs(noise * 2.0 - 1.0)</code> turns soft blobs into thin bright lines, which is how the lightning,
                    plasma threads and mountain ridges in the examples are made. Raise it to a power to make the lines thinner.</p>
                    <h2>Warping</h2>
                    <p>Use noise to shift the position you feed into more noise and you get swirls and tendrils. Lesson 5 walks through it.</p>
                    <h2>Cells</h2>
                    <p><code>floor(p)</code> numbers the cells of a grid and <code>fract(p)</code> is the position inside one.
                    Hash the cell number to decide what each cell holds. Stars, rain, embers and petals are all this.</p>
                    """),
            new Guide("Color and light", "mixing, palettes and tone mapping", """
                    <h1>Color and light</h1>
                    <h2>Two ways to combine</h2>
                    <p><code>mix(a, b, t)</code> blends, like paint. <code>a + b</code> adds, like light. Skies are usually a mixed
                    base with light added on top: suns, glows, stars, lightning.</p>
                    <h2>Glow shapes</h2>
                    <table>
                    <tr><td><code>exp(-d * k)</code></td><td>soft glow, k sets how tight</td></tr>
                    <tr><td><code>pow(x, n)</code></td><td>sharpen a 0 to 1 value, big n for small hot spots</td></tr>
                    <tr><td><code>1.0 - smoothstep(0.0, w, d)</code></td><td>a crisp disc or line w wide</td></tr>
                    </table>
                    <p>A crisp core plus a wide soft halo, added together, reads as bright and hot.</p>
                    <h2>Palettes</h2>
                    <p><code>a + b * cos(6.28318 * (c * t + d))</code> with vec3 a, b, c and d turns one number into a smooth
                    run of colors. Lesson 6 has you write one.</p>
                    <h2>Tone mapping</h2>
                    <p>Anything over 1 clips. <code>1.0 - exp(-color * k)</code> squeezes everything back under 1 while keeping
                    the shape of highlights. Larger k makes everything brighter.</p>
                    <div class="tip">Keep one strong color idea per effect. The examples mostly use a dark base, one main
                    color and one accent.</div>
                    """),
            new Guide("Animation", "time, cycles and phases", """
                    <h1>Animation</h1>
                    <table>
                    <tr><td><code>sin(time * speed)</code></td><td>smooth back and forth between -1 and 1</td></tr>
                    <tr><td><code>0.5 + 0.5 * sin(...)</code></td><td>the same, but between 0 and 1</td></tr>
                    <tr><td><code>fract(time * speed)</code></td><td>a ramp from 0 to 1 that starts over, for things that repeat</td></tr>
                    <tr><td><code>floor(time * speed)</code></td><td>a counter that ticks up, hash it for a new random choice each cycle</td></tr>
                    <tr><td><code>p + vec3(time, 0, 0)</code></td><td>slide a pattern, for drifting clouds</td></tr>
                    </table>
                    <h2>Out of step</h2>
                    <p>When many things move, give each its own phase: <code>sin(time * 3.0 + hash(cell) * 50.0)</code>. Without
                    that every star blinks at once and it looks mechanical.</p>
                    <h2>Events</h2>
                    <p>For things that happen now and then, like lightning or a hit on a shield, split time into beats
                    with floor, hash each beat to decide if something happens, and use <code>exp(-fract(...) * k)</code> for a
                    sharp flash that fades.</p>
                    <h2>Easing</h2>
                    <p><code>t * t * (3.0 - 2.0 * t)</code> on a 0 to 1 value starts and ends slowly, which makes movement feel
                    natural. That's what smoothstep does inside.</p>
                    """),
            new Guide("GLSL cheat sheet", "types, swizzles and built ins", """
                    <h1>GLSL cheat sheet</h1>
                    <h2>Types</h2>
                    <table>
                    <tr><td><code>float int bool</code></td><td>one value</td></tr>
                    <tr><td><code>vec2 vec3 vec4</code></td><td>2, 3 or 4 floats</td></tr>
                    <tr><td><code>mat2 mat3</code></td><td>rotations, <code>mat2(c, -s, s, c)</code> rotates a vec2</td></tr>
                    </table>
                    <h2>Swizzles</h2>
                    <p><code>v.x v.y v.z</code> read parts, <code>v.xz</code> makes a vec2 from two of them, <code>v.rgb</code> is the
                    same as <code>v.xyz</code>, and you can write to them: <code>p.xz = rotate * p.xz;</code></p>
                    <h2>Built ins</h2>
                    <table>
                    <tr><td><code>mix(a, b, t)</code></td><td>blend from a to b</td></tr>
                    <tr><td><code>smoothstep(e0, e1, x)</code></td><td>0 below e0, 1 above e1, smooth between. keep e0 smaller than e1</td></tr>
                    <tr><td><code>step(edge, x)</code></td><td>0 below edge, 1 above</td></tr>
                    <tr><td><code>clamp(x, lo, hi)</code></td><td>keep x in range</td></tr>
                    <tr><td><code>fract floor mod</code></td><td>part after the dot, whole number below, wrap around</td></tr>
                    <tr><td><code>abs sign min max</code></td><td>the usual</td></tr>
                    <tr><td><code>dot cross</code></td><td>agreement of two vectors, a vector at right angles to both</td></tr>
                    <tr><td><code>length distance normalize</code></td><td>size, gap between two points, same direction at length 1</td></tr>
                    <tr><td><code>reflect refract</code></td><td>bounce a ray off a surface, bend it through glass</td></tr>
                    <tr><td><code>pow exp sqrt</code></td><td>powers, the glow curve, square root</td></tr>
                    <tr><td><code>sin cos atan</code></td><td>waves and angles</td></tr>
                    </table>
                    <h2>Gotchas</h2>
                    <p>Write floats with a dot, <code>1.0</code> and not <code>1</code>. <code>pow</code> with a negative base gives
                    nothing useful, wrap the base in <code>max(x, 0.0)</code>. Loops need a fixed number of steps, use
                    <code>break</code> to leave early. Some words are reserved and can't be variable names, like
                    <code>flat</code>, <code>sample</code>, <code>half</code> and <code>input</code>.</p>
                    """),
            new Guide("Making it fast", "what costs the most and how to cut it", """
                    <h1>Making it fast</h1>
                    <p class="lead">An effect runs for every pixel of the item, every frame, on every player's GPU.</p>
                    <h2>Watch the preview</h2>
                    <p>The preview shows its frame rate, and when an effect is slow it drops its own resolution and says so.
                    If you see the resolution go down, players on weaker hardware will feel it.</p>
                    <h2>What costs the most</h2>
                    <p>Loops multiply everything inside them. A 100 step march that calls 5 octaves of noise is 500 noise calls
                    per pixel. A sky that fills the screen at 1080p is two million pixels. Cut steps first, then octaves.</p>
                    <h2>Ways to cut</h2>
                    <table>
                    <tr><td>leave early</td><td><code>break</code> as soon as the answer is known: the ray hit, the cloud is solid, it left the box</td></tr>
                    <tr><td>skip empty space</td><td>don't march clouds below the horizon or noise where nothing shows</td></tr>
                    <tr><td>less detail far away</td><td>fewer octaves where the distance hides them anyway, like the far ocean</td></tr>
                    <tr><td>solve instead of march</td><td>spheres and flat planes have exact formulas, Ringed Planet uses no march at all</td></tr>
                    <tr><td>bigger steps</td><td>for glow you can step in fixed sizes and get away with fewer of them</td></tr>
                    </table>
                    """),
            new Guide("Debugging", "errors, black screens and odd colors", """
                    <h1>Debugging</h1>
                    <h2>Compile errors</h2>
                    <p>The preview compiles as you type. Errors show as red squiggles, a red dot by the line number and a list
                    in Problems. Click one to jump to it.</p>
                    <table>
                    <tr><td><b>undeclared identifier</b></td><td>a typo, or a variable used outside the braces it was made in</td></tr>
                    <tr><td><b>cannot convert from int to float</b></td><td>a number written without a dot, write 2.0 instead of 2</td></tr>
                    <tr><td><b>wrong operand types</b></td><td>mixing sizes, like a vec2 plus a vec3. check your swizzles</td></tr>
                    <tr><td><b>syntax error</b></td><td>usually a missing semicolon or bracket on the line above</td></tr>
                    <tr><td><b>needs one of ...</b></td><td>the file needs an entry function: <code>sky</code>, <code>object</code>, <code>surface</code>, <code>material</code> or <code>overlay</code></td></tr>
                    </table>
                    <h2>It compiles but looks wrong</h2>
                    <p><b>All black.</b> Something became 0 or negative, often a smoothstep with its edges the wrong way round, or a
                    pow of a negative number. <b>Flat white.</b> Colors over 1, add tone mapping. <b>Flickering black pixels.</b>
                    Usually a divide by zero or sqrt of a negative, guard them with max.</p>
                    <h2>See your numbers</h2>
                    <div class="tip">Return the value you're unsure about as a color: <code>return vec3(n);</code>. Black is 0, white
                    is 1. It's the fastest way to see what a noise, a mask or a distance is really doing.</div>
                    """),
            new Guide("Using it in game", "resource packs, commands and Skript", """
                    <h1>Using it in game</h1>
                    <h2>Loading the pack</h2>
                    <p>Build, then put the zip from the project's build folder into your resource packs folder, or send it from
                    a server. Any version from 1.21 to 26.3 can load it.</p>
                    <h2>Getting the items</h2>
                    <p>Right click an effect and choose <b>Give Command</b> to copy a /give for its item. A sky item has to be
                    huge and inside out around the player, so for skies the Skript is much easier.</p>
                    <h2>The Skript</h2>
                    <p>Turn on <b>Build &gt; Also Make a Skript</b> and every build also writes a .sk. Put it in
                    <code>plugins/Skript/scripts</code>. The command is named after your namespace:</p>
                    <table>
                    <tr><td><code>/ns sky &lt;name&gt; [player|all]</code></td><td>puts someone inside a sky that follows them</td></tr>
                    <tr><td><code>/ns off [player|all]</code></td><td>takes it away</td></tr>
                    <tr><td><code>/ns place &lt;name&gt; [size]</code></td><td>puts an object down in front of you</td></tr>
                    <tr><td><code>/ns size &lt;size&gt;</code></td><td>smoothly resizes the nearest object</td></tr>
                    <tr><td><code>/ns clear, give, list</code></td><td>remove nearby objects, get an item, see what's in the pack</td></tr>
                    </table>
                    <div class="tip">Tab suggestions need the SkBee addon. Rejoin after a reload so your client learns the new command.</div>
                    """));

    static JEditorPane page(String html) {
        JEditorPane pane = new JEditorPane() {
            // in a plain column the pane measures itself as if nothing wrapped and cuts the rest off,
            // so its height is worked out for the width it's actually given
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                int w = getWidth() > 0 ? getWidth() : getParent() != null ? getParent().getWidth() : 0;
                if (w <= 0) return d;
                javax.swing.text.View root = getUI().getRootView(this);
                root.setSize(w, Integer.MAX_VALUE);
                java.awt.Insets in = getInsets();
                return new Dimension(w, (int) Math.ceil(root.getPreferredSpan(javax.swing.text.View.Y_AXIS)) + in.top + in.bottom);
            }

            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        HTMLEditorKit kit = new HTMLEditorKit();
        kit.getStyleSheet().addRule(css());
        pane.setEditorKit(kit);
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setText("<html><body>" + html + "</body></html>");
        pane.setCaretPosition(0);
        return pane;
    }

    static JScrollPane scroller(JComponent c) {
        JScrollPane s = new JScrollPane(c);
        s.setBorder(null);
        s.setOpaque(false);
        s.getViewport().setOpaque(false);
        Fx.smooth(s, true);
        return s;
    }

    static JButton primary(String text) {
        return new Fx.Soft(text, true);
    }

    static JButton secondary(String text) {
        return new Fx.Soft(text, false);
    }

    private static JComponent left(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private static JPanel column() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        return p;
    }

    // follows the scroll pane's width so text wraps, but keeps its own height so nothing gets stretched
    static final class Page extends JPanel implements javax.swing.Scrollable {
        Page() {
            setOpaque(false);
        }

        Page(java.awt.LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        public int getScrollableUnitIncrement(java.awt.Rectangle r, int orientation, int direction) {
            return 18;
        }

        public int getScrollableBlockIncrement(java.awt.Rectangle r, int orientation, int direction) {
            return r.height - 40;
        }

        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    // a thin rounded bar that slides to its new value instead of jumping
    static final class Progress extends JComponent {
        private float shown;
        private float target;
        private final Timer tick = new Timer(10, null);

        Progress() {
            setPreferredSize(new Dimension(200, 6));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));

        }

        void set(float value) {
            target = Math.max(0, Math.min(1, value));
            shown = target;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int h = getHeight();
            g2.setColor(Theme.current.border());
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), h, h, h));
            if (shown > 0.001f) {
                g2.setColor(Theme.current.accent());
                g2.fill(new RoundRectangle2D.Float(0, 0, Math.max(h, getWidth() * shown), h, h, h));
            }
            g2.dispose();
        }
    }

    // the page a lesson card opens in the home screen's Learn section
    static String lessonHtml(Lesson lesson) {
        int n = LESSONS.indexOf(lesson) + 1;
        StringBuilder html = new StringBuilder("<p class='dim'>LESSON " + n + " OF " + LESSONS.size() + "</p><h1>" + lesson.title()
                + "</h1><p class='lead'>" + lesson.steps().size() + " tasks &nbsp;&middot;&nbsp; about " + lesson.minutes() + " minutes"
                + (finished(lesson) ? " &nbsp;&middot;&nbsp; done" : "") + "</p>" + lesson.theory() + "<h2>What you'll do</h2>");
        int k = 1;
        for (Step s : lesson.steps()) html.append("<p><b>").append(k++).append(".</b>&nbsp; ").append(s.task()).append("</p>");
        html.append("<div class='tip'>The code opens in the editor with the preview running. The lesson takes over the left side,"
                + " and each task ticks off the moment your code does it.</div>");
        return html.toString();
    }

    static void resetProgress() {
        for (Lesson l : LESSONS) {
            PREFS.remove("done." + l.id());
            forget(l);
        }
    }

    private static String markKey(Lesson lesson, int step) {
        return "marked." + lesson.id() + "." + step;
    }

    private static void forget(Lesson lesson) {
        for (int i = 0; i < lesson.steps().size(); i++) PREFS.remove(markKey(lesson, i));
    }

    // takes over the left side of the window while a lesson is open and ticks the tasks off as the code changes
    static final class Panel extends JPanel {
        record Actions(Runnable reset, Runnable exit, Runnable next) {
        }

        private final Lesson lesson;
        private final List<Tick> ticks = new ArrayList<>();
        private final List<JComponent> details = new ArrayList<>();
        private final JLabel count = new JLabel();
        private final Progress bar = new Progress();
        private final JButton nextButton = primary("Next lesson");
        private final JComponent doneNote;
        private boolean finishedOnce;
        private String lastSource = "";

        Panel(Lesson lesson, Actions actions) {
            super(new BorderLayout());
            this.lesson = lesson;
            setBackground(Fx.SIDE);

            JPanel head = column();
            head.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
            int n = LESSONS.indexOf(lesson) + 1;
            JLabel kicker = new JLabel("LESSON " + n + " OF " + LESSONS.size());
            kicker.setFont(kicker.getFont().deriveFont(Font.BOLD, 10f));
            kicker.setForeground(Fx.DIM);
            JLabel title = new JLabel(lesson.title());
            title.setFont(title.getFont().deriveFont(Font.BOLD, 17f));
            title.setForeground(Fx.WHITE);
            count.setForeground(Fx.DIM);
            head.add(left(kicker));
            head.add(Box.createVerticalStrut(3));
            head.add(left(title));
            head.add(Box.createVerticalStrut(10));
            head.add(left(bar));
            head.add(Box.createVerticalStrut(6));
            head.add(left(count));
            add(head, BorderLayout.NORTH);

            JPanel body = new Page();
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            body.setBorder(BorderFactory.createEmptyBorder(0, 14, 14, 14));
            JEditorPane theory = page(lesson.theory());
            JButton about = secondary("Hide the explanation");
            about.addActionListener(e -> {
                theory.setVisible(!theory.isVisible());
                about.setText(theory.isVisible() ? "Hide the explanation" : "About this lesson");
                body.revalidate();
            });
            body.add(left(about));
            body.add(Box.createVerticalStrut(8));
            body.add(left(theory));
            body.add(Box.createVerticalStrut(10));

            for (Step s : lesson.steps()) {
                Tick t = new Tick(s.task());
                ticks.add(t);
                body.add(left(t));
                JComponent detail = detail(s, ticks.size() - 1);
                detail.setVisible(false);
                details.add(detail);
                body.add(left(detail));
            }
            doneNote = challenge(lesson);
            doneNote.setVisible(false);
            body.add(Box.createVerticalStrut(10));
            body.add(left(doneNote));
            add(scroller(body), BorderLayout.CENTER);

            JButton reset = secondary("Reset code");
            reset.addActionListener(e -> {
                forget(lesson);
                actions.reset().run();
            });
            JButton exit = secondary("Exit");
            exit.addActionListener(e -> actions.exit().run());
            nextButton.addActionListener(e -> actions.next().run());
            nextButton.setVisible(false);
            JPanel foot = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            foot.setOpaque(false);
            foot.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Fx.LINE),
                    BorderFactory.createEmptyBorder(10, 6, 2, 6)));
            foot.add(reset);
            foot.add(exit);
            foot.add(nextButton);
            add(foot, BorderLayout.SOUTH);
        }

        private JComponent detail(Step s, int index) {
            JPanel box = column();
            box.setBorder(BorderFactory.createEmptyBorder(2, 34, 12, 4));
            JTextArea hint = text(s.hint(), getFont().deriveFont(12.5f), Fx.DIM);
            JTextArea code = text(s.example(), CodeEditor.mono(12), Fx.TEXT);
            code.setOpaque(true);
            code.setBackground(Fx.t().editor());
            code.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            code.setVisible(false);
            JButton show = secondary("Show example");
            JButton copy = secondary("Copy");
            copy.setVisible(false);
            show.addActionListener(e -> {
                code.setVisible(!code.isVisible());
                copy.setVisible(code.isVisible());
                show.setText(code.isVisible() ? "Hide example" : "Show example");
                box.revalidate();
            });
            copy.addActionListener(e -> {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(s.example()), null);
                Fx.toast((JFrame) SwingUtilities.getWindowAncestor(this), "Copied the example", false, null, null);
            });
            // the checks read the code, not the picture, so they can miss a right answer written a different way
            JButton mine = secondary("It works, mark done");
            mine.setToolTipText("Use this when the preview shows what the task asks for but the check hasn't ticked");
            mine.addActionListener(e -> {
                PREFS.putBoolean(markKey(lesson, index), true);
                check(lastSource);
            });
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            buttons.setOpaque(false);
            buttons.add(show);
            buttons.add(Box.createHorizontalStrut(6));
            buttons.add(copy);
            buttons.add(Box.createHorizontalStrut(6));
            buttons.add(mine);
            box.add(left(hint));
            box.add(Box.createVerticalStrut(8));
            box.add(left(buttons));
            box.add(Box.createVerticalStrut(8));
            box.add(left(code));
            return box;
        }

        Lesson lesson() {
            return lesson;
        }

        private static JComponent challenge(Lesson lesson) {
            JPanel box = column();
            box.setOpaque(true);
            box.setBackground(Fx.t().editor());
            box.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
            JLabel head = new JLabel("CHALLENGE");
            head.setFont(head.getFont().deriveFont(Font.BOLD, 10f));
            head.setForeground(Fx.WHITE);
            JTextArea task = text(CHALLENGES.getOrDefault(lesson.id(), "Keep playing with it, the preview follows every change."), new JLabel().getFont().deriveFont(13f), Fx.TEXT);
            JTextArea note = text("Every task is done. This one has no example and no check, try it on your own.", new JLabel().getFont().deriveFont(12f), Fx.DIM);
            box.add(left(head));
            box.add(Box.createVerticalStrut(6));
            box.add(left(task));
            box.add(Box.createVerticalStrut(8));
            box.add(left(note));
            return box;
        }

        void check(String source) {
            lastSource = source;
            int done = 0;
            int current = -1;
            for (int i = 0; i < ticks.size(); i++) {
                boolean ok = PREFS.getBoolean(markKey(lesson, i), false) || lesson.steps().get(i).done().test(source);
                ticks.get(i).set(ok);
                if (ok) done++;
                else if (current < 0) current = i;
            }
            for (int i = 0; i < ticks.size(); i++) {
                ticks.get(i).current(i == current);
                details.get(i).setVisible(i == current);
            }
            count.setText(done + " of " + ticks.size() + " tasks done");
            bar.set(done / (float) ticks.size());
            boolean all = current < 0;
            if (all && !finishedOnce) {
                markFinished(lesson);
                java.awt.Window w = SwingUtilities.getWindowAncestor(this);
                if (w instanceof JFrame f) Fx.toast(f, "Lesson complete: " + lesson.title(), false, null, null);
            }
            finishedOnce = all;
            doneNote.setVisible(all);
            nextButton.setVisible(all && LESSONS.indexOf(lesson) < LESSONS.size() - 1);
            revalidate();
            repaint();
        }

        // a wrapping text area works out its height from its width, so it gets the sidebar's narrowest width up front
        private static JTextArea text(String s, Font font, Color color) {
            JTextArea t = new JTextArea(s) {
                @Override
                public Dimension getMaximumSize() {
                    return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
                }
            };
            t.setFont(font);
            t.setForeground(color);
            t.setLineWrap(true);
            t.setWrapStyleWord(true);
            t.setEditable(false);
            t.setOpaque(false);
            t.setBorder(null);
            t.setSize(340, Short.MAX_VALUE);
            return t;
        }
    }

    // a task row whose circle fills in with a quick pop when it's done
    private static final class Tick extends JComponent {
        private final String text;
        private boolean done;
        private boolean current;
        private float fill;

        Tick(String text) {
            this.text = text;
            setPreferredSize(new Dimension(360, 32));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        }

        void set(boolean ok) {
            if (ok == done) return;
            done = ok;
            fill = ok ? 1f : 0f;
            repaint();
        }

        void current(boolean c) {
            if (c == current) return;
            current = c;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (current) {
                g2.setColor(Theme.current.hover());
                g2.fill(new RoundRectangle2D.Float(0, 1, getWidth(), getHeight() - 2, 8, 8));
            }
            float r = 8;
            Theme t = Theme.current;
            float cx = 15, cy = getHeight() / 2f;
            g2.setStroke(new BasicStroke(1.4f));
            g2.setColor(current ? t.strong() : t.faint());
            g2.draw(new Ellipse2D.Float(cx - 8, cy - 8, 16, 16));
            if (fill > 0.01f) {
                g2.setColor(t.good());
                g2.fill(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
                g2.setColor(t.dark() ? Color.BLACK : Color.WHITE);
                g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine((int) cx - 4, (int) cy, (int) cx - 1, (int) cy + 3);
                g2.drawLine((int) cx - 1, (int) cy + 3, (int) cx + 4, (int) cy - 3);
            }
            g2.setFont(getFont().deriveFont(current ? Font.BOLD : Font.PLAIN, 13f));
            g2.setColor(done ? t.dim() : current ? t.strong() : t.text());
            g2.drawString(text, 34, cy + 5);
            g2.dispose();
        }
    }
}
