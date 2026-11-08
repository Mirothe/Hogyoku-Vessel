// a huge glowing sigil spread over the sky. the dome gets flattened into a disk and everything on it is 2D distance math:
// rings of runes, a heptagram with pulses running along its lines, circles on its points, an eye in the middle,
// spiral motes drifting in, and a rippled reflection of the whole thing below the horizon

float asHash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float asNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(asHash(i), asHash(i + vec2(1.0, 0.0)), f.x), mix(asHash(i + vec2(0.0, 1.0)), asHash(i + vec2(1.0, 1.0)), f.x), f.y);
}

// a crisp core with a soft halo around it
float asGlow(float d, float width) {
    return (1.0 - smoothstep(0.0, width, d)) + exp(-d / (width * 6.0)) * 0.3;
}

// distance to a segment, and how far along it the closest point is in y
vec2 asSegment(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return vec2(length(pa - ba * h), h);
}

vec2 asCorner(float k, float points, float radius, float spin) {
    float a = spin + k / points * 6.2831853;
    return vec2(cos(a), sin(a)) * radius;
}

// joins every point on a circle to the one `skip` places further round, 7 and 3 gives a heptagram.
// y comes back as a pulse that runs along each line
vec2 asStar(vec2 p, float radius, float points, float skip, float spin, float time) {
    float best = 1e5;
    float pulse = 0.0;
    for (int k = 0; k < 12; k++) {
        float fk = float(k);
        if (fk >= points) break;
        vec2 s = asSegment(p, asCorner(fk, points, radius, spin), asCorner(fk + skip, points, radius, spin));
        best = min(best, s.x);
        float head = fract(time * 0.35 + fk / points);
        pulse = max(pulse, exp(-s.x * 400.0) * exp(-abs(s.y - head) * 30.0));
    }
    return vec2(best, pulse);
}

// strokes and arcs on a small grid, different for every slot
float asRune(vec2 g, float id) {
    float best = 1e5;
    for (int k = 0; k < 3; k++) {
        float fk = float(k);
        vec2 a = floor(vec2(asHash(vec2(id, fk)), asHash(vec2(fk, id))) * 3.0) / 2.0 - 0.5;
        vec2 b = floor(vec2(asHash(vec2(id + 7.0, fk)), asHash(vec2(fk, id + 7.0))) * 3.0) / 2.0 - 0.5;
        if (length(a - b) < 0.1) b = -a;
        best = min(best, asSegment(g, a * 0.7, b * 0.7).x);
    }
    if (asHash(vec2(id, 9.0)) > 0.55) best = min(best, abs(length(g - vec2(0.0, 0.15)) - 0.2));
    return best;
}

float asRuneRing(vec2 p, float r, float angle, float radius, float slots, float spin, float height) {
    float slot = (angle + spin) / 6.2831853 * slots;
    float id = mod(floor(slot), slots) + radius * 100.0;
    vec2 g = vec2((fract(slot) - 0.5) * 1.6, (r - radius) / height);
    if (abs(g.y) > 0.8) return 0.0;
    return asGlow(asRune(g, id) * height, 0.003);
}

