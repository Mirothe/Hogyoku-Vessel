package dev.mirothe.vessel;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.awt.AWTGLCanvas;
import org.lwjgl.opengl.awt.GLData;

import javax.swing.SwingUtilities;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.lwjgl.opengl.GL33.*;

final class Preview extends AWTGLCanvas {
    record Problem(int line, String message) {
    }

    private static final Pattern ERROR_LINE = Pattern.compile("(?:\\d+[:(](\\d+)\\)?|ERROR: \\d+:(\\d+))[^:]*:\\s*(.*)");

    private final Consumer<List<Problem>> problems;
    private final long started = System.nanoTime();
    private final FloatBuffer view = BufferUtils.createFloatBuffer(9);

    private int program, vao;
    private int timeLoc, aspectLoc, viewLoc;
    private volatile String pendingSource;
    private volatile int pendingHead;
    private volatile int pendingLines;
    private volatile String pendingKind = "sky";
    private float yaw, pitch = 15;
    private int dragX, dragY;
    private final java.util.concurrent.atomic.AtomicBoolean queued = new java.util.concurrent.atomic.AtomicBoolean();
    private int fbo, target, targetW, targetH;
    private float scale = 1;
    private int frames;
    private long statsSince = System.nanoTime();
    private Consumer<String> stats = s -> { };
    private volatile Consumer<java.awt.image.BufferedImage> shot;
    private volatile long shotAt;

