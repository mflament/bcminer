// language=glsl
const shader = `
#version 300 es

precision highp float;
precision highp int;
precision highp usampler2D;

uniform usampler2D uResults;
uniform int uReduceFactor;

out uvec4 outMatched;

void main()
{
    ivec2 coord = ivec2(gl_FragCoord);
    ivec2 offset = coord * uReduceFactor;
    uint matchedNonce = 0u;
    for (int y = 0; y < uReduceFactor; y++) {
        for (int x = 0; x < uReduceFactor; x++) {
            matchedNonce |= texelFetch(uResults, offset + ivec2(x, y) , 0).x;
        }
    }
    outMatched = uvec4(matchedNonce);    
}
`
export default shader;