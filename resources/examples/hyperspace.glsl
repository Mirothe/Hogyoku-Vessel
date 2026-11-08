// the jump to lightspeed. starlight gets stretched into lanes around a tunnel with a white hot core ahead
// and a shock ring pulsing around it. look back and the streaks go red

float hsHash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float hsNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hsHash(i), hsHash(i + vec2(1.0, 0.0)), f.x), mix(hsHash(i + vec2(0.0, 1.0)), hsHash(i + vec2(1.0, 1.0)), f.x), f.y);
}

vec3 sky(vec3 dir, float time) {
    float ahead = -dir.z;
    float radius = length(dir.xy);
    float angle = atan(dir.y, dir.x);
    // how far down the tunnel this ray looks, rays near the middle see further
    float depth = abs(ahead) / max(radius, 0.002);
    vec3 shift = ahead > 0.0 ? vec3(0.55, 0.75, 1.0) : vec3(1.0, 0.35, 0.2);

    vec3 color = vec3(0.0);
    for (int layer = 0; layer < 4; layer++) {
        float fl = float(layer);
        float lanes = 64.0 + fl * 48.0;
        float a = (angle / 6.2831853 + 0.5) * lanes;
        float lane = floor(a);
        float across = abs(fract(a) - 0.5);
        float h = hsHash(vec2(lane, fl * 7.0));

        float z = depth * (0.35 + fl * 0.12) - time * (3.0 + h * 4.0) * sign(ahead) + h * 40.0;
        float piece = fract(z);
        float on = step(0.5, hsHash(vec2(lane + fl * 31.0, floor(z))));
        float streak = smoothstep(0.0, 0.08, piece) * (1.0 - smoothstep(0.08, 0.6, piece));
        float width = 1.0 - smoothstep(0.04, 0.22 + 0.15 * h, across);
        vec3 tint = mix(shift, vec3(1.0), h * 0.7);
        color += tint * streak * on * width * (0.5 + 0.8 * h);
    }
    color *= smoothstep(0.15, 1.2, depth);

    vec2 swirlPos = vec2(cos(angle), sin(angle)) * 2.5 + vec2(depth * 0.4 - time * 1.5, depth * 0.25);
    float swirl = hsNoise(swirlPos) * 0.6 + hsNoise(swirlPos * 2.3) * 0.4;
    color += shift * swirl * swirl * 0.35 * smoothstep(0.5, 4.0, depth);

    if (ahead > 0.0) {
        color += vec3(0.85, 0.92, 1.0) * pow(ahead, 60.0) * 3.0;
        color += vec3(0.3, 0.55, 1.0) * pow(ahead, 9.0) * 0.6;
        float ringSize = 0.32 + 0.04 * sin(time * 2.5);
        float ring = exp(-abs(radius - ringSize) * 30.0) * (0.6 + 0.4 * hsNoise(vec2(angle * 6.0, time * 3.0)));
        color += vec3(0.45, 0.7, 1.0) * ring * 0.9;
    }
    return 1.0 - exp(-color * 1.5);
}
