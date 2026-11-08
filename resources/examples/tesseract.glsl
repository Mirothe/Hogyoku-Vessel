// a four dimensional cube turning through itself. its 16 corners are spun in 4D, squashed down into 3D
// with a perspective along the fourth axis, and every edge is drawn as a glowing line by finding how close
// the view ray passes to it. edges deeper in the fourth dimension go magenta, the near ones cyan
// origin is where the ray enters the item's box (-1 to 1 on every axis), return alpha 0 for empty space

mat2 tsTurn(float a) {
    float c = cos(a);
    float s = sin(a);
    return mat2(c, -s, s, c);
}

// corner number n (0 to 15) is a 4 digit binary number, each digit picks -1 or 1 on one axis
vec4 tsCorner(float n, float time) {
    vec4 p = mod(floor(n / vec4(1.0, 2.0, 4.0, 8.0)), 2.0) * 2.0 - 1.0;
    p.xw = tsTurn(time * 0.45) * p.xw;
    p.yw = tsTurn(time * 0.31) * p.yw;
    p.xz = tsTurn(time * 0.2) * p.xz;
    return vec4(p.xyz * (1.5 / (3.0 - p.w)) * 0.8, p.w);
}

// how close the view ray comes to the edge a-b
float tsGap(vec3 ro, vec3 rd, vec3 a, vec3 b) {
    vec3 ba = b - a;
    vec3 w = ro - a;
    float d = dot(rd, ba);
    float bb = dot(ba, ba);
    float h = clamp((dot(ba, w) - d * dot(rd, w)) / max(bb - d * d, 1e-5), 0.0, 1.0);
    vec3 q = a + ba * h;
    float t = max(dot(q - ro, rd), 0.0);
    return length(ro + rd * t - q);
}

vec4 object(vec3 origin, vec3 dir, float time) {
    vec3 glow = vec3(0.0);
    for (int i = 0; i < 16; i++) {
        float n = float(i);
        vec4 a = tsCorner(n, time);
        glow += vec3(1.0) * exp(-tsGap(origin, dir, a.xyz, a.xyz) * 90.0) * 0.9;
        // an edge joins two corners that differ in one digit, only walk it from the one with that digit at 0
        for (int k = 0; k < 4; k++) {
            float place = exp2(float(k));
            if (mod(floor(n / place), 2.0) > 0.5) continue;
            vec4 b = tsCorner(n + place, time);
            float gap = tsGap(origin, dir, a.xyz, b.xyz);
            vec3 tint = mix(vec3(0.2, 0.9, 1.0), vec3(1.0, 0.25, 0.85), (a.w + b.w) * 0.25 + 0.5);
            glow += tint * (smoothstep(0.012, 0.0, gap) * 1.6 + exp(-gap * 55.0) * 0.35);
        }
    }

    vec3 c = 1.0 - exp(-glow * 1.4);
    float alpha = max(c.r, max(c.g, c.b));
    return vec4(c / max(alpha, 1e-3), alpha);
}
