import sha256 from "./sha256.glsl"

// language=glsl
const shader = `#version 300 es

precision highp float;
precision highp int;

${sha256}

layout(location = 0) in vec2 position;

uniform uint uData[20];
uniform uint uMidstate[8];
uniform int  uHMaskOffset;
uniform uint uHMask;
uniform uint uNonce;

flat out uint matchedNonce;

void main()
{
    //gl_InstanceID;
//    uint nonce = uNonce + uint(gl_VertexID);

//    uint[BUFFER_INTS] workBuffer;
//    uint[H_INTS] hash;
//    updateHash(uData, uMidstate, nonce, workBuffer, hash);
//    float x;
//    if (gl_VertexID == 0)
//        gl_Position = vec4(-0.5, 0, 0.0, 0.0);
//    else
//        gl_Position = vec4(0.5, 0, 0.0, 0.0);
//    matchedNonce = uint(gl_VertexID);
    float x = gl_VertexID - 0.5; 
    gl_Position = vec4(x, 0.0, 0.0, 0.0);
    matchedNonce = uint(gl_VertexID);
    
//    if (testHash(hash, uHMaskOffset, uHMask)) {
//        matchedNonce = nonce;
//        x = -0.5;
//    } else {
//        matchedNonce = 0u;
//        x = 0.5;
//    }
//    gl_Position = vec4(x, 0.0, 0.0, 0.0);
}`

export default shader;