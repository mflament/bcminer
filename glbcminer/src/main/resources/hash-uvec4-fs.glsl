#version 330

#define HEADER_VEC4 5
#define H_VEC4      2
#define BLOCK_VEC4  4
#define BUFFER_VEC4 16

//uint DEFAULT_H[H_INTS] = uint[H_INTS]( 0x6A09E667u, 0xBB67AE85u, 0x3C6EF372u, 0xA54FF53Au, 0x510E527Fu, 0x9B05688Cu, 0x1F83D9ABu, 0x5BE0CD19u );
uvec4 DEFAULT_H[H_VEC4] = uvec4[H_VEC4](uvec4(0x6A09E667u, 0xBB67AE85u, 0x3C6EF372u, 0xA54FF53Au), uvec4(0x510E527Fu, 0x9B05688Cu, 0x1F83D9ABu, 0x5BE0CD19u));

uvec4 K[16] = uvec4[16](uvec4(0x428A2F98u, 0x71374491u, 0xB5C0FBCFu, 0xE9B5DBA5u),
uvec4(0x3956C25Bu, 0x59F111F1u, 0x923F82A4u, 0xAB1C5ED5u),
uvec4(0xD807AA98u, 0x12835B01u, 0x243185BEu, 0x550C7DC3u),
uvec4(0x72BE5D74u, 0x80DEB1FEu, 0x9BDC06A7u, 0xC19BF174u),
uvec4(0xE49B69C1u, 0xEFBE4786u, 0x0FC19DC6u, 0x240CA1CCu),
uvec4(0x2DE92C6Fu, 0x4A7484AAu, 0x5CB0A9DCu, 0x76F988DAu),
uvec4(0x983E5152u, 0xA831C66Du, 0xB00327C8u, 0xBF597FC7u),
uvec4(0xC6E00BF3u, 0xD5A79147u, 0x06CA6351u, 0x14292967u),
uvec4(0x27B70A85u, 0x2E1B2138u, 0x4D2C6DFCu, 0x53380D13u),
uvec4(0x650A7354u, 0x766A0ABBu, 0x81C2C92Eu, 0x92722C85u),
uvec4(0xA2BFE8A1u, 0xA81A664Bu, 0xC24B8B70u, 0xC76C51A3u),
uvec4(0xD192E819u, 0xD6990624u, 0xF40E3585u, 0x106AA070u),
uvec4(0x19A4C116u, 0x1E376C08u, 0x2748774Cu, 0x34B0BCB5u),
uvec4(0x391C0CB3u, 0x4ED8AA4Au, 0x5B9CCA4Fu, 0x682E6FF3u),
uvec4(0x748F82EEu, 0x78A5636Fu, 0x84C87814u, 0x8CC70208u),
uvec4(0x90BEFFFAu, 0xA4506CEBu, 0xBEF9A3F7u, 0xC67178F2u));

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

void processBlock(inout uvec4[BUFFER_VEC4] wb, inout uvec4[H_VEC4] target)
{
    int i, j;
    uint a = target[0].x;
    uint b = target[0].y;
    uint c = target[0].z;
    uint d = target[0].w;
    uint e = target[1].x;
    uint f = target[1].y;
    uint g = target[1].z;
    uint h = target[1].w;
    uint T1, T2;

    for (i = BLOCK_VEC4; i < BUFFER_VEC4; i++) {
        wb[i].x = gamma1256(wb[i - 1].z) + wb[i - 2].y + gamma0256(wb[i - 4].y) + wb[i - 4].x;
        wb[i].y = gamma1256(wb[i - 1].w) + wb[i - 2].z + gamma0256(wb[i - 4].z) + wb[i - 4].y;
        wb[i].z = gamma1256(wb[i].x) + wb[i - 2].w + gamma0256(wb[i - 4].w) + wb[i - 4].z;
        wb[i].w = gamma1256(wb[i].y) + wb[i - 1].x + gamma0256(wb[i - 3].x) + wb[i - 4].w;
    }

    for (i = 0; i < BUFFER_VEC4; i++) {
        for (j = 0; j < 4; j++) {
            T1 = h + sigma1256(e) + ch(e, f, g) + K[i][j] + wb[i][j];
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
    }

    target[0].x += a;
    target[0].y += b;
    target[0].z += c;
    target[0].w += d;
    target[1].x += e;
    target[1].y += f;
    target[1].z += g;
    target[1].w += h;
}

void hash_block(in uvec4[HEADER_VEC4] data,
in uvec4[H_VEC4] midstate,
in uint nonce,
inout uvec4[BUFFER_VEC4] workBuffer,
inout uvec4[H_VEC4] hash)
{
    workBuffer[0].xyz = data[4].xyz;// last int of merkel root,  time, nbits
    workBuffer[0].w = swap(nonce);// nonce
    // Clear
    for (int i = 1; i < BLOCK_VEC4; i++) workBuffer[i] = uvec4(0u);
    //Padding
    workBuffer[1].x = 0x80000000u;
    // size (in bits) = 80 * 8 =  640
    workBuffer[BLOCK_VEC4 - 1].w = 640u;

    hash[0] = midstate[0];
    hash[1] = midstate[1];
    processBlock(workBuffer, hash);

    workBuffer[0]= hash[0];
    workBuffer[1]= hash[1];
    for (int i = 2; i < BLOCK_VEC4; i++) workBuffer[i] = uvec4(0u);
    workBuffer[2].x = 0x80000000u;// padding
    workBuffer[BLOCK_VEC4 - 1].w = 256u;// size (16 * 16)

    hash[0] = DEFAULT_H[0];
    hash[1] = DEFAULT_H[1];
    processBlock(workBuffer, hash);
}

bool test_hash(in uvec4[H_VEC4] hash, ivec2 hMaskOffset, uint hMask)
{
    uint sum = hash[hMaskOffset.x][hMaskOffset.y] & hMask;

    for (int i = hMaskOffset.y + 1; i < 4; i++) {
        sum |= hash[hMaskOffset.x][i];
    }
    for (int i = hMaskOffset.x + 1; i < H_VEC4; i++) {
        sum |= hash[i].x;
        sum |= hash[i].y;
        sum |= hash[i].z;
        sum |= hash[i].w;
    }
    return sum == 0u;
}

uniform uvec4 uData[5];
uniform uvec4 uMidstate[2];
uniform ivec2 uHMaskOffset;
uniform uint  uHMask;
uniform uint  uResultsSize;
uniform uint  uNonce;

out uvec4 outMatched;

void main()
{
    uvec2 coord = uvec2(gl_FragCoord);

    uint pixelIndex = coord.y * uResultsSize + coord.x;
    uint nonce = uNonce + pixelIndex;

    uvec4[BUFFER_VEC4] workBuffer = uvec4[BUFFER_VEC4](
    uvec4(0), uvec4(0), uvec4(0), uvec4(0), uvec4(0), uvec4(0), uvec4(0), uvec4(0),
    uvec4(0), uvec4(0), uvec4(0), uvec4(0), uvec4(0), uvec4(0), uvec4(0), uvec4(0)
    );
    uvec4[H_VEC4] hash = uvec4[H_VEC4](uvec4(0), uvec4(0));
    hash_block(uData, uMidstate, nonce, workBuffer, hash);
    if (test_hash(hash, uHMaskOffset, uHMask))
    outMatched = uvec4(nonce);
    else
    outMatched = uvec4(0);
}