    Preview(Consumer<List<Problem>> problems) {
        super(settings());
        this.problems = problems;
        MouseAdapter drag = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragX = e.getX();
                dragY = e.getY();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                yaw += (e.getX() - dragX) * 0.3f;
                pitch = Math.max(-89, Math.min(89, pitch + (e.getY() - dragY) * 0.3f));
                dragX = e.getX();
                dragY = e.getY();
            }
        };
        addMouseListener(drag);
        addMouseMotionListener(drag);
        // a swing timer can't go much past 60, so a small thread keeps one frame queued on the EDT at a time
        Thread pump = new Thread(() -> {
            while (true) {
                if (!queued.getAndSet(true)) {
                    SwingUtilities.invokeLater(() -> {
                        try {
                            if (isShowing()) render();
                        } finally {
                            queued.set(false);
                        }
                    });
                }
                try {
                    Thread.sleep(4);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "preview frames");
        pump.setDaemon(true);
        pump.start();
    }

    // the sky needs a moment to settle, a frame straight after compiling can still be the old shader
    void shoot(Consumer<java.awt.image.BufferedImage> done) {
        shotAt = System.nanoTime() + 1_500_000_000L;
        shot = done;
    }

    private java.awt.image.BufferedImage frame(int w, int h) {
        java.nio.ByteBuffer px = BufferUtils.createByteBuffer(w * h * 4);
        glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, px);
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = (y * w + x) * 4;
                img.setRGB(x, h - 1 - y, (px.get(i) & 255) << 16 | (px.get(i + 1) & 255) << 8 | px.get(i + 2) & 255);
            }
        }
        return img;
    }

    void onStats(Consumer<String> listener) {
        stats = listener;
    }

    private static GLData settings() {
        GLData data = new GLData();
        data.majorVersion = 3;
        data.minorVersion = 3;
        data.profile = GLData.Profile.CORE;
        // a Mac only hands out OpenGL 3.3 to a forward compatible core context, everyone else doesn't mind
        data.forwardCompatible = true;
        data.swapInterval = 0;
        return data;
    }

    void show(String effectSource, Map<String, String> defines) {
        StringBuilder head = new StringBuilder();
        defines.forEach((k, v) -> head.append("#define ").append(k).append(' ').append(v).append('\n'));
        float[] size = Obfuscator.shapeSize(effectSource);
        head.append("#define SHAPE_SIZE vec3(").append(size[0]).append(", ").append(size[1]).append(", ").append(size[2]).append(")\n");
        effectSource = Obfuscator.withoutShape(effectSource);
        pendingHead = Templates.headLines(head.toString());
        pendingLines = (int) effectSource.chars().filter(c -> c == '\n').count() + 1;
        pendingKind = Obfuscator.kind(effectSource);
        String tail = switch (pendingKind) {
            case "object" -> Templates.PREVIEW_OBJECT_TAIL;
            case "surface" -> Templates.PREVIEW_SURFACE_TAIL;
            case "material" -> Templates.PREVIEW_MATERIAL_TAIL;
            case "overlay" -> Templates.PREVIEW_OVERLAY_TAIL;
            default -> Templates.PREVIEW_TAIL;
        };
        pendingSource = Templates.PREVIEW_HEAD + head + effectSource + tail;
    }

    @Override
    public void initGL() {
        GL.createCapabilities();
        vao = glGenVertexArrays();
    }

    @Override
    public void paintGL() {
        String src = pendingSource;
        if (src != null) {
            pendingSource = null;
            compile(src, pendingHead, pendingLines, pendingKind);
        }
        int w = getFramebufferWidth(), h = getFramebufferHeight();
        int sw = Math.max(1, Math.round(w * scale)), sh = Math.max(1, Math.round(h * scale));
        if (sw != targetW || sh != targetH) resizeTarget(sw, sh);

        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glViewport(0, 0, sw, sh);
        glClearColor(0.05f, 0.05f, 0.05f, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        if (program != 0) {
            glUseProgram(program);
            glUniform1f(timeLoc, (System.nanoTime() - started) / 1e9f);
            glUniform1f(aspectLoc, w / (float) Math.max(1, h));
            glUniformMatrix3fv(viewLoc, false, viewMatrix());
            glBindVertexArray(vao);
            glDrawArrays(GL_TRIANGLES, 0, 3);
        }
        glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo);
        Consumer<java.awt.image.BufferedImage> take = shot;
        if (take != null && program != 0 && pendingSource == null && System.nanoTime() > shotAt) {
            shot = null;
            take.accept(frame(sw, sh));
        }
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
        glBlitFramebuffer(0, 0, sw, sh, 0, 0, w, h, GL_COLOR_BUFFER_BIT, GL_LINEAR);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        swapBuffers();
        countFrame();
    }

    // heavy shaders render at a lower resolution and get scaled up, light ones go back to full size
    private void countFrame() {
        frames++;
        long now = System.nanoTime();
        if (now - statsSince < 500_000_000L) return;
        float fps = frames * 1e9f / (now - statsSince);
        frames = 0;
        statsSince = now;
        if (fps < 55 && scale > 0.5f) scale -= 0.125f;
        else if (fps > 110 && scale < 1f) scale += 0.125f;
        stats.accept(Math.round(fps) + " fps" + (scale < 1 ? "   ·   " + Math.round(scale * 100) + "% resolution" : ""));
    }

    private void resizeTarget(int w, int h) {
        if (fbo == 0) {
            fbo = glGenFramebuffers();
            target = glGenTextures();
        }
        glBindTexture(GL_TEXTURE_2D, target);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, target, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        targetW = w;
        targetH = h;
    }

    private FloatBuffer viewMatrix() {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        float cy = (float) Math.cos(y), sy = (float) Math.sin(y), cp = (float) Math.cos(p), sp = (float) Math.sin(p);
        view.clear();
        view.put(new float[]{cy, 0, -sy, sy * sp, cp, cy * sp, sy * cp, -sp, cy * cp});
        view.flip();
        return view;
    }

    private void compile(String fragment, int headLines, int userLines, String kind) {
        List<Problem> found = new ArrayList<>();
        int vs = shader(GL_VERTEX_SHADER, Templates.PREVIEW_VERTEX, 0, found);
        int fs = shader(GL_FRAGMENT_SHADER, fragment, headLines, found);
        // an error past the last line is in the preview's own code, which only breaks when the effect's main function is missing or different
        if (found.removeIf(problem -> problem.line() > userLines)) {
            found.add(0, new Problem(0, kind.equals("sky")
                    ? "No main function found. A sky needs vec3 sky(vec3 dir, float time), an object needs vec4 object(vec3 pos, vec3 dir, float time)"
                    : "The " + kind + " function doesn't take the inputs the preview gives it, check its parameters against the " + kind + " template"));
        }
        if (found.isEmpty()) {
            int p = glCreateProgram();
            glAttachShader(p, vs);
            glAttachShader(p, fs);
            glLinkProgram(p);
            if (glGetProgrami(p, GL_LINK_STATUS) == GL_TRUE) {
                if (program != 0) glDeleteProgram(program);
                program = p;
                timeLoc = glGetUniformLocation(p, "uTime");
                aspectLoc = glGetUniformLocation(p, "uAspect");
                viewLoc = glGetUniformLocation(p, "uView");
            } else {
                found.add(new Problem(0, glGetProgramInfoLog(p).strip()));
                glDeleteProgram(p);
            }
        }
        glDeleteShader(vs);
        glDeleteShader(fs);
        SwingUtilities.invokeLater(() -> problems.accept(found));
    }

    private static int shader(int type, String src, int headLines, List<Problem> found) {
        int s = glCreateShader(type);
        glShaderSource(s, src);
        glCompileShader(s);
        if (glGetShaderi(s, GL_COMPILE_STATUS) != GL_TRUE) {
            for (String line : glGetShaderInfoLog(s).split("\\R")) {
                if (line.isBlank()) continue;
                Matcher m = ERROR_LINE.matcher(line);
                int at = 0;
                String msg = line.strip();
                if (m.find()) {
                    String n = m.group(1) != null ? m.group(1) : m.group(2);
                    at = Integer.parseInt(n) - headLines;
                    msg = m.group(3).strip();
                }
                found.add(new Problem(Math.max(0, at), msg));
            }
        }
        return s;
    }
}
