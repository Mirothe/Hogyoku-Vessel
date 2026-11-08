// molten lava running down the item: dark crust drifting over bright cracks that flicker.
// the sprite's own shading stays mixed in, so the item keeps its shape under the lava

// texel is the item's own pixel colour, uv goes 0 to 1 across the sprite, normal faces out, view is where you look

float lvHash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float lvNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(lvHash(i), lvHash(i + vec2(1.0, 0.0)), f.x), mix(lvHash(i + vec2(0.0, 1.0)), lvHash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float lvFbm(vec2 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        sum += amp * lvNoise(p);
        p = mat2(1.6, 1.2, -1.2, 1.6) * p;
        amp *= 0.5;
    }
    return sum;
}

vec4 material(vec4 texel, vec2 uv, vec3 normal, vec3 view, float time) {
    vec2 p = uv * 3.0 - vec2(0.0, time * 0.25);
    // feeding the noise back into itself is what makes it look like it's flowing and not just scrolling
    float warp = lvFbm(p + lvFbm(p * 1.5 + time * 0.1) * 1.5);
    float cracks = smoothstep(0.4, 0.62, warp);

    vec3 hot = mix(vec3(1.0, 0.28, 0.02), vec3(1.0, 0.85, 0.35), smoothstep(0.6, 0.85, warp));
    vec3 crust = vec3(0.13, 0.05, 0.04);
    vec3 color = mix(crust, hot, cracks) * (0.55 + 0.6 * texel.r);
    color += vec3(1.0, 0.5, 0.1) * pow(cracks, 4.0) * (0.5 + 0.5 * sin(time * 3.0 + warp * 12.0)) * 0.35;
    return vec4(clamp(color, 0.0, 1.0), 1.0);
}
