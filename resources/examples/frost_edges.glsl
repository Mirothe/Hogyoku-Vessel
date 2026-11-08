// ice creeping in from the edges of the screen with feathery crystals and a few sparkles,
// breathing in and out slowly like someone's freezing

// screen goes from 0,0 bottom left to 1,1 top right, dir is where you look

float feHash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float feNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(feHash(i), feHash(i + vec2(1.0, 0.0)), f.x), mix(feHash(i + vec2(0.0, 1.0)), feHash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float feFbm(vec2 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        sum += amp * feNoise(p);
        p = mat2(1.6, 1.2, -1.2, 1.6) * p;
        amp *= 0.5;
    }
    return sum;
}

vec4 overlay(vec2 screen, vec3 dir, float time) {
    vec2 c = (screen - 0.5) * 2.0;
    float edge = mix(max(abs(c.x), abs(c.y)), length(c) * 0.75, 0.5);
    float n = feFbm(screen * vec2(9.0, 5.0));
    float grow = 0.62 + 0.08 * sin(time * 0.5);
    float frost = smoothstep(grow, grow + 0.3, edge + (n - 0.5) * 0.4);

    // ridged noise gives the thin branching lines frost has on glass
    float ridge = pow(1.0 - abs(feNoise(screen * vec2(34.0, 19.0) + n * 3.0) * 2.0 - 1.0), 8.0);
    float sparkle = step(0.996, feHash(floor(screen * vec2(320.0, 180.0)))) * (0.5 + 0.5 * sin(time * 6.0 + n * 50.0));

    vec3 color = mix(vec3(0.72, 0.86, 1.0), vec3(1.0), ridge) + sparkle;
    float alpha = clamp(frost * (0.5 + 0.45 * ridge) + sparkle * frost, 0.0, 0.92);
    return vec4(clamp(color, 0.0, 1.0), alpha);
}
