#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float GameTime;
uniform vec2 ScreenSize;

in float vertexDistance;
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
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