vec3 sky(vec3 dir, float time) {
    // zenith lands in the middle of the disk and the horizon on its edge
    vec2 p = dir.xz / (1.0 + abs(dir.y));
    bool below = dir.y < 0.0;
    if (below) p += vec2(sin(length(p) * 90.0 - time * 2.0), cos(length(p) * 70.0 - time * 1.6)) * 0.0025;
    float r = length(p);
    float angle = atan(p.y, p.x);

    vec3 color = mix(vec3(0.015, 0.012, 0.04), vec3(0.08, 0.04, 0.12), smoothstep(0.2, 1.0, r));
    vec2 around = vec2(cos(angle), sin(angle));
    float ribbon = asNoise(around * 2.0 + vec2(time * 0.03, r * 4.0)) * asNoise(around * 5.0 + vec2(r * 9.0 - time * 0.05, 3.0));
    color += vec3(0.15, 0.05, 0.25) * ribbon * smoothstep(0.6, 1.0, r);

    float w = 0.0032;
    float gold = 0.0;
    float cyan = 0.0;

    gold += asGlow(abs(r - 0.86), w) + asGlow(abs(r - 0.835), w * 0.6) * 0.6;
    gold += asGlow(abs(r - 0.79), w * 0.7) * 0.7 + asGlow(abs(r - 0.71), w);
    float tickPos = (angle + time * 0.02) / 6.2831853 * 96.0;
    float tick = 1.0 - smoothstep(0.04, 0.12, abs(fract(tickPos) - 0.5));
    float outer = mod(floor(tickPos), 8.0) < 0.5 ? 0.86 : 0.85;
    gold += tick * smoothstep(0.838, 0.842, r) * (1.0 - smoothstep(outer, outer + 0.003, r));
    gold += asRuneRing(p, r, angle, 0.76, 36.0, -time * 0.03, 0.04);

    vec2 hepta = asStar(p, 0.68, 7.0, 3.0, time * 0.04, time);
    gold += asGlow(hepta.x, w);
    cyan += hepta.y * 3.0;
    for (int k = 0; k < 7; k++) {
        vec2 c = asCorner(float(k), 7.0, 0.68, time * 0.04);
        float d = length(p - c);
        gold += asGlow(abs(d - 0.045), w * 0.8) + asGlow(abs(d - 0.028), w * 0.6) * 0.6;
        vec2 tri = asStar(p - c, 0.026, 3.0, 1.0, -time * 0.5 + float(k), time);
        cyan += asGlow(tri.x, w * 0.6) * 0.8;
    }

    gold += asGlow(abs(r - 0.5), w) + asGlow(abs(r - 0.47), w * 0.6) * 0.5;
    gold += asRuneRing(p, r, angle, 0.555, 28.0, time * 0.04, 0.035);
    vec2 hexa = asStar(p, 0.47, 6.0, 2.0, -time * 0.07, time);
    cyan += asGlow(hexa.x, w) * 0.9 + hexa.y * 2.0;
    cyan += asGlow(asStar(p, 0.47, 12.0, 5.0, time * 0.03, time).x, w * 0.5) * 0.35;

    float dash = step(0.45, fract((angle - time * 0.3) / 6.2831853 * 48.0));
    cyan += asGlow(abs(r - 0.33), w * 1.2) * dash;
    gold += asGlow(abs(r - 0.3), w * 0.7) * 0.7;

    // the eye: two arcs meeting at the corners, an iris that turns and a dark pupil
    vec2 e = p / 0.2;
    float lid = max(abs(length(e - vec2(0.0, -0.55)) - 1.0), 0.0);
    lid = min(lid, max(abs(length(e - vec2(0.0, 0.55)) - 1.0), 0.0));
    float inside = step(length(e - vec2(0.0, -0.55)), 1.0) * step(length(e - vec2(0.0, 0.55)), 1.0);
    gold += asGlow(lid * 0.2, w) * step(abs(e.x), 0.9);
    float iris = length(e);
    cyan += asGlow(abs(iris - 0.32) * 0.2, w) * inside;
    cyan += (1.0 - smoothstep(0.05, 0.12, abs(fract(atan(e.y, e.x) / 6.2831853 * 16.0 + time * 0.2) - 0.5))) * step(iris, 0.3) * step(0.14, iris) * 0.8;
    float pupil = 1.0 - smoothstep(0.1, 0.14, iris);

    float motes = 0.0;
    for (int i = 0; i < 10; i++) {
        float fi = float(i);
        float life = fract(time * 0.08 + fi * 0.1);
        float rad = mix(0.95, 0.1, life);
        float a = fi * 2.399 + life * 9.0;
        float d = length(p - vec2(cos(a), sin(a)) * rad);
        motes += (exp(-d * 160.0) * 1.4 + exp(-d * 35.0) * 0.2) * sin(life * 3.14159);
    }

    float pulseRing = fract(time * 0.18);
    cyan += asGlow(abs(r - pulseRing * 1.0), w * 5.0) * (1.0 - pulseRing) * 0.6;

    float shimmer = 0.5 + 0.5 * sin(r * 18.0 - time * 0.9);
    vec3 goldInk = mix(vec3(1.0, 0.78, 0.38), vec3(1.0, 0.9, 0.65), shimmer);
    vec3 cyanInk = vec3(0.45, 0.85, 1.0);
    float fade = 1.0 - smoothstep(0.9, 1.0, r);
    color += (goldInk * gold + cyanInk * cyan + vec3(1.0, 0.92, 0.8) * motes) * fade;
    color = mix(color, vec3(0.0), pupil * 0.85);

    vec2 cell = floor(p * 140.0);
    float h = asHash(cell);
    color += vec3(1.0, 0.95, 0.85) * step(0.985, h) * (0.5 + 0.5 * sin(time * 3.0 + h * 50.0))
           * (1.0 - smoothstep(0.0, 0.5, length(fract(p * 140.0) - 0.5)));

    if (below) color *= vec3(0.3, 0.35, 0.45);
    return 1.0 - exp(-color * 1.4);
}
