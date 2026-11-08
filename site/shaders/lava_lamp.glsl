// a lava lamp. warm blobs rise and sink inside a glass tube and melt into each other where they meet,
// that's a smooth minimum between spheres (metaballs). the glass is a cylinder found exactly, the blobs
// and the metal caps are marched, and the liquid gets darker the more of it the ray passes through
// origin is where the ray enters the item's box (-1 to 1 on every axis), return alpha 0 for empty space

const float LL_RADIUS = 0.36;
const float LL_TOP = 0.66;

float llBlend(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

float llBlobs(vec3 p, float time) {
    float d = length((p - vec3(0.0, -LL_TOP, 0.0)) * vec3(1.0, 2.6, 1.0)) / 2.6 - 0.12;
    for (int i = 0; i < 5; i++) {
        float f = float(i);
        float speed = 0.22 + f * 0.05;
        float y = sin(time * speed + f * 1.9) * 0.48;
        vec3 c = vec3(sin(time * 0.4 + f * 2.3) * 0.12, y, cos(time * 0.35 + f * 1.7) * 0.12);
        // blobs stretch tall while they move fast and squash round at the turning points
        float stretch = 1.0 + abs(cos(time * speed + f * 1.9)) * 0.35;
        float r = 0.1 + mod(f * 0.37, 0.09);
        d = llBlend(d, length((p - c) * vec3(1.0, 1.0 / stretch, 1.0)) * min(1.0, stretch) - r, 0.16);
    }
    return max(d, length(p.xz) - LL_RADIUS + 0.03);
}

float llCaps(vec3 p) {
    vec3 q = vec3(length(p.xz), abs(p.y) - (LL_TOP + 0.13), 0.0);
    // a cap that narrows towards the tube, made of a rounded cylinder cut by a slanted plane
    float body = max(q.x - 0.42 + q.y * 0.35, abs(q.y) - 0.13) - 0.01;
    return body;
}

float llScene(vec3 p, float time, out float metal) {
    float blobs = llBlobs(p, time);
    float caps = llCaps(p);
    metal = caps < blobs ? 1.0 : 0.0;
    return min(blobs, caps);
}

vec4 object(vec3 origin, vec3 dir, float time) {
    float t = 0.0;
    float metal = 0.0;
    bool hit = false;
    for (int i = 0; i < 80; i++) {
        float d = llScene(origin + dir * t, time, metal);
        if (d < 0.001) {
            hit = true;
            break;
        }
        t += d;
        if (t > 3.5) break;
    }

    // where the ray goes in and out of the glass tube
    float a = dot(dir.xz, dir.xz);
    float b = dot(origin.xz, dir.xz);
    float c = dot(origin.xz, origin.xz) - LL_RADIUS * LL_RADIUS;
    float disc = b * b - a * c;
    float glassIn = -1.0;
    float glassOut = -1.0;
    if (disc > 0.0 && a > 1e-6) {
        glassIn = (-b - sqrt(disc)) / a;
        glassOut = (-b + sqrt(disc)) / a;
        float y = origin.y + dir.y * glassIn;
        if (abs(y) > LL_TOP) glassIn = -1.0;
    }

    vec4 color = vec4(0.0);
    if (glassIn > 0.0) {
        float through = (hit && metal < 0.5 ? t : glassOut) - glassIn;
        vec3 liquid = vec3(0.35, 0.05, 0.35);
        color = vec4(liquid, 1.0 - exp(-through * 2.2));
    }

    if (hit) {
        vec3 p = origin + dir * t;
        float m;
        vec2 k = vec2(0.002, 0.0);
        vec3 n = normalize(vec3(llScene(p + k.xyy, time, m) - llScene(p - k.xyy, time, m),
                                llScene(p + k.yxy, time, m) - llScene(p - k.yxy, time, m),
                                llScene(p + k.yyx, time, m) - llScene(p - k.yyx, time, m)));
        vec3 lamp = normalize(vec3(0.4, 0.9, 0.5));
        if (metal > 0.5) {
            vec3 r = reflect(dir, n);
            vec3 sky = mix(vec3(0.08), vec3(0.85), smoothstep(-0.2, 0.8, r.y));
            vec3 steel = vec3(0.7, 0.72, 0.78) * sky + pow(max(dot(r, lamp), 0.0), 40.0);
            color = vec4(steel, 1.0);
        } else {
            // the blobs glow from inside, brightest where you look straight at them
            float facing = max(dot(n, -dir), 0.0);
            vec3 wax = mix(vec3(0.9, 0.1, 0.05), vec3(1.0, 0.75, 0.2), pow(facing, 2.0));
            wax += vec3(1.0, 0.9, 0.6) * pow(max(dot(reflect(dir, n), lamp), 0.0), 30.0) * 0.6;
            color = vec4(mix(wax, color.rgb, color.a * 0.35), 1.0);
        }
    }

    if (glassIn > 0.0) {
        vec3 n = normalize(vec3((origin + dir * glassIn).x, 0.0, (origin + dir * glassIn).z));
        float fresnel = pow(1.0 - abs(dot(n, dir)), 4.0);
        float streak = smoothstep(0.92, 1.0, dot(n.xz, normalize(vec2(-0.6, 0.8))));
        float edge = fresnel * 0.6 + streak * 0.5;
        color.rgb = mix(color.rgb, vec3(1.0), edge * (1.0 - color.a * 0.3));
        color.a = max(color.a, edge);
    }
    return color;
}
