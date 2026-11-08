#version 150
float fog_distance(vec3 pos, int shape) {
    return shape == 0 ? length(pos) : max(length(pos.xz), abs(pos.y));
}
vec4 linear_fog(vec4 inColor, float vertexDistance, float fogStart, float fogEnd, vec4 fogColor) {
    if (vertexDistance <= fogStart) return inColor;
    float f = vertexDistance < fogEnd ? smoothstep(fogStart, fogEnd, vertexDistance) : 1.0;
    return vec4(mix(inColor.rgb, fogColor.rgb, f * fogColor.a), inColor.a);
}
