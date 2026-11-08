// a gas giant with rings, drawn with plain ray math against one sphere and one flat ring, so nothing gets marched.
// the planet throws a shadow across the rings and the rings throw one back onto the planet
// origin is where the ray enters the item's box (-1 to 1 on every axis), return alpha 0 for empty space

float plHash(vec3 p) {
    return fract(sin(dot(p, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
}

float plNoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(plHash(i), plHash(i + vec3(1.0, 0.0, 0.0)), f.x),
                   mix(plHash(i + vec3(0.0, 1.0, 0.0)), plHash(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
               mix(mix(plHash(i + vec3(0.0, 0.0, 1.0)), plHash(i + vec3(1.0, 0.0, 1.0)), f.x),
                   mix(plHash(i + vec3(0.0, 1.0, 1.0)), plHash(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z);
}

float plFbm(vec3 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        sum += amp * plNoise(p);
        p = p * 2.1 + vec3(1.3, 7.1, 3.7);
        amp *= 0.5;
    }
    return sum;
}

float plRings(float r) {
    if (r < 0.6 || r > 0.98) return 0.0;
    float dust = 0.35 + 0.65 * plNoise(vec3(r * 55.0, 0.0, 0.0));
    float fine = 0.6 + 0.4 * sin(r * 170.0);
    float gap = smoothstep(0.01, 0.02, abs(r - 0.8));
    return dust * fine * gap * smoothstep(0.6, 0.65, r) * (1.0 - smoothstep(0.92, 0.98, r));
}

// how far along the ray it hits a sphere of this radius at the origin, or -1
float plSphere(vec3 ro, vec3 rd, float radius) {
    float b = dot(ro, rd);
    float c = dot(ro, ro) - radius * radius;
    float h = b * b - c;
    if (h < 0.0) return -1.0;
    return -b - sqrt(h);
}

vec4 object(vec3 origin, vec3 dir, float time) {
    vec3 sun = normalize(vec3(0.8, 0.35, 0.5));
    vec3 ringUp = normalize(vec3(0.25, 1.0, 0.1));
    float radius = 0.5;

    float tPlanet = plSphere(origin, dir, radius);
    float denom = dot(dir, ringUp);
    float tRing = abs(denom) > 0.0001 ? -dot(origin, ringUp) / denom : -1.0;

    vec4 ring = vec4(0.0);
    if (tRing > 0.0 && (tPlanet < 0.0 || tRing < tPlanet)) {
        vec3 rp = origin + dir * tRing;
        float density = plRings(length(rp));
        float lit = plSphere(rp, sun, radius) > 0.0 ? 0.12 : 1.0;
        vec3 tint = mix(vec3(0.75, 0.62, 0.48), vec3(0.95, 0.9, 0.8), plNoise(vec3(length(rp) * 30.0, 1.0, 0.0)));
        ring = vec4(tint * lit * (0.35 + 0.65 * abs(dot(ringUp, sun))), clamp(density * 0.9, 0.0, 1.0));
    }

    vec4 body = vec4(0.0);
    if (tPlanet > 0.0) {
        vec3 hp = origin + dir * tPlanet;
        vec3 n = hp / radius;
        float a = time * 0.08;
        vec3 turned = vec3(cos(a) * n.x - sin(a) * n.z, n.y, sin(a) * n.x + cos(a) * n.z);

        // squashing the noise vertically is what stretches it into bands
        float flow = plFbm(vec3(turned.x * 2.0, turned.y * 11.0, turned.z * 2.0) + vec3(time * 0.02, 0.0, 0.0));
        float stripes = 0.5 + 0.5 * sin(turned.y * 22.0 + flow * 5.0);
        vec3 color = mix(vec3(0.85, 0.72, 0.52), vec3(0.62, 0.34, 0.18), stripes);
        color = mix(color, vec3(0.96, 0.92, 0.84), smoothstep(0.6, 0.8, flow));
        float storm = 1.0 - smoothstep(0.0, 0.16, length(vec2(turned.x - 0.5, (turned.y + 0.25) * 1.8)));
        color = mix(color, vec3(0.75, 0.28, 0.15), storm * (turned.z < 0.0 ? 1.0 : 0.0));

        float diffuse = smoothstep(-0.1, 0.6, dot(n, sun));
        float shadowT = dot(sun, ringUp) != 0.0 ? -dot(hp, ringUp) / dot(sun, ringUp) : -1.0;
        float shade = shadowT > 0.0 ? 1.0 - plRings(length(hp + sun * shadowT)) * 0.7 : 1.0;
        float rim = pow(1.0 - max(dot(n, -dir), 0.0), 3.0);
        color = color * (0.04 + diffuse * shade) + vec3(0.45, 0.6, 0.9) * rim * diffuse * 0.6;
        body = vec4(color, 1.0);
    } else {
        // thin atmosphere glow for rays that just miss
        float along = dot(origin, dir);
        float miss = sqrt(max(dot(origin, origin) - along * along, 0.0));
        float glow = exp(-max(miss - radius, 0.0) * 28.0) * 0.6;
        body = vec4(vec3(0.5, 0.65, 1.0) * (0.3 + 0.7 * max(dot(normalize(origin - dir * along), sun), 0.0)), glow);
    }

    vec3 color = ring.rgb * ring.a + body.rgb * body.a * (1.0 - ring.a);
    float alpha = ring.a + body.a * (1.0 - ring.a);
    return vec4(color / max(alpha, 0.0001), alpha);
}
