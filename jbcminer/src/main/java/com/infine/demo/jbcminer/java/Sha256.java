package com.infine.demo.jbcminer.java;

import com.infine.demo.jbcminer.BlockHeader;

import javax.annotation.Nullable;

import java.util.Arrays;

import static com.infine.demo.jbcminer.BlockHeader.HEADER_INTS;

/**
 * Implementation of bitcoin sha-256 with midstate precomputed optimization
 * <a href="https://github.com/aseemgautam/bitcoin-sha256">Original implementation</a>.<p/>
 * <a href="https://crypto.stackexchange.com/questions/1862/how-can-i-calculate-the-sha-256-midstate">Midstate optimization</a>
 */
public class Sha256 {

    /**
     * int count in  H
     */
    public static final int H_INTS = 8;
    public static final int BLOCK_INTS = 16;
    public static final int BUFFER_INTS = 64;

    public static final int[] DEFAULT_H = {0x6A09E667, 0xBB67AE85, 0x3C6EF372, 0xA54FF53A, 0x510E527F, 0x9B05688C, 0x1F83D9AB, 0x5BE0CD19};

    private static final int[] K = {
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
    };

    public static int[] createMidstate(BlockHeader header) {
        int[] midstate = new int[H_INTS];
        System.arraycopy(DEFAULT_H, 0, midstate, 0, H_INTS);
        Sha256.hash(header.data(), midstate);
        return midstate;
    }

    public static void hash(int[] data, @Nullable int[] hash) {
        if (hash == null)
            hash = new int[H_INTS];
        int[] workBuffer = new int[BUFFER_INTS];
        System.arraycopy(data, 0, workBuffer, 0, HEADER_INTS);
        processBlock(workBuffer, hash);
    }

    public static void updateHash(int[] hash, int[] data, int[] midstate, int[] workBuffer, int nonce) {
        workBuffer[0] = data[16]; // last int of merkel root
        workBuffer[1] = data[BlockHeader.TIME_OFFSET]; // time
        workBuffer[2] = data[BlockHeader.NBITS_OFFSET]; // nbits
        workBuffer[3] = swap(nonce); // nonce
        //Padding
        workBuffer[4] = 0x80000000;
        Arrays.fill(workBuffer, 5, BLOCK_INTS - 1, 0);
        // size (in bits) = 80 * 8 =  640
        workBuffer[BLOCK_INTS - 1] = 640;

        System.arraycopy(midstate, 0, hash, 0, H_INTS);
        Sha256.processBlock(workBuffer, hash);

        System.arraycopy(hash, 0, workBuffer, 0, H_INTS);
        workBuffer[8] = 0x80000000; // padding
        Arrays.fill(workBuffer, 9, BLOCK_INTS - 1, 0);
        workBuffer[BLOCK_INTS - 1] = 256; // size (16 * 16)

        System.arraycopy(DEFAULT_H, 0, hash, 0, H_INTS);
        Sha256.processBlock(workBuffer, hash);
    }

    public static void processBlock(int[] workBuffer, int[] target) {
        int i;
        int a = target[0];
        int b = target[1];
        int c = target[2];
        int d = target[3];
        int e = target[4];
        int f = target[5];
        int g = target[6];
        int h = target[7];
        int T1, T2;

        for (i = 16; i < BUFFER_INTS; i++) {
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

        target[0] += a;
        target[1] += b;
        target[2] += c;
        target[3] += d;
        target[4] += e;
        target[5] += f;
        target[6] += g;
        target[7] += h;
    }

    /// <summary>
    /// ROTR^n(value). Circular shift right
    /// </summary>
    public static int rotateRight(int value, int bits) {
        return (value >>> bits) | (value << (32 - bits));
    }

    public static int shiftRight(int value, int bits) {
        return (value >>> bits);
    }

    public static int ch(int x, int y, int z) {
        return ((x & y) ^ ((~x) & z));
    }

    public static int maj(int x, int y, int z) {
        return ((x & y) ^ (x & z) ^ (y & z));
    }

    public static int sigma0256(int x) {
        return (rotateRight(x, 2) ^ rotateRight(x, 13) ^ rotateRight(x, 22));
    }

    public static int sigma1256(int x) {
        return (rotateRight(x, 6) ^ rotateRight(x, 11) ^ rotateRight(x, 25));
    }

    public static int gamma0256(int x) {
        return (rotateRight(x, 7) ^ rotateRight(x, 18) ^ shiftRight(x, 3));
    }

    public static int gamma1256(int x) {
        return (rotateRight(x, 17) ^ rotateRight(x, 19) ^ shiftRight(x, 10));
    }

    private static int swap(int i) {
        return ((i >> 24) & 0xff) | // move byte 3 to byte 0
                ((i << 8) & 0xff0000) | // move byte 1 to byte 2
                ((i >> 8) & 0xff00) | // move byte 2 to byte 1
                ((i << 24) & 0xff000000); // byte 0 to byte 3
    }
}
