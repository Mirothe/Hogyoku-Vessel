// a blob of liquid chrome. the surface ripples with a few sine waves running through it, and small drops orbit
// round, pull away and fall back in, joined to the blob with a smooth minimum so they stretch like liquid.
// chrome has no colour of its own, it's the bright room it reflects that makes it look like metal
// origin is where the ray enters the item's box (-1 to 1 on every axis), return alpha 0 for empty space

float lmBlend(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

float lmScene(vec3 p, float time) {
    float ripple = sin(p.x * 7.0 + time * 1.3) * sin(p.y * 6.0 - time * 1.1) * sin(p.z * 8.0 + time * 0.9) * 0.035;
    ripple += sin(length(p) * 14.0 - time * 3.0) * 0.008;
    float d = length(p) - 0.5 + ripple;
    for (int i = 0; i < 4; i++) {
        float f = float(i);
        float a = time * (0.6 + f * 0.17) + f * 1.57;
        // each drop drifts out and back in on its own beat
        float reach = 0.45 + 0.22 * (0.5 + 0.5 * sin(time * 0.8 + f * 2.1));
        vec3 c = vec3(cos(a), sin(a * 1.3 + f) * 0.6, sin(a)) * reach;
        d = lmBlend(d, length(p - c) - 0.09 - f * 0.012, 0.2);
    }
    return d;
}

// a studio: dark floor, bright sky and a couple of long softbox lights
vec3 lmRoom(vec3 r) {
    vec3 color = mix(vec3(0.06), vec3(0.75, 0.78, 0.85), smoothstep(-0.5, 0.5, r.y));
    color += vec3(1.6) * smoothstep(0.9, 0.98, dot(r, normalize(vec3(0.3, 0.6, 0.75))));
    color += vec3(1.4) * smoothstep(0.05, 0.0, abs(r.y - 0.55)) * smoothstep(0.2, 0.6, r.x + 0.3);
    color += vec3(1.1, 1.0, 0.95) * smoothstep(0.08, 0.0, abs(r.x + 0.75)) * smoothstep(-0.2, 0.3, r.y);
    color += vec3(0.25, 0.5, 1.0) * smoothstep(0.85, 1.0, -r.z) * 0.5;
    return color;
}

vec4 object(vec3 origin, vec3 dir, float time) {
    float t = 0.0;
    bool hit = false;
    for (int i = 0; i < 90; i++) {
        float d = lmScene(origin + dir * t, time);
        if (d < 0.001) {
            hit = true;
            break;
        }
        // the ripples bend distances a little, so shorter steps keep the march from skipping through
        t += d * 0.8;
        if (t > 3.5) break;
    }
    if (!hit) return vec4(0.0);

    vec3 p = origin + dir * t;
    vec2 k = vec2(0.0015, 0.0);
    vec3 n = normalize(vec3(lmScene(p + k.xyy, time) - lmScene(p - k.xyy, time),
                            lmScene(p + k.yxy, time) - lmScene(p - k.yxy, time),
                            lmScene(p + k.yyx, time) - lmScene(p - k.yyx, time)));
    vec3 r = reflect(dir, n);
    float fresnel = 0.55 + 0.45 * pow(1.0 - max(dot(n, -dir), 0.0), 4.0);
    vec3 color = lmRoom(r) * vec3(0.92, 0.95, 1.0) * fresnel;
    return vec4(1.0 - exp(-color * 1.8), 1.0);
}
