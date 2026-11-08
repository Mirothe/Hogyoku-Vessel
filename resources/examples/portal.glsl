// a round gateway: a vortex spiralling into a bright eye, a glowing rim and sparks drifting off it.
// the item is a flat panel and everything past the rim gets alpha 0, so in game it looks perfectly round
#pragma vessel shape panel

// uv runs from 0 to 1 across each face, pos is the same point in the -1 to 1 box, normal points out, view is where you look

float ptHash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float ptNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(ptHash(i), ptHash(i + vec2(1.0, 0.0)), f.x), mix(ptHash(i + vec2(0.0, 1.0)), ptHash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float ptFbm(vec2 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        sum += amp * ptNoise(p);
        p = mat2(1.6, 1.2, -1.2, 1.6) * p;
        amp *= 0.5;
    }
    return sum;
}

vec4 surface(vec2 uv, vec3 pos, vec3 normal, vec3 view, float time) {
    vec2 p = uv * 2.0 - 1.0;
    float r = length(p);
    if (r > 1.0) return vec4(0.0);

    // twisting the angle harder toward the middle is what makes the spiral
    float twist = atan(p.y, p.x) + 2.6 / (r + 0.22) - time * 1.4;
    vec2 q = vec2(cos(twist), sin(twist)) * (1.0 + r * 3.0);
    float swirl = ptFbm(q + time * 0.15);
    float arms = 0.5 + 0.5 * sin(twist * 3.0 + swirl * 5.0);

    vec3 color = mix(vec3(0.03, 0.0, 0.1), vec3(0.45, 0.15, 0.95), arms * swirl * 1.6);
    color += vec3(0.3, 0.6, 1.0) * pow(swirl, 3.0) * (1.0 - r) * 1.5;
    color += vec3(0.95, 0.85, 1.0) * pow(max(1.0 - r * 1.6, 0.0), 4.0) * 2.5;

    float rim = abs(r - 0.92);
    color += vec3(0.65, 0.45, 1.0) * exp(-rim * 35.0) * 1.8 + vec3(1.0) * exp(-rim * 140.0);

    for (int i = 0; i < 14; i++) {
        float fi = float(i);
        float life = fract(time * 0.35 + ptHash(vec2(fi, 2.0)));
        float a = ptHash(vec2(fi, 1.0)) * 6.2831 + time * 0.25;
        vec2 spark = vec2(cos(a), sin(a)) * (0.35 + life * 0.6);
        color += vec3(0.9, 0.75, 1.0) * exp(-length(p - spark) * 110.0) * (1.0 - life) * 2.5;
    }

    float alpha = 1.0 - smoothstep(0.95, 0.99, r);
    return vec4(clamp(1.0 - exp(-color * 1.3), 0.0, 1.0), alpha);
}
