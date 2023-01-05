// language=glsl
const shader = `#version 300 es

precision highp int;

flat in uint matchedNonce;

out uvec4 outMatched;

void main()
{
    //outMatched = uvec4(matchedNonce);
    outMatched = uvec4(42u);
}
`

export default shader;