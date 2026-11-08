// the hogyoku: a faceted crystal with glowing edges and blue energy swirling around inside it.
// the shape is a handful of cut planes, and the inside is marched after the ray bends through the glass
// origin is where the ray enters the item's box (-1 to 1 on every axis), return alpha 0 for empty space

float hgHash(vec3 p) {
    return fract(sin(dot(p, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
}

float hgNoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hgHash(i), hgHash(i + vec3(1.0, 0.0, 0.0)), f.x),
                   mix(hgHash(i + vec3(0.0, 1.0, 0.0)), hgHash(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
               mix(mix(hgHash(i + vec3(0.0, 0.0, 1.0)), hgHash(i + vec3(1.0, 0.0, 1.0)), f.x),
                   mix(hgHash(i + vec3(0.0, 1.0, 1.0)), hgHash(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z);
}

mat3 hgSpin(float time) {
    float a = time * 0.35;
    float b = time * 0.22;
    mat3 yaw = mat3(cos(a), 0.0, sin(a), 0.0, 1.0, 0.0, -sin(a), 0.0, cos(a));
    mat3 tilt = mat3(1.0, 0.0, 0.0, 0.0, cos(b), -sin(b), 0.0, sin(b), cos(b));
    return yaw * tilt;
}

// keeps the two biggest values seen so far, the gap between them tells us how close we are to an edge
void hgTake(inout float first, inout float second, float v) {
    if (v > first) {
        second = first;
        first = v;
    } else if (v > second) {
        second = v;
    }
}

float hgShape(vec3 p, out float edge) {
    vec3 a = abs(p);
    float first = -10.0;
    float second = -10.0;
    hgTake(first, second, a.x - 0.5);
    hgTake(first, second, a.y - 0.5);
    hgTake(first, second, a.z - 0.5);
    hgTake(first, second, dot(a, vec3(0.57735)) - 0.6);
    hgTake(first, second, dot(a.xy, vec2(0.70711)) - 0.58);
    hgTake(first, second, dot(a.yz, vec2(0.70711)) - 0.58);
    hgTake(first, second, dot(a.zx, vec2(0.70711)) - 0.58);
    edge = first - second;
    return first;
}

vec4 object(vec3 origin, vec3 dir, float time) {
    mat3 spin = hgSpin(time);
    vec3 ro = origin * spin;
    vec3 rd = dir * spin;

    float t = 0.0;
    float closest = 9.0;
    float edge = 1.0;
    bool hit = false;
    for (int i = 0; i < 64; i++) {
        float d = hgShape(ro + rd * t, edge);
        closest = min(closest, d);
        if (d < 0.001) {
            hit = true;
            break;
        }
        t += d;
        if (t > 3.5) break;
    }
    if (!hit) {
        float halo = exp(-max(closest, 0.0) * 14.0) * 0.55;
        return vec4(0.45, 0.65, 1.0, halo);
    }

    vec3 p = ro + rd * t;
    float ignore;
    vec2 k = vec2(0.002, 0.0);
    vec3 n = normalize(vec3(hgShape(p + k.xyy, ignore) - hgShape(p - k.xyy, ignore),
                            hgShape(p + k.yxy, ignore) - hgShape(p - k.yxy, ignore),
                            hgShape(p + k.yyx, ignore) - hgShape(p - k.yyx, ignore)));
    float fresnel = 0.08 + 0.92 * pow(1.0 - max(dot(n, -rd), 0.0), 4.0);

    vec3 inside = refract(rd, n, 0.67);
    vec3 q = p;
    vec3 energy = vec3(0.0);
    float backEdge = 1.0;
    for (int i = 0; i < 48; i++) {
        q += inside * 0.025;
        if (hgShape(q, backEdge) > 0.0) break;
        float swirl = hgNoise(q * 5.0 + vec3(0.0, time * 0.6, time * 0.2));
        float strands = pow(1.0 - abs(swirl * 2.0 - 1.0), 6.0);
        energy += vec3(0.35, 0.6, 1.0) * strands * 0.07 + vec3(0.8, 0.9, 1.0) * exp(-length(q) * 9.0) * 0.12;
    }

    vec3 body = vec3(0.01, 0.02, 0.07) + energy;
    // the edges on the far side show through the glass too, just dimmer
    body += vec3(0.75, 0.85, 1.0) * exp(-backEdge * 90.0) * 0.6;
    vec3 lamp = normalize(vec3(0.4, 0.8, 0.3));
    vec3 shine = vec3(0.6, 0.75, 1.0) * pow(max(dot(reflect(rd, n), lamp), 0.0), 20.0) * 1.5;
    vec3 color = mix(body, vec3(0.3, 0.45, 0.8) + shine, fresnel * 0.6);
    color += vec3(0.85, 0.92, 1.0) * exp(-edge * 160.0) * 2.4;
    return vec4(1.0 - exp(-color * 1.2), 1.0);
}
