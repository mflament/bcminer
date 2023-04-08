#define H_INTS 8
#define BLOCK_INTS 16
#define BUFFER_INTS 64

typedef unsigned int uint;

__constant__ uint DEFAULT_H[H_INTS] = { 0x6A09E667, 0xBB67AE85, 0x3C6EF372, 0xA54FF53A, 0x510E527F, 0x9B05688C, 0x1F83D9AB, 0x5BE0CD19 };

__constant__ uint K[64] = {
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

__device__ uint rotateRight(const uint value, const int bits)
{
	return (value >> bits) | (value << (32 - bits));
}

__device__ uint shiftRight(const uint value, const int bits)
{
	return (value >> bits);
}

__device__ uint ch(const uint x, const uint y, const uint z)
{
	return ((x & y) ^ ((~x) & z));
}

__device__ uint maj(const uint x, const uint y, const uint z)
{
	return ((x & y) ^ (x & z) ^ (y & z));
}

__device__ uint sigma0256(const uint x)
{
	return (rotateRight(x, 2) ^ rotateRight(x, 13) ^ rotateRight(x, 22));
}

__device__ uint sigma1256(const uint x)
{
	return (rotateRight(x, 6) ^ rotateRight(x, 11) ^ rotateRight(x, 25));
}

__device__ uint gamma0256(const uint x)
{
	return (rotateRight(x, 7) ^ rotateRight(x, 18) ^ shiftRight(x, 3));
}

__device__ uint gamma1256(const uint x)
{
	return (rotateRight(x, 17) ^ rotateRight(x, 19) ^ shiftRight(x, 10));
}

__device__ uint swap(const uint i)
{
	return ((i >> 24) & 0xff) | // move byte 3 to byte 0
		   ((i << 8) & 0xff0000) | // move byte 1 to byte 2
		   ((i >> 8) & 0xff00) | // move byte 2 to byte 1
		   ((i << 24) & 0xff000000); // byte 0 to byte 3
}

__device__ void clear(uint* array, const int start, const int end)
{
	for (uint i = start; i < end; i++)
		array[i] = 0;
}

__device__ void processBlock(uint* wb, uint* target)
{
	int i;
	uint a = target[0];
	uint b = target[1];
	uint c = target[2];
	uint d = target[3];
	uint e = target[4];
	uint f = target[5];
	uint g = target[6];
	uint h = target[7];
	uint T1, T2;

	for (i = 16; i < BUFFER_INTS; i++) {
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

__device__ bool test_hash(const int hMaskOffset, const uint hMask, uint* hash)
{
	int offset = hMaskOffset;
	uint sum = hash[offset] & hMask;
	for (int i = offset + 1; i < H_INTS; i++) {
		sum |= hash[i];
	}
	return sum == 0;
}

__device__ void hash_block(const uint header[3], const uint midstate[H_INTS], uint nonce, uint* workBuffer, uint* hash)
{
	workBuffer[0] = header[0]; // last int of merkel root
	workBuffer[1] = header[1]; // time
	workBuffer[2] = header[2]; // nbits
	workBuffer[3] = swap(nonce); // nonce
	//Padding
	workBuffer[4] = 0x80000000;
	clear(workBuffer, 5, BLOCK_INTS - 1);
	// size (in bits) = 80 * 8 =  640
	workBuffer[BLOCK_INTS - 1] = 640;

    for (int i = 0; i < H_INTS; i++) hash[i] = midstate[i];
	processBlock(workBuffer, hash);

	for (int i = 0; i < H_INTS; i++) workBuffer[i] = hash[i];
	workBuffer[H_INTS] = 0x80000000; // padding
	clear(workBuffer, 9, BLOCK_INTS - 1);
	workBuffer[BLOCK_INTS - 1] = 256; // size (16 * 16)

	for (int i = 0; i < H_INTS; i++) hash[i] = DEFAULT_H[i];
	processBlock(workBuffer, hash);
}

/**
* Data layout
* [0] last int of merkel root
* [1] time
* [2] nBits
* [3+8] midState
* [11] hMastOffset (int)
* [12] hMask
*/
extern "C" __global__ void mine(uint *globalData, const uint baseNonce, const uint nonceCount, uint *result)
{
    __shared__ uint header[3];
    __shared__ uint midstate[8];
    extern __shared__ int localMatches[];

    int hMaskOffset = (int)globalData[11];
    uint hMask = globalData[12];

    uint workBuffer[BUFFER_INTS];
    uint hash[H_INTS];

    uint groupCount = gridDim.x;
    uint groupNonces = nonceCount / groupCount;
    uint groupId = blockIdx.x;
    uint localId = threadIdx.x;
    uint groupSize = blockDim.x;

    uint startNonce = baseNonce + groupId * groupNonces + localId;

    for (int i = localId; i < 3; i += groupSize) header[i] = globalData[i];
    for (int i = localId; i < 8; i += groupSize) midstate[i] = globalData[3 + i];
    localMatches[localId] = -1;
    __syncthreads();

//     if (groupId == 0 && localId == 0)
//         printf("baseNonce: %u, nonceCount: %u result[0]=%u\n", baseNonce, nonceCount, result[0]);

    uint nonce;
    for (uint i = 0; i < groupNonces; i += groupSize) {
        nonce = startNonce + i;
        hash_block(header, midstate, nonce, workBuffer, hash);
        if (test_hash(hMaskOffset, hMask, hash)) {
            // printf("nonce matched: group id %u local id: %u nonce: %u\n", groupId, localId, nonce);
            localMatches[localId] = localId;
        }
        __syncthreads();
        for(int s = groupSize/2; s > 0; s >>= 1) {
            if (localId < s) {
                localMatches[localId] = localMatches[localId] < 0 ? localMatches[localId + s] : localMatches[localId];
            }
            __syncthreads();
        }
        if (localId == 0 && localMatches[0] >= 0) {
            // printf("matched by local id %d\n", localMatches[0]);
            result[0] = 1;
            result[1] = nonce + localMatches[0];
        }
        __threadfence();
        if (result[0] != 0) {
            // printf("nonce matched: group id %d local id: %d result: %d\n", groupId, localId, result[0]);
            break;
        }
    }
}