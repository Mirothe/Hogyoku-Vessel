// real Vessel effects compiled with WebGL2 and wrapped the same way the app's preview wraps them.
// one renderer runs the sky behind the title, another runs the editor further down

const HEAD = `#version 300 es
precision highp float;
precision highp int;
in vec2 screen;
out vec4 outColor;
uniform float uTime;
uniform float uAspect;
uniform mat3 uView;
float intro = 1.0;
`;

const TAILS = {
  sky: `
void main() {
    float f = 1.0 / tan(radians(35.0));
    vec3 dir = normalize(uView * vec3(screen.x * uAspect, screen.y, -f));
    outColor = vec4(clamp(sky(dir, uTime), 0.0, 1.0), 1.0);
}
`,
  object: `
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
    vec3 back = vec3(0.0);
    vec2 hit = previewBox(ro, rd);
    if (hit.x < hit.y && hit.y > 0.0) {
        vec4 o = object(ro + rd * max(hit.x, 0.0), rd, uTime);
        back = mix(back, clamp(o.rgb, 0.0, 1.0), clamp(o.a, 0.0, 1.0));
    }
    outColor = vec4(back, 1.0);
}
`,
};

const VERTEX = `#version 300 es
out vec2 screen;
void main() {
    vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    screen = p * 2.0 - 1.0;
    gl_Position = vec4(screen, 0.0, 1.0);
}
`;

const still = matchMedia("(prefers-reduced-motion: reduce)").matches;
const sources = {};

async function source(file) {
  if (sources[file] === undefined) {
    const res = await fetch(`shaders/${file}.glsl`);
    sources[file] = res.ok ? await res.text() : "// couldn't load this example\n";
  }
  return sources[file];
}

// one canvas, one program at a time. compile() hands back null when it worked, or the problem to show
function renderer(canvas, { scale = 1.5 } = {}) {
  const gl = canvas.getContext("webgl2", { antialias: false });
  if (!gl) return null;
  gl.bindVertexArray(gl.createVertexArray());
  const r = { yaw: 0, pitch: 4, visible: true, program: null, started: performance.now() };
  let where = {};

  function shader(type, src) {
    const s = gl.createShader(type);
    gl.shaderSource(s, src);
    gl.compileShader(s);
    return s;
  }

  r.compile = (code, kind) => {
    const headLines = HEAD.split("\n").length - 1;
    const vs = shader(gl.VERTEX_SHADER, VERTEX);
    const fs = shader(gl.FRAGMENT_SHADER, HEAD + code + TAILS[kind]);
    if (!gl.getShaderParameter(fs, gl.COMPILE_STATUS)) {
      const log = gl.getShaderInfoLog(fs) || "";
      gl.deleteShader(vs);
      gl.deleteShader(fs);
      const m = /ERROR:\s*\d+:(\d+):\s*(.*)/.exec(log);
      const line = m ? Number(m[1]) - headLines : 0;
      const userLines = code.split("\n").length;
      if (line > userLines) {
        return kind === "sky" ? "No main function found. A sky needs vec3 sky(vec3 dir, float time)"
          : "No main function found. An object needs vec4 object(vec3 origin, vec3 dir, float time)";
      }
      const message = m ? m[2].trim() : log.trim().split("\n")[0];
      return line > 0 ? `Line ${line}: ${message}` : message;
    }
    const p = gl.createProgram();
    gl.attachShader(p, vs);
    gl.attachShader(p, fs);
    gl.linkProgram(p);
    gl.deleteShader(vs);
    gl.deleteShader(fs);
    if (!gl.getProgramParameter(p, gl.LINK_STATUS)) {
      const problem = gl.getProgramInfoLog(p).trim();
      gl.deleteProgram(p);
      return problem;
    }
    if (r.program) gl.deleteProgram(r.program);
    r.program = p;
    where = { time: gl.getUniformLocation(p, "uTime"), aspect: gl.getUniformLocation(p, "uAspect"), view: gl.getUniformLocation(p, "uView") };
    if (still) r.draw();
    return null;
  };

  r.draw = () => {
    if (!r.program) return;
    const s = Math.min(devicePixelRatio || 1, scale);
    const w = Math.max(1, Math.round(canvas.clientWidth * s));
    const h = Math.max(1, Math.round(canvas.clientHeight * s));
    if (canvas.width !== w || canvas.height !== h) {
      canvas.width = w;
      canvas.height = h;
    }
    const y = r.yaw * Math.PI / 180, p = r.pitch * Math.PI / 180;
    const cy = Math.cos(y), sy = Math.sin(y), cp = Math.cos(p), sp = Math.sin(p);
    gl.viewport(0, 0, w, h);
    gl.useProgram(r.program);
    gl.uniform1f(where.time, still ? 6.0 : (performance.now() - r.started) / 1000);
    gl.uniform1f(where.aspect, w / h);
    gl.uniformMatrix3fv(where.view, false, new Float32Array([cy, 0, -sy, sy * sp, cp, cy * sp, sy * cp, -sp, cy * cp]));
    gl.drawArrays(gl.TRIANGLES, 0, 3);
  };

  new IntersectionObserver(([e]) => (r.visible = e.isIntersecting)).observe(canvas);
  addEventListener("resize", () => still && r.draw());
  return r;
}

