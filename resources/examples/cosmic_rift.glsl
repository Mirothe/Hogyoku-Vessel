// a tear across the sky with plasma pouring through it. it's all layered 3D noise with no raymarching,
// so it stays cheap even with this much going on

float crHash(vec3 p) {
    return fract(sin(dot(p, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
}

float crNoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(crHash(i), crHash(i + vec3(1.0, 0.0, 0.0)), f.x),
                   mix(crHash(i + vec3(0.0, 1.0, 0.0)), crHash(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
               mix(mix(crHash(i + vec3(0.0, 0.0, 1.0)), crHash(i + vec3(1.0, 0.0, 1.0)), f.x),
                   mix(crHash(i + vec3(0.0, 1.0, 1.0)), crHash(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z);
}

float crFbm(vec3 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        sum += amp * crNoise(p);
        p = p * 2.03 + vec3(1.7, 9.2, 4.1);
        amp *= 0.5;
    }
    return sum;
}

// folding the noise around 0.5 turns soft clouds into thin bright threads
float crThreads(vec3 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        float n = 1.0 - abs(crNoise(p) * 2.0 - 1.0);
        sum += n * n * amp;
        p = p * 2.2 + vec3(4.1, 2.3, 7.7);
        amp *= 0.5;
    }
    return sum;
}

float crStars(vec3 dir, float scale, float time) {
    vec3 cell = floor(dir * scale);
    float h = crHash(cell);
    float shape = 1.0 - smoothstep(0.0, 0.5, length(fract(dir * scale) - 0.5));
    return step(0.975, h) * shape * (0.6 + 0.4 * sin(time * (1.5 + h * 4.0) + h * 60.0));
}

vec3 sky(vec3 dir, float time) {
    vec3 axis = normalize(vec3(0.8, 0.55, 0.12));
    vec3 along = normalize(cross(axis, vec3(0.0, 1.0, 0.0)));
    float drift = time * 0.03;

    // signed distance from the middle of the tear, bent by noise so the edges come out jagged
    float warp = crFbm(dir * 2.5 + vec3(0.0, drift, 0.0)) - 0.5;
    float across = dot(dir, axis) + warp * 0.35;
    float width = 0.04 + 0.07 * crFbm(dir * 4.0 + vec3(drift * 2.0));
    float inside = 1.0 - smoothstep(width * 0.55, width, abs(across));
    float edge = exp(-abs(abs(across) - width * 0.8) * 80.0);
    float near = exp(-abs(across) * 5.0);

    vec3 color = vec3(0.008, 0.006, 0.018);
    float dust = crFbm(dir * 1.6 + vec3(7.0, 3.0, 1.0));
    color += vec3(0.14, 0.05, 0.22) * dust * dust * (0.4 + near);
    color += vec3(0.9, 0.9, 1.0) * crStars(dir, 70.0, time) + vec3(0.6, 0.7, 1.0) * crStars(dir, 150.0, time) * 0.6;

    vec3 q = dir * 3.0 + along * time * 0.25;
    float plasma = crFbm(q + crFbm(q * 1.7 - time * 0.05) * 1.6);
    vec3 hot = mix(vec3(0.45, 0.08, 0.85), vec3(0.15, 0.85, 1.0), smoothstep(0.35, 0.7, plasma));
    hot = mix(hot, vec3(1.0, 0.95, 1.0), smoothstep(0.62, 0.82, plasma));
    color = mix(color, hot * (1.0 + plasma * 1.5), inside);

    color += vec3(0.85, 0.45, 1.0) * edge * 1.6;
    color += vec3(0.35, 0.12, 0.65) * near * 0.45;

    float threads = crThreads(dir * 5.0 + vec3(0.0, time * 0.12, time * 0.05));
    color += vec3(0.55, 0.8, 1.0) * pow(threads, 6.0) * near * 3.0 * (1.0 - inside);

    // specks of debris flung out of the tear, moving away from it on both sides
    vec3 flung = dir * 90.0 - axis * sign(across) * time * 4.0;
    float speck = step(0.985, crHash(floor(flung))) * (1.0 - smoothstep(0.0, 0.5, length(fract(flung) - 0.5)));
    color += vec3(0.9, 0.75, 1.0) * speck * near * 2.5;

    return 1.0 - exp(-color * 1.5);
}
