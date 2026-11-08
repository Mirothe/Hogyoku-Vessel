// a small black hole floating inside the item. rays get bent by its gravity on the way through,
// so the back of the disk curls up over the top and a thin bright ring hugs the shadow
// origin is where the ray enters the item's box (-1 to 1 on every axis), return alpha 0 for empty space

float bhHash(vec3 p) {
    return fract(sin(dot(p, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
}

float bhNoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(bhHash(i), bhHash(i + vec3(1.0, 0.0, 0.0)), f.x),
                   mix(bhHash(i + vec3(0.0, 1.0, 0.0)), bhHash(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
               mix(mix(bhHash(i + vec3(0.0, 0.0, 1.0)), bhHash(i + vec3(1.0, 0.0, 1.0)), f.x),
                   mix(bhHash(i + vec3(0.0, 1.0, 1.0)), bhHash(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z);
}

float bhFbm(vec3 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        sum += amp * bhNoise(p);
        p = p * 2.07 + vec3(3.1, 1.7, 5.3);
        amp *= 0.5;
    }
    return sum;
}

// the disk lives in units of the horizon radius here, it runs from 3 out to 8
vec4 bhDisk(vec3 hit, vec3 vel, float time) {
    float r = length(hit.xz);
    if (r < 2.8 || r > 8.2) return vec4(0.0);

    float spin = time * 6.0 / pow(r, 1.5);
    float c = cos(spin);
    float s = sin(spin);
    vec2 p = vec2(c * hit.x - s * hit.z, s * hit.x + c * hit.z);
    float gas = bhFbm(vec3(p * 0.6, r * 0.35));
    float bands = 0.55 + 0.45 * sin(r * 5.0 + gas * 6.0);
    float edge = smoothstep(2.8, 3.6, r) * (1.0 - smoothstep(5.8, 8.2, r));
    float density = edge * (0.2 + 0.8 * gas) * bands;

    float heat = clamp((r - 2.8) / 5.4, 0.0, 1.0);
    vec3 hot = mix(vec3(1.0, 0.96, 0.9), vec3(1.0, 0.55, 0.18), smoothstep(0.0, 0.4, heat));
    hot = mix(hot, vec3(0.6, 0.12, 0.03), smoothstep(0.4, 1.0, heat));

    vec3 orbit = normalize(vec3(-hit.z, 0.0, hit.x));
    float beam = 1.0 + 0.7 * dot(orbit, -vel);
    beam = beam * beam * beam;
    return vec4(hot * beam * (3.2 - heat * 2.0), clamp(density * 2.2, 0.0, 1.0));
}

vec4 object(vec3 origin, vec3 dir, float time) {
    float horizon = 0.115;
    vec3 pos = origin / horizon;
    vec3 vel = dir;
    vec3 spinAxis = cross(pos, vel);
    float h2 = dot(spinAxis, spinAxis);
    float side = pos.y;

    vec3 light = vec3(0.0);
    float cover = 0.0;
    float haze = 0.0;
    bool swallowed = false;
    for (int i = 0; i < 150; i++) {
        float r = length(pos);
        if (r < 1.0) {
            swallowed = true;
            break;
        }
        if (r > 16.0 && dot(pos, vel) > 0.0) break;

        float dt = clamp(0.06 * r, 0.02, 0.8);
        haze += dt * 0.05 / (r * r);
        vel = normalize(vel - 1.5 * h2 * pos / pow(r, 5.0) * dt);
        vec3 next = pos + vel * dt;
        if (side * next.y < 0.0 && cover < 0.99) {
            vec4 disk = bhDisk(mix(pos, next, side / (side - next.y)), vel, time);
            light += (1.0 - cover) * disk.rgb * disk.a;
            cover += (1.0 - cover) * disk.a;
        }
        side = next.y;
        pos = next;
    }

    // the glow fades out before the box edge so the cube itself never shows
    float glowAlpha = clamp(haze * 1.2 - 0.015, 0.0, 0.6);
    vec3 color = light + (1.0 - cover) * vec3(1.0, 0.55, 0.25) * glowAlpha;
    float alpha = cover + (1.0 - cover) * glowAlpha;
    if (swallowed) alpha = 1.0;
    color = 1.0 - exp(-color * 1.4);
    return vec4(color / max(alpha, 0.0001), alpha);
}
