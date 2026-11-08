// the low health look: red closing in from the edges on every heartbeat, with veins showing at the border

// screen goes from 0,0 bottom left to 1,1 top right, dir is where you look

float hbHash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float hbNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hbHash(i), hbHash(i + vec2(1.0, 0.0)), f.x), mix(hbHash(i + vec2(0.0, 1.0)), hbHash(i + vec2(1.0, 1.0)), f.x), f.y);
}

vec4 overlay(vec2 screen, vec3 dir, float time) {
    float beat = fract(time * 1.1);
    // two thumps close together and then a rest, the way a real heartbeat sounds
    float thump = exp(-beat * 16.0) + step(0.2, beat) * exp(-(beat - 0.2) * 16.0) * 0.7;

    vec2 c = (screen - 0.5) * vec2(1.6, 1.0);
    float r = length(c);
    float vignette = smoothstep(0.42 - thump * 0.12, 0.95, r);

    float n = hbNoise(c * 6.0) * 0.6 + hbNoise(c * 13.0) * 0.4;
    float veins = pow(1.0 - abs(n * 2.0 - 1.0), 10.0) * smoothstep(0.45, 0.85, r);

    vec3 color = mix(vec3(0.3, 0.0, 0.02), vec3(0.7, 0.02, 0.05), thump) + vec3(0.3, 0.0, 0.0) * veins;
    float alpha = clamp(vignette * (0.55 + 0.35 * thump) + veins * 0.5, 0.0, 0.9);
    return vec4(clamp(color, 0.0, 1.0), alpha);
}
