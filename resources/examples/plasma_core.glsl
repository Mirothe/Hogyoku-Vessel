// a containment core: a white hot plasma ball with lightning crawling over it, held in place by three spinning rings.
// the glow is gathered in small steps along the ray and the rings are solid metal
// origin is where the ray enters the item's box (-1 to 1 on every axis), return alpha 0 for empty space

float pcHash(vec3 p) {
    return fract(sin(dot(p, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
}

float pcNoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(pcHash(i), pcHash(i + vec3(1.0, 0.0, 0.0)), f.x),
                   mix(pcHash(i + vec3(0.0, 1.0, 0.0)), pcHash(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
               mix(mix(pcHash(i + vec3(0.0, 0.0, 1.0)), pcHash(i + vec3(1.0, 0.0, 1.0)), f.x),
                   mix(pcHash(i + vec3(0.0, 1.0, 1.0)), pcHash(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z);
}

mat2 pcTurn(float a) {
    float c = cos(a);
    float s = sin(a);
    return mat2(c, -s, s, c);
}

vec3 pcRingSpace(vec3 p, int i, float time) {
    float fi = float(i);
    p.yz = pcTurn(time * (0.6 + fi * 0.25) + fi * 2.1) * p.yz;
    p.xy = pcTurn(time * (0.35 - fi * 0.2) + fi * 1.2) * p.xy;
    return p;
}

float pcRings(vec3 p, float time) {
    float best = 9.0;
    for (int i = 0; i < 3; i++) {
        vec3 q = pcRingSpace(p, i, time);
        float radius = 0.86 - float(i) * 0.1;
        vec2 w = abs(vec2(length(q.xz) - radius, q.y)) - vec2(0.018, 0.05);
        best = min(best, length(max(w, 0.0)) + min(max(w.x, w.y), 0.0) - 0.006);
    }
    return best;
}

vec4 object(vec3 origin, vec3 dir, float time) {
    vec3 glow = vec3(0.0);
    float t = 0.0;
    bool metal = false;
    for (int i = 0; i < 110; i++) {
        vec3 p = origin + dir * t;
        float ring = pcRings(p, time);
        if (ring < 0.0015) {
            metal = true;
            break;
        }
        float r = length(p);
        float core = max(r - 0.24, 0.0);
        glow += vec3(1.0, 0.95, 0.9) * exp(-core * 22.0) * 0.14;
        glow += vec3(0.35, 0.6, 1.0) * exp(-core * 6.0) * 0.03;

        // arcs are the thin ridges of a noise field wrapped around the ball
        vec3 around = p / max(r, 0.001) * 3.0 + vec3(0.0, time * 1.3, time * 0.7);
        float arc = pow(1.0 - abs(pcNoise(around + r * 2.0) * 2.0 - 1.0), 14.0);
        float shell = exp(-abs(r - 0.42) * 9.0);
        glow += vec3(0.55, 0.8, 1.0) * arc * shell * 0.35;

        t += min(ring, 0.035);
        if (t > 3.5) break;
    }

    vec3 color = glow;
    float alpha = clamp(max(max(glow.r, glow.g), glow.b) * 1.2, 0.0, 1.0);
    if (metal) {
        vec3 p = origin + dir * t;
        vec2 k = vec2(0.0015, 0.0);
        vec3 n = normalize(vec3(pcRings(p + k.xyy, time) - pcRings(p - k.xyy, time),
                                pcRings(p + k.yxy, time) - pcRings(p - k.yxy, time),
                                pcRings(p + k.yyx, time) - pcRings(p - k.yyx, time)));
        vec3 toCore = normalize(-p);
        float lit = max(dot(n, toCore), 0.0);
        float shine = pow(max(dot(reflect(dir, n), toCore), 0.0), 30.0);
        vec3 steel = vec3(0.05, 0.055, 0.07) + vec3(0.55, 0.75, 1.0) * lit * 0.6 + vec3(0.8, 0.9, 1.0) * shine * 1.5;
        color = steel + glow;
        alpha = 1.0;
    }
    color = 1.0 - exp(-color * 1.6);
    return vec4(color / max(alpha, 0.0001), alpha);
}
