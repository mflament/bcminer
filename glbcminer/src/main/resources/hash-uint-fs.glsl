#version 330

#define HEADER_INTS 20
#define H_INTS      8
#define BLOCK_INTS  16
#define BUFFER_INTS 64

uint DEFAULT_H[H_INTS] = uint[H_INTS](0x6A09E667u, 0xBB67AE85u, 0x3C6EF372u, 0xA54FF53Au, 0x510E527Fu, 0x9B05688Cu, 0x1F83D9ABu, 0x5BE0CD19u);


uint K[BUFFER_INTS] = uint[BUFFER_INTS](
0x428A2F98, 0x71374491, 0xB5C0FBCF, 0xE9B5DBA5,
0x3956C25B, 0x59F111F1, 0x923F82A4, 0xAB1C5ED5,
0xD807AA98, 0x12835B01, 0x243185BE, 0x550C7DC3,
0x72BE5D74, 0x80DEB1FE, 0x9BDC06A7, 0xC19BF174,
0xE49B69C1, 0xEFBE4786, 0x0FC19DC6, 0x240CA1CC,
0x2DE92C6F, 0x4A7484AA, 0x5CB0A9DC, 0x76F988DA,
0x983E5152, 0xA831C66D, 0xB00327C8, 0xBF597FC7,
0xC6E00BF3, 0xD5A79147, 0x06CA6351, 0x14292967,
0x27B70A85, 0x2E1B2138, 0x4D2C6DFC, 0x53380D13,
0x650A7354, 0x766A0ABB, 0x81C2C92E, 0x92722C85,
0xA2BFE8A1, 0xA81A664B, 0xC24B8B70, 0xC76C51A3,
0xD192E819, 0xD6990624, 0xF40E3585, 0x106AA070,
0x19A4C116, 0x1E376C08, 0x2748774C, 0x34B0BCB5,
0x391C0CB3, 0x4ED8AA4A, 0x5B9CCA4F, 0x682E6FF3,
0x748F82EE, 0x78A5636F, 0x84C87814, 0x8CC70208,
0x90BEFFFA, 0xA4506CEB, 0xBEF9A3F7, 0xC67178F2
);

uint rotateRight(const uint value, const int bits)
{
    return (value >> bits) | (value << (32 - bits));
}

uint shiftRight(const uint value, const int bits)
{
    return (value >> bits);
}

uint ch(const uint x, const uint y, const uint z)
{
    return ((x & y) ^ ((~x) & z));
}

uint maj(const uint x, const uint y, const uint z)
{
    return ((x & y) ^ (x & z) ^ (y & z));
}

uint sigma0256(const uint x)
{
    return (rotateRight(x, 2) ^ rotateRight(x, 13) ^ rotateRight(x, 22));
}

uint sigma1256(const uint x)
{
    return (rotateRight(x, 6) ^ rotateRight(x, 11) ^ rotateRight(x, 25));
}

uint gamma0256(const uint x)
{
    return (rotateRight(x, 7) ^ rotateRight(x, 18) ^ shiftRight(x, 3));
}

uint gamma1256(const uint x)
{
    return (rotateRight(x, 17) ^ rotateRight(x, 19) ^ shiftRight(x, 10));
}

uint swap(const uint i)
{
    return ((i >> 24) & 0xffu) |// move byte 3 to byte 0
    ((i << 8) & 0xff0000u) |// move byte 1 to byte 2
    ((i >> 8) & 0xff00u) |// move byte 2 to byte 1
    ((i << 24) & 0xff000000u);// byte 0 to byte 3
}

void processBlock(inout uint[BUFFER_INTS] wb, inout uint[H_INTS] target)
{
    int i, j;
    uint a = target[0];
    uint b = target[1];
    uint c = target[2];
    uint d = target[3];
    uint e = target[4];
    uint f = target[5];
    uint g = target[6];
    uint h = target[7];
    uint T1, T2;

    for (i = BLOCK_INTS; i < BUFFER_INTS; i++) {
        wb[i] = gamma1256(wb[i - 2]) + wb[i - 7] + gamma0256(wb[i - 15]) + wb[i - 16];
    }

    for (i = 0; i < BUFFER_INTS; i++) {
        T1 = h + sigma1256(e) + ch(e, f, g) + K[i] + wb[i];
        T2 = sigma0256(a) + maj(a, b, c);

        h = g;
        g = f;
        f = e;
        e = d + T1;
        d = c;
        c = b;
        b = a;
        a = T1 + T2;
    }

    target[0] += a;
    target[1] += b;
    target[2] += c;
    target[3] += d;
    target[4] += e;
    target[5] += f;
    target[6] += g;
    target[7] += h;
}

void updateHash(in uint[HEADER_INTS] data, in uint[H_INTS] midstate, in uint nonce, inout uint[BUFFER_INTS] workBuffer, inout uint[H_INTS] hash)
{
    workBuffer[0] = data[16];// last int of merkel root
    workBuffer[1] = data[17];// time
    workBuffer[2] = data[18];// nbits
    workBuffer[3] = swap(nonce);// nonce
    //Padding
    workBuffer[4] = 0x80000000u;
    for (int i = 5; i < BLOCK_INTS - 1; i++) workBuffer[i] = 0u;
    // size (in bits) = 80 * 8 =  640
    workBuffer[BLOCK_INTS - 1] = 640u;

    // h = midstate
    for (int i = 0; i < H_INTS; i++) hash[i] = midstate[i];
    processBlock(workBuffer, hash);

    // copy hash in start of workbuffer
    for (int i = 0; i < H_INTS; i++) workBuffer[i] = hash[i];
    workBuffer[8] = 0x80000000u;// padding
    for (int i = 9; i < BLOCK_INTS - 1; i++) workBuffer[i] = 0u;
    workBuffer[BLOCK_INTS - 1] = 256u;// size (16 * 16)

    // h = DEFAULT_H
    for (int i = 0; i < H_INTS; i++) hash[i] = DEFAULT_H[i];
    processBlock(workBuffer, hash);
}

bool testHash(in uint[H_INTS] hash, int hMaskOffset, uint hMask)
{
    uint sum = hash[hMaskOffset] & hMask;
    for (int i = hMaskOffset + 1; i < H_INTS; i++) {
        sum |= hash[i];
    }
    return sum == 0u;
}

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