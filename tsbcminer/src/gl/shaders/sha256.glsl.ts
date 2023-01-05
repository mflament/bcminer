// language=glsl
const shader = `
#define HEADER_INTS 20
#define H_INTS      8
#define BLOCK_INTS  16
#define BUFFER_INTS 64

uint DEFAULT_H[H_INTS] = uint[H_INTS](0x6A09E667u, 0xBB67AE85u, 0x3C6EF372u, 0xA54FF53Au, 0x510E527Fu, 0x9B05688Cu, 0x1F83D9ABu, 0x5BE0CD19u);

uint K[BUFFER_INTS] = uint[BUFFER_INTS](
0x428A2F98u, 0x71374491u, 0xB5C0FBCFu, 0xE9B5DBA5u,
0x3956C25Bu, 0x59F111F1u, 0x923F82A4u, 0xAB1C5ED5u,
0xD807AA98u, 0x12835B01u, 0x243185BEu, 0x550C7DC3u,
0x72BE5D74u, 0x80DEB1FEu, 0x9BDC06A7u, 0xC19BF174u,
0xE49B69C1u, 0xEFBE4786u, 0x0FC19DC6u, 0x240CA1CCu,
0x2DE92C6Fu, 0x4A7484AAu, 0x5CB0A9DCu, 0x76F988DAu,
0x983E5152u, 0xA831C66Du, 0xB00327C8u, 0xBF597FC7u,
0xC6E00BF3u, 0xD5A79147u, 0x06CA6351u, 0x14292967u,
0x27B70A85u, 0x2E1B2138u, 0x4D2C6DFCu, 0x53380D13u,
0x650A7354u, 0x766A0ABBu, 0x81C2C92Eu, 0x92722C85u,
0xA2BFE8A1u, 0xA81A664Bu, 0xC24B8B70u, 0xC76C51A3u,
0xD192E819u, 0xD6990624u, 0xF40E3585u, 0x106AA070u,
0x19A4C116u, 0x1E376C08u, 0x2748774Cu, 0x34B0BCB5u,
0x391C0CB3u, 0x4ED8AA4Au, 0x5B9CCA4Fu, 0x682E6FF3u,
0x748F82EEu, 0x78A5636Fu, 0x84C87814u, 0x8CC70208u,
0x90BEFFFAu, 0xA4506CEBu, 0xBEF9A3F7u, 0xC67178F2u
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
}`

export default shader;