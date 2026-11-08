// a sky is one function. it gets the direction
// a pixel looks in and gives back its color.
// change anything, it reruns as you type

vec3 sky(vec3 dir, float time) {
    float up = max(dir.y, 0.0);
    vec3 low = vec3(1.0, 0.55, 0.3);
    vec3 high = vec3(0.08, 0.12, 0.35);
    vec3 color = mix(low, high, pow(up, 0.6));

    // the sun is every pixel looking at sunDir
    float rise = 0.12 + sin(time * 0.3) * 0.08;
    vec3 sunDir = normalize(vec3(0.0, rise, -1.0));
    float sun = max(dot(dir, sunDir), 0.0);
    color += vec3(1.0, 0.9, 0.7) * pow(sun, 600.0);
    color += vec3(1.0, 0.5, 0.2) * pow(sun, 10.0) * 0.5;

    // a dark sea below the horizon
    vec3 sea = vec3(0.05, 0.04, 0.08);
    if (dir.y < 0.0) color = mix(sea, color, exp(dir.y * 30.0));
    return 1.0 - exp(-color * 1.4);
}
