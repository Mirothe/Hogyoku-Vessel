// the mandelbulb, a 3D fractal. every point is pushed through z = z^8 + p a few times, and the points that
// never fly off make up the shape. a distance estimate lets us march straight to its surface, and how close
// each point's orbit swings to the middle (the orbit trap) picks its colour. the power breathes slowly
// origin is where the ray enters the item's box (-1 to 1 on every axis), return alpha 0 for empty space

mat3 mbSpin(float time) {
    float a = time * 0.25;
    float b = 0.45;
    mat3 yaw = mat3(cos(a), 0.0, sin(a), 0.0, 1.0, 0.0, -sin(a), 0.0, cos(a));
    mat3 tilt = mat3(1.0, 0.0, 0.0, 0.0, cos(b), -sin(b), 0.0, sin(b), cos(b));
    return tilt * yaw;
}

float mbDistance(vec3 p, float power, out float trap) {
    vec3 z = p;
    float dr = 1.0;
    float r = length(z);
    trap = 1e9;
    for (int i = 0; i < 7; i++) {
        r = length(z);
        if (r > 2.0) break;
        float theta = acos(clamp(z.y / max(r, 1e-6), -1.0, 1.0)) * power;
        float phi = atan(z.z, z.x) * power;
        dr = pow(r, power - 1.0) * power * dr + 1.0;
        z = pow(r, power) * vec3(sin(theta) * cos(phi), cos(theta), sin(theta) * sin(phi)) + p;
        trap = min(trap, dot(z, z));
    }
    return 0.5 * log(max(r, 1e-6)) * r / dr;
}

// the fractal is about 1.2 across, so it's scaled up to fill the box
float mbScene(vec3 p, float power, out float trap) {
    return mbDistance(p * 1.3, power, trap) / 1.3;
}

vec4 object(vec3 origin, vec3 dir, float time) {
    mat3 spin = mbSpin(time);
    vec3 ro = origin * spin;
    vec3 rd = dir * spin;
    float power = 8.0 + sin(time * 0.3) * 1.5;

    float t = 0.0;
    float trap = 0.0;
    float closest = 9.0;
    int steps = 0;
    bool hit = false;
    for (int i = 0; i < 90; i++) {
        float d = mbScene(ro + rd * t, power, trap);
        closest = min(closest, d);
        if (d < 0.0015) {
            hit = true;
            break;
        }
        t += d * 0.9;
        steps = i;
        if (t > 3.5) break;
    }
    if (!hit) {
        float halo = exp(-max(closest, 0.0) * 22.0) * 0.4;
        return vec4(1.0, 0.6, 0.3, halo);
    }

    vec3 p = ro + rd * t;
    float ignore;
    vec2 k = vec2(0.0015, 0.0);
    vec3 n = normalize(vec3(mbScene(p + k.xyy, power, ignore) - mbScene(p - k.xyy, power, ignore),
                            mbScene(p + k.yxy, power, ignore) - mbScene(p - k.yxy, power, ignore),
                            mbScene(p + k.yyx, power, ignore) - mbScene(p - k.yyx, power, ignore)));

    // deep folds take more steps to reach, which doubles as cheap ambient occlusion
    float occlusion = clamp(1.25 - float(steps) / 55.0, 0.35, 1.0);
    vec3 inner = vec3(1.0, 0.5, 0.12);
    vec3 outer = vec3(0.1, 0.6, 0.85);
    // a wave over the trap distance instead of a straight ramp, so every part of the fractal gets both colours
    vec3 albedo = mix(outer, inner, 0.5 + 0.5 * cos(log(trap + 1e-4) * 2.5));
    vec3 lamp = normalize(vec3(0.5, 0.8, 0.4));
    float diffuse = max(dot(n, lamp), 0.0);
    float rim = pow(1.0 - max(dot(n, -rd), 0.0), 3.0);
    float shine = pow(max(dot(reflect(rd, n), lamp), 0.0), 24.0);
    vec3 color = albedo * (0.35 + diffuse * 1.1) * occlusion + vec3(1.0, 0.85, 0.7) * shine * 0.6 + outer * rim * 0.5;
    return vec4(1.0 - exp(-color * 1.6), 1.0);
}
