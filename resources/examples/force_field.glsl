// a wall of energy made of hexagons. every few seconds something hits it and a ripple runs out from the impact,
// the cells pulse on their own, and the edges glow harder because you're looking through more of the shield there
#pragma vessel shape panel

// uv runs from 0 to 1 across each face, pos is the same point in the -1 to 1 box, normal points out, view is where you look

float ffHash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

// how far a point is from the middle of a hexagon, 0.5 right on its edge
float ffHexDistance(vec2 p) {
    p = abs(p);
    return max(dot(p, normalize(vec2(1.0, 1.7320508))), p.x);
}

vec4 surface(vec2 uv, vec3 pos, vec3 normal, vec3 view, float time) {
    vec2 p = uv * 6.0;
    vec2 size = vec2(1.0, 1.7320508);
    vec2 a = mod(p, size) - size * 0.5;
    vec2 b = mod(p - size * 0.5, size) - size * 0.5;
    vec2 local = dot(a, a) < dot(b, b) ? a : b;
    vec2 id = p - local;

    float toEdge = 0.5 - ffHexDistance(local);
    float line = 1.0 - smoothstep(0.0, 0.05, toEdge);
    float pulse = 0.5 + 0.5 * sin(time * 2.0 + ffHash(id) * 30.0);

    float beat = floor(time * 0.45);
    float since = fract(time * 0.45);
    vec2 impact = vec2(ffHash(vec2(beat, 1.0)), ffHash(vec2(beat, 2.0)));
    float wave = exp(-abs(length(uv - impact) - since * 1.3) * 22.0) * (1.0 - since);
    float flash = exp(-length(uv - impact) * 9.0) * exp(-since * 6.0);

    float rim = pow(1.0 - abs(dot(normal, view)), 2.0);
    vec3 blue = vec3(0.3, 0.62, 1.0);
    vec3 color = blue * (line * (0.45 + 0.55 * pulse) + wave * 1.8 + rim * 0.9 + flash * 2.0) + vec3(0.8, 0.9, 1.0) * wave * line;
    float alpha = clamp(0.06 + line * 0.45 * (0.5 + pulse * 0.5) + wave * 0.7 + rim * 0.55 + flash, 0.0, 1.0);
    return vec4(clamp(color, 0.0, 1.0), alpha);
}
