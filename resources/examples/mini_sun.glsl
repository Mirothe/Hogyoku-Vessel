// a small star. its boiling surface is layered noise on a turning sphere, darker towards the edge like a real
// sun, with a few cooler spots drifting across. around it a corona flickers and loops of plasma rise off
// the rim, all from the same noise looked at around the outline
// origin is where the ray enters the item's box (-1 to 1 on every axis), return alpha 0 for empty space

const float MS_RADIUS = 0.5;

float msHash(vec3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float msNoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(msHash(i), msHash(i + vec3(1.0, 0.0, 0.0)), f.x),
                   mix(msHash(i + vec3(0.0, 1.0, 0.0)), msHash(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
               mix(mix(msHash(i + vec3(0.0, 0.0, 1.0)), msHash(i + vec3(1.0, 0.0, 1.0)), f.x),
                   mix(msHash(i + vec3(0.0, 1.0, 1.0)), msHash(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z);
}

float msLayers(vec3 p) {
    float sum = 0.0;
    float weight = 0.5;
    for (int i = 0; i < 5; i++) {
        sum += msNoise(p) * weight;
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        weight *= 0.5;
    }
    return sum;
}

// black, through red and orange, to yellow white
vec3 msHeat(float x) {
    x = clamp(x, 0.0, 1.0);
    return vec3(min(x * 3.0, 1.0), clamp(x * 2.2 - 0.6, 0.0, 1.0), clamp(x * 3.0 - 2.0, 0.0, 1.0) * 0.8 + 0.05 * x);
}

vec4 object(vec3 origin, vec3 dir, float time) {
    float b = dot(origin, dir);
    vec3 nearest = origin - dir * b;
    float miss = length(nearest);
    float spin = time * 0.08;
    mat3 turn = mat3(cos(spin), 0.0, sin(spin), 0.0, 1.0, 0.0, -sin(spin), 0.0, cos(spin));

    if (miss < MS_RADIUS) {
        vec3 p = origin - dir * (b + sqrt(MS_RADIUS * MS_RADIUS - miss * miss));
        vec3 n = normalize(p);
        vec3 q = n * turn;
        // big slow cells with fine boiling on top
        float cells = msLayers(q * 6.0 + vec3(0.0, time * 0.15, 0.0));
        float boil = msNoise(q * 22.0 + vec3(time * 0.6));
        float spots = smoothstep(0.62, 0.7, msLayers(q * 2.0 + vec3(5.0, time * 0.02, 0.0)));
        float limb = pow(max(dot(n, -dir), 0.0), 0.45);
        float heat = (0.55 + cells * 0.45 + boil * 0.12) * limb * (1.0 - spots * 0.6);
        return vec4(1.0 - exp(-msHeat(heat) * 2.2), 1.0);
    }

    // outside the disc: the corona, stronger where the noise around the outline flares up
    float beyond = (miss - MS_RADIUS) / MS_RADIUS;
    vec3 around = normalize(nearest) * turn;
    float flare = msLayers(around * 3.0 + vec3(0.0, 0.0, time * 0.25) - normalize(nearest) * beyond * 2.0);
    float corona = exp(-beyond * 6.0) * 0.9 + exp(-beyond * 2.5) * smoothstep(0.45, 0.85, flare) * 0.9;
    vec3 c = 1.0 - exp(-msHeat(0.55 + corona * 0.4) * corona * 2.0);
    float alpha = clamp(max(c.r, max(c.g, c.b)), 0.0, 1.0);
    return vec4(c / max(alpha, 1e-3), alpha);
}
