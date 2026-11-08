// a brass gyroscope. three rings sit inside each other like a gimbal, each one turning on its own axis
// inside the one around it, with a glowing core in the middle. the metal reflects a made up room around it,
// which is how polished metal gets its look without any real lights
// origin is where the ray enters the item's box (-1 to 1 on every axis), return alpha 0 for empty space

mat3 gyAround(vec3 axis, float a) {
    float c = cos(a);
    float s = sin(a);
    vec3 k = axis * (1.0 - c);
    return mat3(k.x * axis.x + c, k.x * axis.y + s * axis.z, k.x * axis.z - s * axis.y,
                k.y * axis.x - s * axis.z, k.y * axis.y + c, k.y * axis.z + s * axis.x,
                k.z * axis.x + s * axis.y, k.z * axis.y - s * axis.x, k.z * axis.z + c);
}

// a flat band, wider than it is thick, with little notches cut round it
float gyRing(vec3 p, float radius) {
    float around = atan(p.z, p.x);
    float notch = smoothstep(0.75, 1.0, abs(sin(around * 18.0))) * 0.006;
    vec2 q = vec2(length(p.xz) - radius, p.y);
    vec2 size = vec2(0.03, 0.06);
    vec2 d = abs(q) - size;
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - 0.01 + notch;
}

float gyScene(vec3 p, float time, out float part) {
    // the whole thing leans a little so no ring ever lines up exactly edge on to you
    p = p * gyAround(normalize(vec3(0.3, 0.0, 1.0)), 0.5);
    vec3 a = p * gyAround(vec3(1.0, 0.0, 0.0), time * 0.7 + 0.9);
    vec3 b = a * gyAround(vec3(0.0, 0.0, 1.0), time * 1.1);
    vec3 c = b * gyAround(vec3(1.0, 0.0, 0.0), time * 1.7);
    float outer = gyRing(a.yxz, 0.82);
    float middle = gyRing(b, 0.64);
    float inner = gyRing(c.xzy, 0.47);
    float axle = length(c.xz) - 0.025;
    axle = max(axle, abs(c.y) - 0.47);
    float core = length(p) - 0.17;
    float metal = min(min(outer, middle), min(inner, axle));
    part = core < metal ? 1.0 : 0.0;
    return min(metal, core);
}

vec3 gyRoom(vec3 r) {
    vec3 color = mix(vec3(0.18, 0.15, 0.12), vec3(0.95, 0.9, 0.82), smoothstep(-0.5, 0.8, r.y));
    color += vec3(1.2) * smoothstep(0.96, 0.99, dot(r, normalize(vec3(0.5, 0.8, 0.3))));
    color += vec3(0.4, 0.6, 0.9) * smoothstep(0.9, 0.97, dot(r, normalize(vec3(-0.7, 0.2, -0.6)))) * 0.6;
    return color;
}

vec4 object(vec3 origin, vec3 dir, float time) {
    float t = 0.0;
    float part = 0.0;
    float closest = 9.0;
    bool hit = false;
    for (int i = 0; i < 90; i++) {
        float d = gyScene(origin + dir * t, time, part);
        if (part > 0.5) closest = min(closest, d);
        if (d < 0.0008) {
            hit = true;
            break;
        }
        t += d;
        if (t > 3.5) break;
    }
    float pulse = 0.8 + 0.2 * sin(time * 3.0);
    vec3 glow = vec3(0.3, 0.8, 1.0) * exp(-max(closest, 0.0) * 9.0) * pulse;
    if (!hit) {
        float a = clamp(max(glow.r, max(glow.g, glow.b)), 0.0, 1.0);
        return vec4(glow / max(a, 1e-3), a * 0.8);
    }

    vec3 p = origin + dir * t;
    float m;
    vec2 k = vec2(0.001, 0.0);
    vec3 n = normalize(vec3(gyScene(p + k.xyy, time, m) - gyScene(p - k.xyy, time, m),
                            gyScene(p + k.yxy, time, m) - gyScene(p - k.yxy, time, m),
                            gyScene(p + k.yyx, time, m) - gyScene(p - k.yyx, time, m)));
    if (part > 0.5) {
        float facing = max(dot(n, -dir), 0.0);
        return vec4(mix(vec3(0.2, 0.7, 1.0), vec3(0.95, 1.0, 1.0), pow(facing, 1.5)) * pulse, 1.0);
    }

    // a quick look along the normal: if the scene is close by, light can't reach in there as well
    float near = gyScene(p + n * 0.06, time, m);
    float occlusion = clamp(near / 0.06, 0.3, 1.0);
    vec3 brass = vec3(1.0, 0.72, 0.36);
    float fresnel = 0.6 + 0.4 * pow(1.0 - max(dot(n, -dir), 0.0), 5.0);
    vec3 color = brass * gyRoom(reflect(dir, n)) * fresnel * occlusion;
    color += vec3(0.3, 0.8, 1.0) * max(dot(n, -normalize(p)), 0.0) * exp(-length(p) * 4.0) * pulse * 1.5;
    return vec4(1.0 - exp(-color * 1.5), 1.0);
}
