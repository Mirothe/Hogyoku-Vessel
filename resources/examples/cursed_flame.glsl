// blue cursed energy burning up through the item. it snaps to the sprite's pixels on purpose,
// so it stays blocky and sits right next to vanilla textures

// texel is the item's own pixel colour, uv goes 0 to 1 across the sprite, normal faces out, view is where you look

float cfHash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float cfNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(cfHash(i), cfHash(i + vec2(1.0, 0.0)), f.x), mix(cfHash(i + vec2(0.0, 1.0)), cfHash(i + vec2(1.0, 1.0)), f.x), f.y);
}

vec4 material(vec4 texel, vec2 uv, vec3 normal, vec3 view, float time) {
    vec2 px = (floor(uv * 16.0) + 0.5) / 16.0;
    // uv.y is 0 at the top of the sprite, so adding time moves the flames up
    vec2 p = vec2(px.x * 4.0, px.y * 3.0 + time * 1.6);
    float n = cfNoise(p) * 0.6 + cfNoise(p * 2.3 + 4.0) * 0.4;
    float heat = smoothstep(0.3, 0.85, n * (0.55 + px.y * 0.75));

    vec3 color = mix(vec3(0.02, 0.03, 0.12), vec3(0.12, 0.38, 1.0), heat);
    color = mix(color, vec3(0.75, 0.95, 1.0), smoothstep(0.7, 1.0, heat));
    color *= 0.7 + 0.5 * texel.r;
    color *= 0.9 + 0.1 * step(0.5, cfHash(vec2(floor(time * 12.0), 1.0)));
    return vec4(clamp(color, 0.0, 1.0), 1.0);
}
