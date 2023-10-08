//language=wgsl
export const SHA256 = `

const DEFAULT_H = array<u32, H_INTS>(0x6A09E667u, 0xBB67AE85u, 0x3C6EF372u, 0xA54FF53Au, 0x510E527Fu, 0x9B05688Cu, 0x1F83D9ABu, 0x5BE0CD19u);
const K = array<u32,BUFFER_INTS>(
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

fn rotateRight(value: u32, bits: u32) -> u32 {
    return (value >> bits) | (value << (32 - bits));
}

fn shiftRight(value: u32, bits: u32) -> u32 {
    return (value >> bits);
}

fn ch(x: u32, y: u32, z: u32) -> u32 {
    return ((x & y) ^ ((~x) & z));
}

fn maj(x: u32, y: u32, z: u32) -> u32 {
    return ((x & y) ^ (x & z) ^ (y & z));
}

fn sigma0256(x: u32) -> u32 {
    return (rotateRight(x, 2) ^ rotateRight(x, 13) ^ rotateRight(x, 22));
}

fn sigma1256(x: u32) -> u32 {
    return (rotateRight(x, 6) ^ rotateRight(x, 11) ^ rotateRight(x, 25));
}

fn gamma0256(x: u32) -> u32 {
    return (rotateRight(x, 7) ^ rotateRight(x, 18) ^ shiftRight(x, 3));
}

fn gamma1256(x: u32) -> u32 {
    return (rotateRight(x, 17) ^ rotateRight(x, 19) ^ shiftRight(x, 10));
}

fn swap(i: u32)-> u32 {
    return ((i >> 24) & 0xffu) |// move byte 3 to byte 0
    ((i << 8) & 0xff0000u) |// move byte 1 to byte 2
    ((i >> 8) & 0xff00u) |// move byte 2 to byte 1
    ((i << 24) & 0xff000000u);// byte 0 to byte 3
}

fn processBlock() {
    var i: u32; 
    var j: u32;
    var a = hash[0];
    var b = hash[1];
    var c = hash[2];
    var d = hash[3];
    var e = hash[4];
    var f = hash[5];
    var g = hash[6];
    var h = hash[7];
    var T1: u32; 
    var T2: u32;

    for (i = BLOCK_INTS; i < BUFFER_INTS; i++) {
        workBuffer[i] = gamma1256(workBuffer[i - 2]) + workBuffer[i - 7] + gamma0256(workBuffer[i - 15]) + workBuffer[i - 16];
    }

    for (i = 0; i < BUFFER_INTS; i++) {
        T1 = h + sigma1256(e) + ch(e, f, g) + K[i] + workBuffer[i];
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

    hash[0] += a;
    hash[1] += b;
    hash[2] += c;
    hash[3] += d;
    hash[4] += e;
    hash[5] += f;
    hash[6] += g;
    hash[7] += h;
}
`