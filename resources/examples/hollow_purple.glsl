// hollow purple: a red orb and a blue orb spiral into each other, hit, and blow up into a crackling purple sphere
// with a shockwave, then it all starts over. glow is gathered in small steps along the ray
// origin is where the ray enters the item's box (-1 to 1 on every axis), return alpha 0 for empty space

float hpHash(vec3 p) {
    return fract(sin(dot(p, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
}

float hpNoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hpHash(i), hpHash(i + vec3(1.0, 0.0, 0.0)), f.x),
                   mix(hpHash(i + vec3(0.0, 1.0, 0.0)), hpHash(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
               mix(mix(hpHash(i + vec3(0.0, 0.0, 1.0)), hpHash(i + vec3(1.0, 0.0, 1.0)), f.x),
                   mix(hpHash(i + vec3(0.0, 1.0, 1.0)), hpHash(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z);
}

vec4 object(vec3 origin, vec3 dir, float time) {
    float cycle = fract(time / 6.0);
    // the first 60 percent is the two orbs closing in, the rest is the purple sphere
    float approach = clamp(cycle / 0.6, 0.0, 1.0);
    float burst = clamp((cycle - 0.6) / 0.4, 0.0, 1.0);
    float gap = mix(0.6, 0.0, approach * approach);
    float turn = approach * 9.0;
    vec3 red = vec3(cos(turn), 0.15 * sin(turn * 0.5), sin(turn)) * gap;
    vec3 blue = -red;
    float orbSize = 0.12;
    float sphere = burst > 0.0 ? mix(0.12, 0.5, sqrt(burst)) : 0.0;
    float fadeOut = 1.0 - smoothstep(0.75, 1.0, burst);

    vec3 glow = vec3(0.0);
    float t = 0.0;
    for (int i = 0; i < 90; i++) {
        vec3 p = origin + dir * t;
        if (burst <= 0.0) {
            float dr = max(length(p - red) - orbSize, 0.0);
            float db = max(length(p - blue) - orbSize, 0.0);
            glow += vec3(1.0, 0.15, 0.1) * exp(-dr * 30.0) * 0.08 + vec3(1.0, 0.4, 0.3) * exp(-dr * 90.0) * 0.12;
            glow += vec3(0.1, 0.35, 1.0) * exp(-db * 30.0) * 0.08 + vec3(0.5, 0.75, 1.0) * exp(-db * 90.0) * 0.12;
            // a thin tether forms between them as they get close
            vec3 pa = p - red;
            vec3 ba = blue - red;
            float h = clamp(dot(pa, ba) / max(dot(ba, ba), 0.0001), 0.0, 1.0);
            float tether = length(pa - ba * h);
            glow += mix(vec3(1.0, 0.3, 0.3), vec3(0.4, 0.5, 1.0), h) * exp(-tether * 70.0) * 0.03 * approach;
        } else {
            float r = length(p);
            float shell = max(r - sphere, 0.0);
            vec3 around = p / max(r, 0.001) * 3.5 + vec3(time * 1.7, 0.0, time * 1.1);
            float crackle = pow(1.0 - abs(hpNoise(around + r * 3.0) * 2.0 - 1.0), 10.0);
            glow += vec3(0.6, 0.2, 1.0) * exp(-shell * 14.0) * 0.05 * fadeOut;
            glow += vec3(0.95, 0.8, 1.0) * exp(-max(r - sphere * 0.5, 0.0) * 30.0) * 0.06 * fadeOut;
            glow += vec3(0.8, 0.5, 1.0) * crackle * exp(-abs(r - sphere) * 18.0) * 0.12 * fadeOut;
            float wave = burst * 1.2;
            glow += vec3(0.7, 0.5, 1.0) * exp(-abs(r - wave) * 40.0) * 0.06 * (1.0 - burst);
        }
        t += 0.035;
        if (t > 3.5) break;
    }
    float alpha = clamp(max(max(glow.r, glow.g), glow.b) * 1.1, 0.0, 1.0);
    vec3 color = 1.0 - exp(-glow * 1.6);
    return vec4(color / max(alpha, 0.0001), alpha);
}
