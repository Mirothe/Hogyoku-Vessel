// rain on the camera: little drops that bead up and dry out, plus a few bigger ones running down and leaving trails.
// it can't bend the world behind it, so each drop is a dark body with light catching its rim

// screen goes from 0,0 bottom left to 1,1 top right, dir is where you look

float rlHash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

vec4 overlay(vec2 screen, vec3 dir, float time) {
    // screen is squashed to a square, stretching it back out keeps the drops round on a normal widescreen
    vec2 p = screen * vec2(1.8, 1.0);
    float body = 0.0;
    float light = 0.0;

    for (int layer = 0; layer < 2; layer++) {
        float scale = layer == 0 ? 7.0 : 13.0;
        vec2 g = p * scale;
        vec2 cell = floor(g);
        vec2 f = fract(g) - 0.5;
        float h = rlHash(cell + float(layer) * 31.0);
        vec2 off = (vec2(rlHash(cell + 1.7), rlHash(cell + 4.3)) - 0.5) * 0.5;
        float life = fract(time * 0.12 + h);
        float r = (0.1 + 0.14 * h) * smoothstep(0.0, 0.08, life) * (1.0 - smoothstep(0.6, 1.0, life)) * step(0.35, h);
        float d = length(f - off);
        float drop = 1.0 - smoothstep(r * 0.75, r, d);
        body += drop;
        light += drop * smoothstep(r * 0.35, r, d) * (0.6 + 0.4 * clamp((f.y - off.y) / max(r, 0.001), 0.0, 1.0));
    }

    for (int i = 0; i < 6; i++) {
        float fi = float(i);
        float x = rlHash(vec2(fi, 9.0)) * 1.8;
        float y = 1.15 - fract(time * (0.08 + 0.08 * rlHash(vec2(fi, 3.0))) + rlHash(vec2(fi, 5.0))) * 1.4;
        x += sin(y * 14.0 + fi) * 0.01;
        vec2 q = p - vec2(x, y);
        float d = length(q * vec2(1.0, 0.8));
        float trail = (1.0 - smoothstep(0.002, 0.006, abs(q.x))) * step(0.0, q.y) * exp(-q.y * 5.0);
        body += (1.0 - smoothstep(0.016, 0.022, d)) + trail * 0.5;
        light += (1.0 - smoothstep(0.0, 0.006, abs(d - 0.016))) * 0.9 + trail * 0.25;
    }

    vec3 color = mix(vec3(0.04, 0.06, 0.09), vec3(0.85, 0.92, 1.0), clamp(light * 1.5, 0.0, 1.0));
    float alpha = clamp(0.04 + body * 0.22 + light * 0.6, 0.0, 0.75);
    return vec4(color, alpha);
}
