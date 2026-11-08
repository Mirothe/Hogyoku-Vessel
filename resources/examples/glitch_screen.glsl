// a screen that can't hold itself together. bands of the picture tear sideways, the color channels split apart,
// random blocks drop to low resolution and a band of static rolls down every so often. it all changes on a fast tick
#pragma vessel shape panel

// uv runs from 0 to 1 across each face, pos is the same point in the -1 to 1 box, normal points out, view is where you look

float glHash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

// the clean picture underneath: rings, stripes and a checker, bold enough that the damage is easy to see
float glPicture(vec2 p, float time) {
    float rings = step(0.5, fract(length(p - 0.5) * 6.0 - time * 0.4));
    float stripes = step(0.5, fract((p.x + p.y) * 5.0 + time * 0.2));
    vec2 c = floor(p * 4.0);
    float checker = mod(c.x + c.y, 2.0);
    return mix(rings, stripes, checker) * 0.85 + 0.1;
}

vec4 surface(vec2 uv, vec3 pos, vec3 normal, vec3 view, float time) {
    float tick = floor(time * 9.0);
    float bad = step(0.55, glHash(vec2(tick, 7.0)));

    float band = floor(uv.y * 16.0);
    float tear = (glHash(vec2(band, tick)) - 0.5) * step(0.75, glHash(vec2(band * 0.37, tick + 3.0))) * 0.35 * bad;
    vec2 p = uv + vec2(tear, 0.0);

    vec2 block = floor(uv * 6.0);
    if (glHash(block + tick) > 0.88) p = floor(p * 14.0) / 14.0;

    float split = 0.006 + 0.03 * bad;
    float r = glPicture(p + vec2(split, 0.0), time);
    float g = glPicture(p, time);
    float b = glPicture(p - vec2(split, 0.0), time);
    vec3 color = vec3(r * 1.0, g * 0.25, b * 0.95) * 0.6 + vec3(r, g, b) * 0.35;

    float roll = exp(-abs(fract(uv.y - time * 0.35) - 0.5) * 28.0);
    float noise = glHash(floor(uv * vec2(160.0, 90.0)) + tick);
    color = mix(color, vec3(noise), roll * 0.6);
    color *= 0.9 + 0.1 * sin(uv.y * 600.0);
    return vec4(clamp(color, 0.0, 1.0), 1.0);
}
