// a magic circle drawn on the ground: two rings of runes turning opposite ways, a hexagram, a triangle at the heart
// and light pulsing outward. the item is a thin floor tile and everything outside the circle is cut away
#pragma vessel shape floor

// uv runs from 0 to 1 across each face, pos is the same point in the -1 to 1 box, normal points out, view is where you look

float rcHash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float rcSegment(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    return length(pa - ba * clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0));
}

// joins each point on a circle to the one `skip` places further round
float rcStar(vec2 p, float radius, float points, float skip, float spin) {
    float best = 9.0;
    for (int k = 0; k < 8; k++) {
        float fk = float(k);
        if (fk >= points) break;
        float a0 = spin + fk / points * 6.2831853;
        float a1 = spin + (fk + skip) / points * 6.2831853;
        best = min(best, rcSegment(p, vec2(cos(a0), sin(a0)) * radius, vec2(cos(a1), sin(a1)) * radius));
    }
    return best;
}

// three strokes on a 3x3 grid, different for every slot
float rcRune(vec2 g, float id) {
    float best = 9.0;
    for (int k = 0; k < 3; k++) {
        float fk = float(k);
        vec2 a = floor(vec2(rcHash(vec2(id, fk)), rcHash(vec2(fk, id))) * 3.0) / 2.0 - 0.5;
        vec2 b = floor(vec2(rcHash(vec2(id + 7.0, fk)), rcHash(vec2(fk, id + 7.0))) * 3.0) / 2.0 - 0.5;
        if (length(a - b) < 0.1) b = -a;
        best = min(best, rcSegment(g, a * 0.7, b * 0.7));
    }
    return best;
}

float rcLine(float d, float width) {
    return (1.0 - smoothstep(0.0, width, d)) + exp(-d / (width * 5.0)) * 0.35;
}

float rcRunes(vec2 p, float r, float angle, float radius, float slots, float spin) {
    float slot = (angle + spin) / 6.2831853 * slots;
    float id = mod(floor(slot), slots) + radius * 50.0;
    vec2 g = vec2((fract(slot) - 0.5) * 1.6, (r - radius) / 0.07);
    if (abs(g.y) > 0.8) return 0.0;
    return rcLine(rcRune(g, id) * 0.07, 0.006);
}

vec4 surface(vec2 uv, vec3 pos, vec3 normal, vec3 view, float time) {
    vec2 p = uv * 2.0 - 1.0;
    float r = length(p);
    if (r > 1.0) return vec4(0.0);
    float angle = atan(p.y, p.x);
    float w = 0.008;

    float ink = rcLine(abs(r - 0.95), w) + rcLine(abs(r - 0.9), w * 0.7) * 0.7;
    ink += rcLine(abs(r - 0.62), w) + rcLine(abs(r - 0.57), w * 0.7) * 0.6 + rcLine(abs(r - 0.22), w);
    ink += rcRunes(p, r, angle, 0.76, 22.0, time * 0.15);
    ink += rcRunes(p, r, angle, 0.4, 14.0, -time * 0.22) * 0.8;
    ink += rcLine(rcStar(p, 0.57, 6.0, 2.0, time * 0.1), w);
    ink += rcLine(rcStar(p, 0.22, 3.0, 1.0, -time * 0.35), w);

    float ticks = 1.0 - smoothstep(0.05, 0.15, abs(fract((angle - time * 0.05) / 6.2831853 * 72.0) - 0.5));
    ink += ticks * smoothstep(0.9, 0.905, r) * (1.0 - smoothstep(0.945, 0.95, r)) * 0.8;

    float pulse = fract(time * 0.4);
    float wave = exp(-abs(r - pulse) * 30.0) * (1.0 - pulse);
    float breathe = 0.75 + 0.25 * sin(time * 2.0);

    vec3 fire = mix(vec3(1.0, 0.55, 0.15), vec3(1.0, 0.85, 0.5), 0.5 + 0.5 * sin(r * 16.0 - time * 1.5));
    vec3 color = fire * (ink * breathe + wave * 0.8) + vec3(1.0, 0.95, 0.85) * exp(-r * 9.0) * 0.6;
    float alpha = clamp(ink * breathe + wave * 0.5 + 0.12, 0.0, 1.0) * (1.0 - smoothstep(0.97, 1.0, r));
    return vec4(clamp(color, 0.0, 1.0), alpha);
}
