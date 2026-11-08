// you're inside someone's domain: the whole view goes dark red, the edges fall into black
// and embers drift up through the air around you

// screen goes from 0,0 bottom left to 1,1 top right, dir is where you look

float dvHash(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453);
}

vec4 overlay(vec2 screen, vec3 dir, float time) {
    float edge = smoothstep(0.25, 0.95, length((screen - 0.5) * vec2(1.6, 1.0)));
    vec3 color = mix(vec3(0.38, 0.0, 0.04), vec3(0.02, 0.0, 0.0), edge);
    float alpha = 0.5 + edge * 0.42;

    // the embers are placed with dir, not screen, so they stay put in the world when you turn your head
    float embers = 0.0;
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        vec3 q = dir * (12.0 + fi * 8.0) + vec3(0.0, -time * (0.5 + fi * 0.3), 0.0);
        vec3 cell = floor(q);
        vec3 f = fract(q) - 0.5;
        float h = dvHash(cell + fi * 13.0);
        float flicker = 0.6 + 0.4 * sin(time * 5.0 + h * 30.0);
        embers += step(0.85, h) * (1.0 - smoothstep(0.05, 0.14, length(f))) * flicker;
    }
    color = mix(color, vec3(1.0, 0.4, 0.1), clamp(embers, 0.0, 1.0));
    alpha = max(alpha, clamp(embers, 0.0, 1.0));
    return vec4(color, alpha);
}
