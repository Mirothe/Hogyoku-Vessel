// solid gold with a band of light sweeping across it every few seconds and the odd pixel glinting.
// it goes off the sprite's own brightness, so darker pixels turn into darker gold

// texel is the item's own pixel colour, uv goes 0 to 1 across the sprite, normal faces out, view is where you look

float gsHash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

vec4 material(vec4 texel, vec2 uv, vec3 normal, vec3 view, float time) {
    float lum = dot(texel.rgb, vec3(0.333));
    vec3 gold = mix(vec3(0.42, 0.25, 0.04), vec3(1.0, 0.8, 0.32), smoothstep(0.3, 0.9, lum));

    float sweep = fract(time * 0.3) * 2.2 - 0.6;
    float diagonal = (uv.x + uv.y) * 0.5;
    float band = exp(-pow(diagonal - sweep, 2.0) * 250.0) + exp(-pow(diagonal - sweep + 0.08, 2.0) * 900.0) * 0.6;

    vec2 pixel = floor(uv * 16.0);
    float glint = step(0.97, gsHash(pixel + floor(time * 3.0)));
    float facing = abs(dot(normal, view));

    vec3 color = gold * (0.7 + 0.3 * facing) + vec3(1.0, 0.95, 0.8) * (band * 0.9 + glint * 0.5);
    return vec4(clamp(color, 0.0, 1.0), 1.0);
}
