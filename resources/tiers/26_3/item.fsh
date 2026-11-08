#version 330
#extension GL_ARB_separate_shader_objects : require

#if defined(GLINT) || !defined(OIT_ALPHA_ONLY)
#include <minecraft:globals.glsl>
#else
const float GameTime = 0.0;
const vec2 ScreenSize = vec2(1.0);
#endif
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>

uniform sampler2D Sampler0;
#ifdef GLINT
uniform sampler2D GlintSampler;
#endif

#ifndef OIT_ALPHA_ONLY
layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
#endif
layout(location = 2) in vec4 vertexColor;
#ifndef OIT_ALPHA_ONLY
layout(location = 3) in vec4 lightMapColor;
layout(location = 4) in vec4 overlayColor;
#endif
layout(location = 5) in vec2 texCoord0;
#ifdef GLINT
layout(location = 6) in vec2 texCoordGlint;
#endif
layout(location = 8) in vec3 viewRay;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragOutput;
#endif

//%SKY%

void main() {
    vec4 texel = texture(Sampler0, texCoord0);
    vec4 skyColor;
    if (drawSky(textureLod(Sampler0, texCoord0, 0.0), viewRay, texCoord0 * vec2(textureSize(Sampler0, 0)), GameTime * 1200.0, skyColor)) {
#ifdef OIT_ALPHA_ONLY
        executeAlphaOnlyPhase(gl_FragCoord.z, 1.0);
#else
        fragOutput = skyColor;
#endif
        return;
    }

    vec4 color = texel;
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) discard;
#endif
    color *= vertexColor * ColorModulator;
#ifdef GLINT
    color.a = max(color.a, GlintAlpha);
#endif
#ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
#else
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;
#ifdef GLINT
    vec4 glint = GlintAlpha * texture(GlintSampler, texCoordGlint);
    color.rgb += glint.rgb * glint.rgb;
#endif
#ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
#else
    vec4 fogColor = FogColor;
#endif
    fragOutput = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
#endif
}
