#version 330
void executeAlphaOnlyPhase(float depth, float alpha) { if (alpha < 0.01) discard; }
vec4 sampleColorForAccumulation(vec4 c) { return c; }
