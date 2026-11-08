// a hologram panel: scanlines, a flickering grid, rows of fake text scrolling up and a readout ring that fills and empties.
// it's see through, so the flat panel reads as a screen floating in the air
#pragma vessel shape panel

// uv runs from 0 to 1 across each face, pos is the same point in the -1 to 1 box, normal points out, view is where you look

float hoHash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

vec4 surface(vec2 uv, vec3 pos, vec3 normal, vec3 view, float time) {
    vec3 cyan = vec3(0.35, 0.9, 1.0);

    float scan = 0.7 + 0.3 * sin((uv.y + time * 0.04) * 460.0);
    vec2 cell = abs(fract(uv * 10.0) - 0.5);
    float grid = 1.0 - smoothstep(0.0, 0.03, 0.5 - max(cell.x, cell.y));

    // fake text is just rows of bars with random lengths and gaps, scrolling upward
    float lines = (uv.y + time * 0.06) * 24.0;
    float row = floor(lines);
    float inRow = fract(lines);
    float width = 0.15 + 0.55 * hoHash(vec2(row, 1.0));
    float word = step(0.3, hoHash(vec2(row, floor(uv.x * 22.0))));
    float text = step(0.08, uv.x) * step(uv.x, 0.08 + width) * step(0.3, inRow) * step(inRow, 0.72) * word * step(uv.x, 0.58);

    vec2 c = uv - vec2(0.77, 0.7);
    float ringDist = abs(length(c) - 0.13);
    float ring = 1.0 - smoothstep(0.0, 0.007, ringDist);
    float fill = 0.5 + 0.5 * sin(time * 0.8);
    float around = atan(c.y, c.x) / 6.2831853 + 0.5;
    float arc = (1.0 - smoothstep(0.0, 0.02, abs(length(c) - 0.1))) * step(around, fill);

    float edge = 1.0 - smoothstep(0.0, 0.025, min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y)));
    float flicker = 0.85 + 0.15 * step(0.4, hoHash(vec2(floor(time * 14.0), 3.0)));
    // more of the panel is in front of you at a glancing angle, so it looks denser there
    float glance = 1.0 - abs(dot(normal, view));

    float light = text * 1.1 + grid * 0.3 + ring + arc * 1.3 + edge * 1.5;
    vec3 color = cyan * light * scan * flicker + vec3(1.0) * arc * 0.3;
    float alpha = clamp((0.1 + light * 0.55) * (0.8 + glance * 0.6) * flicker, 0.0, 1.0);
    return vec4(clamp(color, 0.0, 1.0), alpha);
}
