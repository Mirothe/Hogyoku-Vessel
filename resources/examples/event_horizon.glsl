// a black hole that bends light. each ray is stepped through the hole's gravity, so the far side of the disk
// shows up wrapped over the top and the stars behind it smear into rings

float ehHash(vec3 p) {
    return fract(sin(dot(p, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
}

float ehNoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(ehHash(i), ehHash(i + vec3(1.0, 0.0, 0.0)), f.x),
                   mix(ehHash(i + vec3(0.0, 1.0, 0.0)), ehHash(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
               mix(mix(ehHash(i + vec3(0.0, 0.0, 1.0)), ehHash(i + vec3(1.0, 0.0, 1.0)), f.x),
                   mix(ehHash(i + vec3(0.0, 1.0, 1.0)), ehHash(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z);
}

float ehFbm(vec3 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        sum += amp * ehNoise(p);
        p = p * 2.07 + vec3(3.1, 1.7, 5.3);
        amp *= 0.5;
    }
    return sum;
}

vec3 ehBackground(vec3 d) {
    vec3 color = vec3(0.0);
    for (int i = 0; i < 3; i++) {
        float scale = 70.0 + float(i) * 85.0;
        vec3 cell = floor(d * scale);
        float h = ehHash(cell + float(i) * 17.0);
        float star = step(0.97, h) * (1.0 - smoothstep(0.0, 0.5, length(fract(d * scale) - 0.5)));
        color += mix(vec3(0.7, 0.8, 1.0), vec3(1.0, 0.85, 0.7), fract(h * 37.0)) * star * (0.6 + 0.6 * fract(h * 91.0));
    }
    float band = exp(-abs(dot(d, normalize(vec3(0.35, 1.0, 0.1)))) * 4.0);
    float gas = ehFbm(d * 3.0);
    color += vec3(0.35, 0.22, 0.55) * band * gas * gas * 0.9;
    color += vec3(0.04, 0.07, 0.14) * gas * 0.35;
    return color;
}

// one hit on the accretion disk, alpha says how thick the gas is there
vec4 ehDisk(vec3 local, vec3 vel, vec3 up, vec3 e1, vec3 e2, float time) {
    float r = length(local);
    if (r < 2.6 || r > 10.0) return vec4(0.0);

    vec2 plane = vec2(dot(local, e1), dot(local, e2));
    float spin = time * 2.4 / pow(r, 1.5);
    float c = cos(spin);
    float s = sin(spin);
    vec2 p = vec2(c * plane.x - s * plane.y, s * plane.x + c * plane.y);

    float gas = ehFbm(vec3(p * 1.2, r * 0.6));
    float bands = 0.55 + 0.45 * sin(r * 7.5 + gas * 6.0);
    float edge = smoothstep(2.6, 3.4, r) * (1.0 - smoothstep(6.0, 10.0, r));
    float density = edge * (0.2 + 0.8 * gas) * bands;

    float heat = clamp((r - 2.6) / 6.5, 0.0, 1.0);
    vec3 hot = mix(vec3(1.0, 0.96, 0.9), vec3(1.0, 0.55, 0.18), smoothstep(0.0, 0.4, heat));
    hot = mix(hot, vec3(0.55, 0.1, 0.03), smoothstep(0.4, 1.0, heat));

    // the side spinning toward you is brighter (doppler beaming)
    vec3 orbit = normalize(cross(up, local));
    float beam = 1.0 + 0.7 * dot(orbit, -vel);
    beam = beam * beam * beam;

    return vec4(hot * beam * (2.6 - heat * 1.8), clamp(density * 1.7, 0.0, 1.0));
}

vec3 sky(vec3 dir, float time) {
    vec3 hole = vec3(0.0, 0.5, -11.0);
    vec3 up = normalize(vec3(0.0, 1.0, 0.16));
    vec3 e1 = normalize(cross(up, vec3(0.0, 0.0, 1.0)));
    vec3 e2 = cross(up, e1);

    vec3 pos = vec3(0.0);
    vec3 vel = dir;
    vec3 rel = pos - hole;
    vec3 spinAxis = cross(rel, vel);
    float h2 = dot(spinAxis, spinAxis);
    float side = dot(rel, up);

    vec3 light = vec3(0.0);
    float cover = 0.0;
    float haze = 0.0;
    bool swallowed = false;

    for (int i = 0; i < 160; i++) {
        rel = pos - hole;
        float r = length(rel);
        if (r < 1.0) {
            swallowed = true;
            break;
        }
        if (r > 34.0 && dot(rel, vel) > 0.0) break;

        float dt = clamp(0.07 * r, 0.025, 1.4);
        haze += dt * 0.05 / (r * r);
        vel = normalize(vel - 1.5 * h2 * rel / pow(r, 5.0) * dt);
        vec3 next = pos + vel * dt;

        float nextSide = dot(next - hole, up);
        if (side * nextSide < 0.0 && cover < 0.99) {
            vec3 hit = mix(pos, next, side / (side - nextSide)) - hole;
            vec4 disk = ehDisk(hit, vel, up, e1, e2, time);
            light += (1.0 - cover) * disk.rgb * disk.a;
            cover += (1.0 - cover) * disk.a;
        }
        side = nextSide;
        pos = next;
    }

    vec3 back = swallowed ? vec3(0.0) : ehBackground(vel);
    vec3 color = light + (1.0 - cover) * (back + vec3(1.0, 0.55, 0.25) * haze * 1.4);
    return 1.0 - exp(-color * 1.4);
}
