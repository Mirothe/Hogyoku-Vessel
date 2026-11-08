#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 viewRay;

out vec4 fragColor;

//%SKY%

void main() {
    vec4 texel = texture(Sampler0, texCoord0);
    vec4 skyColor;
    if (drawSky(textureLod(Sampler0, texCoord0, 0.0), viewRay, texCoord0 * vec2(textureSize(Sampler0, 0)), GameTime * 1200.0, skyColor)) {
        fragColor = skyColor;
        return;
    }

    vec4 color = texel * vertexColor * ColorModulator;
    if (color.a < 0.1) discard;
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
