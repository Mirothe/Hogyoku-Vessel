// a moonlit sea. the waves are raymarched as a height field and reflect the same night sky you see above,
// with a glittering path out to the moon, foam on the crests and the milky way overhead

float ocHash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float ocHash3(vec3 p) {
    return fract(sin(dot(p, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
}

float ocNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(ocHash(i), ocHash(i + vec2(1.0, 0.0)), f.x), mix(ocHash(i + vec2(0.0, 1.0)), ocHash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float ocNoise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(ocHash3(i), ocHash3(i + vec3(1.0, 0.0, 0.0)), f.x),
                   mix(ocHash3(i + vec3(0.0, 1.0, 0.0)), ocHash3(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
               mix(mix(ocHash3(i + vec3(0.0, 0.0, 1.0)), ocHash3(i + vec3(1.0, 0.0, 1.0)), f.x),
                   mix(ocHash3(i + vec3(0.0, 1.0, 1.0)), ocHash3(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z);
}

float ocFbm(vec2 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        sum += amp * ocNoise(p);
        p = mat2(1.6, 1.2, -1.2, 1.6) * p;
        amp *= 0.5;
    }
    return sum;
}

float ocWaves(vec2 p, float time, int octaves) {
    float h = 0.0;
    float amp = 0.5;
    float freq = 0.15;
    vec2 wind = normalize(vec2(0.3, 1.0));
    mat2 turn = mat2(0.8, 0.6, -0.6, 0.8);
    for (int i = 0; i < 7; i++) {
        if (i >= octaves) break;
        float fi = float(i);
        vec2 q = p * freq;
        float wobble = ocNoise(q * 0.8 + time * 0.07) * 2.5;
        // folding a sine gives sharp crests and soft troughs like real waves
        float crest = 1.0 - abs(sin(dot(q, wind) + time * (0.8 + 0.3 * fi) + wobble));
        h += crest * crest * amp;
        wind = turn * wind;
        amp *= 0.46;
        freq *= 2.0;
    }
    return h;
}

vec3 ocSky(vec3 d, vec3 moon, float time) {
    float y = max(d.y, 0.0);
    float m = max(dot(d, moon), 0.0);
    vec3 color = mix(vec3(0.06, 0.1, 0.18), vec3(0.006, 0.01, 0.03), pow(y, 0.4));
    color += vec3(0.1, 0.14, 0.2) * pow(max(dot(normalize(vec3(d.x, 0.0, d.z) + 0.0001), normalize(vec3(moon.x, 0.0, moon.z))), 0.0), 6.0) * exp(-y * 4.0);

    // the milky way: a band of dust with dark lanes and more stars inside it
    vec3 galaxy = normalize(vec3(0.6, 1.0, -0.3));
    float band = exp(-abs(dot(d, galaxy)) * 5.0);
    float dust = ocNoise3(d * 6.0) * 0.6 + ocNoise3(d * 15.0) * 0.4;
    float lanes = smoothstep(0.45, 0.7, ocNoise3(d * 9.0 + 4.0));
    color += vec3(0.16, 0.16, 0.2) * band * dust * (1.0 - lanes * 0.8) * smoothstep(0.0, 0.3, y);

    for (int i = 0; i < 2; i++) {
        float scale = 90.0 + float(i) * 110.0;
        vec3 cell = floor(d * scale);
        float h = ocHash3(cell + float(i) * 11.0);
        float limit = 0.975 - band * 0.02;
        float star = step(limit, h) * (1.0 - smoothstep(0.0, 0.5, length(fract(d * scale) - 0.5)));
        star *= 0.6 + 0.4 * sin(time * (1.0 + h * 5.0) + h * 70.0);
        color += vec3(0.85, 0.9, 1.0) * star * smoothstep(0.02, 0.2, y) * (1.0 - float(i) * 0.4);
    }

    color += vec3(0.45, 0.55, 0.75) * pow(m, 40.0) * 0.6 + vec3(0.2, 0.26, 0.4) * pow(m, 6.0) * 0.35;
    float halo = acos(min(m, 1.0));
    color += vec3(0.25, 0.3, 0.4) * exp(-abs(halo - 0.38) * 45.0) * 0.12;

    float disc = smoothstep(0.99935, 0.9996, m);
    vec3 east = normalize(cross(moon, vec3(0.0, 1.0, 0.0)));
    vec2 face = vec2(dot(d, east), dot(d, cross(east, moon))) * 34.0;
    float maria = ocNoise(face * 2.0) * 0.6 + ocNoise(face * 6.0) * 0.4;
    float craters = smoothstep(0.55, 0.8, ocNoise(face * 11.0));
    float limb = sqrt(max(1.0 - dot(face, face) * 1.8, 0.0));
    vec3 lunar = vec3(1.0, 0.97, 0.9) * (0.7 + 0.3 * maria - craters * 0.12) * (0.65 + 0.35 * limb);
    color = mix(color, lunar * 1.3, disc);

    vec2 cloudPos = d.xz / (y + 0.06) * 0.7 + vec2(time * 0.01, 0.0);
    vec2 warp = vec2(ocFbm(cloudPos * 0.8), ocFbm(cloudPos * 0.8 + 5.2));
    float cloud = smoothstep(0.5, 0.8, ocFbm(cloudPos + warp * 1.2));
    float lining = pow(m, 10.0);
    vec3 cloudColor = vec3(0.07, 0.08, 0.12) + vec3(0.55, 0.6, 0.7) * lining * 1.3 + vec3(0.06, 0.07, 0.1) * cloud;
    color = mix(color, cloudColor, cloud * smoothstep(0.02, 0.18, y) * 0.85 * (1.0 - disc * 0.7));
    return color;
}

vec3 sky(vec3 dir, float time) {
    vec3 moon = normalize(vec3(0.0, 0.17, -1.0));
    if (dir.y >= 0.0) return 1.0 - exp(-ocSky(dir, moon, time) * 1.8);

    vec3 origin = vec3(0.0, 2.4, time * 0.4);
    float t = max((origin.y - 1.1) / -dir.y, 0.0);
    vec3 p = origin;
    for (int i = 0; i < 100; i++) {
        p = origin + dir * t;
        float gap = p.y - ocWaves(p.xz, time, 3);
        if (gap < 0.002 * t || t > 300.0) break;
        t += gap * 0.6;
    }

    // far away the fine waves only add shimmer, so they get dropped there
    int detail = t > 60.0 ? 4 : 7;
    float e = 0.015 + 0.002 * t;
    float hC = ocWaves(p.xz, time, detail);
    float hX = ocWaves(p.xz + vec2(e, 0.0), time, detail);
    float hZ = ocWaves(p.xz + vec2(0.0, e), time, detail);
    vec3 n = normalize(vec3(hC - hX, e, hC - hZ));

    vec3 bounce = reflect(dir, n);
    bounce.y = abs(bounce.y);
    float fresnel = 0.02 + 0.98 * pow(1.0 - max(dot(n, -dir), 0.0), 5.0);
    float lift = smoothstep(0.5, 1.3, hC);
    vec3 deep = vec3(0.0, 0.025, 0.045) + vec3(0.0, 0.1, 0.11) * max(dot(n, moon), 0.0) * lift;
    vec3 color = mix(deep, ocSky(bounce, moon, time), fresnel);

    // close up the glitter is sharp sparkles, far away it smears into one soft path
    float far = clamp(t / 140.0, 0.0, 1.0);
    float sharp = mix(1600.0, 90.0, far);
    float sparkle = mix(step(0.55, ocNoise(p.xz * 9.0 + time * 2.0)), 1.0, far);
    color += vec3(1.0, 0.96, 0.88) * pow(max(dot(bounce, moon), 0.0), sharp) * mix(30.0, 1.4, far) * sparkle;

    float foam = smoothstep(1.15, 1.45, hC) * smoothstep(0.4, 0.75, ocNoise(p.xz * 3.0 - time * 0.3));
    color = mix(color, vec3(0.32, 0.38, 0.45), foam * 0.5 * (1.0 - far));

    vec3 horizon = ocSky(normalize(vec3(dir.x, 0.0001, dir.z)), moon, time);
    color = mix(horizon + vec3(0.03, 0.04, 0.06), color, exp(-t * 0.011));
    return 1.0 - exp(-color * 1.8);
}
