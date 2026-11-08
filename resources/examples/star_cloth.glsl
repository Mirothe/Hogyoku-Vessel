// the item turns into a window onto deep space, like the end portal. the star layers shift with your view
// at different speeds, so it looks like the stars sit far behind the item instead of being painted on

// texel is the item's own pixel colour, uv goes 0 to 1 across the sprite, normal faces out, view is where you look

float scHash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float scNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(scHash(i), scHash(i + vec2(1.0, 0.0)), f.x), mix(scHash(i + vec2(0.0, 1.0)), scHash(i + vec2(1.0, 1.0)), f.x), f.y);
}

vec4 material(vec4 texel, vec2 uv, vec3 normal, vec3 view, float time) {
    vec3 color = vec3(0.01, 0.015, 0.05);
    color += vec3(0.08, 0.2, 0.35) * scNoise(uv * 3.0 + view.xz * 0.8 + time * 0.04) * 0.6;
    color += vec3(0.25, 0.08, 0.3) * scNoise(uv * 5.0 - view.xz * 1.2 - time * 0.03) * 0.3;

    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        float depth = 1.0 + fi * 0.8;
        vec2 p = uv * (5.0 + fi * 3.0) + view.xz * depth * 1.5 + vec2(time * 0.04 * depth, 0.0);
        vec2 cell = floor(p);
        vec2 f = fract(p) - 0.5;
        float h = scHash(cell + fi * 17.0);
        vec2 off = vec2(scHash(cell + 3.1), scHash(cell + 7.7)) - 0.5;
        float d = length(f - off * 0.6);
        float twinkle = 0.6 + 0.4 * sin(time * 3.0 + h * 40.0);
        vec3 tint = mix(vec3(0.6, 0.8, 1.0), vec3(1.0, 0.9, 0.7), scHash(cell + 11.0));
        color += tint * step(0.75, h) * exp(-d * 28.0) * twinkle * (1.4 - fi * 0.25);
    }

    float rim = pow(1.0 - abs(dot(normal, view)), 3.0);
    color += vec3(0.3, 0.6, 1.0) * rim * 0.5;
    return vec4(clamp(color, 0.0, 1.0), 1.0);
}
