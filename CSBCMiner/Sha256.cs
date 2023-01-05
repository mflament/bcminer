using System;
using System.Runtime.CompilerServices;

namespace CSBCMiner
{
    class Sha256
    {
        private static readonly uint[] DEFAULT_H = new uint[] { 0x6A09E667, 0xBB67AE85, 0x3C6EF372, 0xA54FF53A, 0x510E527F, 0x9B05688C, 0x1F83D9AB, 0x5BE0CD19 };

        private static readonly uint[] K = new uint[] {
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

        public const int BLOCK_INTS = 16;
        public const int BUFFER_INTS = 64;
        public const int H_INTS = 8;

        private const uint PADDING = 0x80000000;

        public static uint[] CreateMidstate(uint[] header)
        {
            uint[] midstate = new uint[H_INTS];
            Array.Copy(DEFAULT_H, 0, midstate, 0, H_INTS);
            uint[] workBuffer = new uint[BUFFER_INTS];
            Array.Copy(header, workBuffer, BLOCK_INTS);
            ProcessBlock(workBuffer, midstate);
            return midstate;
        }

        public static void Hash(uint[] data, uint[] midstate, uint nonce, uint[] workBuffer, uint[] hash)
        {
            workBuffer[0] = data[16]; // last uint of merkel root
            workBuffer[1] = data[BlockHeader.TIME_OFFSET]; // time
            workBuffer[2] = data[BlockHeader.NBITS_OFFSET]; // nbits
            workBuffer[3] = Swap(nonce); // nbits
            //Padding
            workBuffer[4] = PADDING;
            Array.Clear(workBuffer, 5, BLOCK_INTS - 6);
            // size (in bits) = 80 * 8 =  640
            workBuffer[BLOCK_INTS - 1] = 640;

            Array.Copy(midstate, hash, H_INTS);
            ProcessBlock(workBuffer, hash);

            Array.Copy(hash, workBuffer, H_INTS);
            workBuffer[8] = PADDING;
            Array.Clear(workBuffer, 9, BLOCK_INTS - 10);
            workBuffer[BLOCK_INTS - 1] = 256; // size (16 * 16)

            Array.Copy(DEFAULT_H, hash, H_INTS);
            ProcessBlock(workBuffer, hash);
        }

        public static void ProcessBlock(uint[] workBuffer, uint[] target)
        {
            uint i;
            uint a = target[0];
            uint b = target[1];
            uint c = target[2];
            uint d = target[3];
            uint e = target[4];
            uint f = target[5];
            uint g = target[6];
            uint h = target[7];
            uint T1, T2;

            for (i = 16; i < BUFFER_INTS; i++)
            {
                workBuffer[i] = Gamma1256(workBuffer[i - 2]) + workBuffer[i - 7] + Gamma0256(workBuffer[i - 15]) + workBuffer[i - 16];
            }

            for (i = 0; i < BUFFER_INTS; i++)
            {
                T1 = h + Sigma1256(e) + Ch(e, f, g) + K[i] + workBuffer[i];
                T2 = Sigma0256(a) + Maj(a, b, c);

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

        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        private static uint RotateRight(uint value, int bits)
        {
            return (value >> bits) | (value << (32 - bits));
        }

        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        private static uint ShiftRight(uint value, int bits)
        {
            return (value >> bits);
        }

        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        private static uint Ch(uint x, uint y, uint z)
        {
            return ((x & y) ^ ((~x) & z));
        }

        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        private static uint Maj(uint x, uint y, uint z)
        {
            return ((x & y) ^ (x & z) ^ (y & z));
        }

        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        private static uint Sigma0256(uint x)
        {
            return (RotateRight(x, 2) ^ RotateRight(x, 13) ^ RotateRight(x, 22));
        }

        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        private static uint Sigma1256(uint x)
        {
            return (RotateRight(x, 6) ^ RotateRight(x, 11) ^ RotateRight(x, 25));
        }

        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        private static uint Gamma0256(uint x)
        {
            return (RotateRight(x, 7) ^ RotateRight(x, 18) ^ ShiftRight(x, 3));
        }

        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        private static uint Gamma1256(uint x)
        {
            return (RotateRight(x, 17) ^ RotateRight(x, 19) ^ ShiftRight(x, 10));
        }

        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        private static uint Swap(uint i)
        {
            return ((i >> 24) & 0xff) | // move byte 3 to byte 0
                   ((i << 8) & 0xff0000) | // move byte 1 to byte 2
                   ((i >> 8) & 0xff00) | // move byte 2 to byte 1
                   ((i << 24) & unchecked((uint)0xff000000)); // byte 0 to byte 3
        }

    }
}
