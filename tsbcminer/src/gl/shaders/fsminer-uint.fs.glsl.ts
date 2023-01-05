import sha256 from "./sha256.glsl";
// language=glsl
const shader = `#version 300 es

precision highp float;
precision highp int;
${sha256}

uniform uint uData[20];
uniform uint uMidstate[8];
uniform int  uHMaskOffset;
uniform uint uHMask;
uniform uint uResultsSize;
uniform uint uNonce;

out uvec4 outMatched;

void main()
{
    uvec2 coord = uvec2(gl_FragCoord);

    uint pixelIndex = coord.y * uResultsSize + coord.x;
    uint nonce = uNonce + pixelIndex;

    uint[BUFFER_INTS] workBuffer;
    uint[H_INTS] hash;

    updateHash(uData, uMidstate, nonce, workBuffer, hash);
    if (testHash(hash, uHMaskOffset, uHMask)) {
        outMatched = uvec4(nonce);
    } else {
        outMatched = uvec4(0);
    }
 }
`;

export default shader;