const running = [];

function loop() {
  if (!document.hidden) for (const r of running) if (r.visible) r.draw();
  requestAnimationFrame(loop);
}

// the full screen sky behind the title. it drifts a little with the mouse so it reads as live, not a video
async function hero() {
  const canvas = document.querySelector(".cinema canvas");
  if (!canvas) return;
  const r = renderer(canvas, { scale: 1 });
  if (!r) return;
  r.pitch = 6;
  const problem = r.compile(await source("sunset"), "sky");
  if (problem) return;
  canvas.classList.add("live");
  running.push(r);
  if (!still) {
    addEventListener("pointermove", (e) => {
      r.yaw = (e.clientX / innerWidth - 0.5) * -6;
      r.pitch = 6 + (e.clientY / innerHeight - 0.5) * -3;
    });
  }
}

const KEYWORDS = new Set(["float", "int", "bool", "void", "vec2", "vec3", "vec4", "mat2", "mat3", "mat4", "return", "if", "else",
  "for", "while", "break", "continue", "const", "in", "out", "inout", "struct", "true", "false"]);

function escape(s) {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

// a small highlighter in the app's black and white style
function highlight(src) {
  const out = [];
  const re = /(\/\/[^\n]*)|(#\w+)|(\b\d+\.?\d*(?:e-?\d+)?\b|\.\d+\b)|([A-Za-z_]\w*)(?=\s*\()|([A-Za-z_]\w*)|([\s\S])/g;
  let m;
  while ((m = re.exec(src))) {
    if (m[1]) out.push(`<span class="tok-c">${escape(m[1])}</span>`);
    else if (m[2]) out.push(`<span class="tok-k">${escape(m[2])}</span>`);
    else if (m[3]) out.push(`<span class="tok-n">${m[3]}</span>`);
    else if (m[4]) out.push(KEYWORDS.has(m[4]) ? `<span class="tok-k">${m[4]}</span>` : `<span class="tok-f">${m[4]}</span>`);
    else if (m[5]) out.push(KEYWORDS.has(m[5]) ? `<span class="tok-k">${m[5]}</span>` : m[5]);
    else out.push(escape(m[6]));
  }
  return out.join("") + "\n";
}

function editor() {
  const studio = document.querySelector(".studio");
  if (!studio) return;
  const area = studio.querySelector("textarea");
  const paint = studio.querySelector(".paint");
  const canvas = studio.querySelector("canvas");
  const status = studio.querySelector(".status");
  const tabs = [...studio.querySelectorAll(".tabs button")];
  const r = renderer(canvas);
  if (!r) {
    status.textContent = "Your browser doesn't support WebGL2, so the live preview can't run here.";
    status.classList.add("bad");
    return;
  }
  const edits = {};
  let current = null;
  let timer = 0;

  const repaint = () => {
    paint.innerHTML = highlight(area.value);
    paint.scrollTop = area.scrollTop;
    paint.scrollLeft = area.scrollLeft;
  };

  const compile = () => {
    if (!current) return;
    const problem = r.compile(area.value, current.dataset.kind);
    status.textContent = problem || "No problems";
    status.classList.toggle("bad", !!problem);
  };

  async function open(tab) {
    if (current) edits[current.dataset.file] = area.value;
    tabs.forEach((t) => t.setAttribute("aria-selected", String(t === tab)));
    current = tab;
    const file = tab.dataset.file;
    area.value = edits[file] ?? await source(file);
    area.scrollTop = 0;
    if (tab.dataset.kind === "sky") {
      r.yaw = 0;
      r.pitch = 4;
    } else {
      r.yaw = 30;
      r.pitch = -18;
    }
    r.started = performance.now();
    repaint();
    compile();
  }

  area.addEventListener("input", () => {
    repaint();
    clearTimeout(timer);
    timer = setTimeout(compile, 250);
  });
  area.addEventListener("scroll", () => {
    paint.scrollTop = area.scrollTop;
    paint.scrollLeft = area.scrollLeft;
  });
  // tab indents like an editor, escape lets keyboard users move on
  area.addEventListener("keydown", (e) => {
    if (e.key === "Tab" && !e.shiftKey) {
      e.preventDefault();
      document.execCommand("insertText", false, "    ");
    } else if (e.key === "Escape") {
      area.blur();
    }
  });
  tabs.forEach((t) => t.addEventListener("click", () => open(t)));

  let drag = null;
  canvas.addEventListener("pointerdown", (e) => {
    drag = { x: e.clientX, y: e.clientY, yaw: r.yaw, pitch: r.pitch };
    canvas.setPointerCapture(e.pointerId);
  });
  canvas.addEventListener("pointermove", (e) => {
    if (!drag) return;
    r.yaw = drag.yaw - (e.clientX - drag.x) * 0.3;
    r.pitch = Math.max(-80, Math.min(80, drag.pitch + (e.clientY - drag.y) * 0.3));
    if (still) r.draw();
  });
  canvas.addEventListener("pointerup", () => (drag = null));

  running.push(r);
  // a link like #tesseract opens that example
  open(tabs.find((t) => "#" + t.dataset.file === location.hash) || tabs[0]);
}

hero();
editor();
if (!still) requestAnimationFrame(loop);
