import {parseHex} from "./Utils";

export const H_INTS = 8;
export const BLOCK_INTS = 16;
export const BUFFER_INTS = 64;
export const HEADER_INTS = 20;

export class BlockData {
  static readonly TIME_OFFSET = 17;
  static readonly NBITS_OFFSET = 18;
  public static readonly NONCE_OFFSET = 19;

  readonly data: Uint32Array;
  readonly hMaskOffset: number;
  readonly hMask: number;
  readonly midstate: Uint32Array;

  constructor(data: Uint32Array | string) {
    if (typeof data === 'string') data = parseHex(data);
    this.data = data;
    const nbitsExp = data[BlockData.NBITS_OFFSET] & 0xff;
    const leadingBytes = 32 - nbitsExp; // expected MSB 0
    this.hMaskOffset = 8 - Math.floor(leadingBytes / 4) - 1;
    let mask = 0;
    for (let i = 0; i < leadingBytes % 4; i++) mask |= 0xff << (i * 8);
    this.hMask = mask;
    this.midstate = createMidstate(data);
  }

  get nonce(): number {
    return this.data[BlockData.NONCE_OFFSET];
  }

  set nonce(n: number) {
    this.data[BlockData.NONCE_OFFSET] = n;
  }

  testHash(hash: Uint32Array): boolean {
    const { hMaskOffset, hMask } = this;
    let sum = hash[hMaskOffset] & hMask;
    for (let i = hMaskOffset + 1; i < H_INTS; i++) {
      sum |= hash[i];
    }
    return sum == 0;
  }
}

const DEFAULT_H: number[] = [
  0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19
];

const K: number[] = [
  0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5, 0xd807aa98,
  0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174, 0xe49b69c1, 0xefbe4786,
  0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da, 0x983e5152, 0xa831c66d, 0xb00327c8,
  0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967, 0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
  0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85, 0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819,
  0xd6990624, 0xf40e3585, 0x106aa070, 0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a,
  0x5b9cca4f, 0x682e6ff3, 0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
];

function createMidstate(data: Uint32Array): Uint32Array {
  const midstate = newHashBuffer();
  midstate.set(DEFAULT_H, 0);
  const workBuffer = newWorkBuffer();
  workBuffer.set(data, 0);
  processBlock(workBuffer, midstate);
  return midstate;
}

export function hash(blockData: BlockData, nonce?: number): Uint32Array {
  const hash = newHashBuffer();
  nonceHasher(blockData)(nonce !== undefined ? nonce : blockData.nonce, hash);
  return hash;
}

export function nonceHasher(blockData: BlockData): (nonce: number, hash: Uint32Array) => void {
  const workBuffer = newWorkBuffer();
  const { data, midstate } = blockData;
  return (nonce, hash) => {
    workBuffer[0] = data[16]; // last int of merkel root
    workBuffer[1] = data[BlockData.TIME_OFFSET]; // time
    workBuffer[2] = data[BlockData.NBITS_OFFSET]; // nbits
    workBuffer[3] = swap(nonce); // nbits
    //Padding
    workBuffer[4] = 0x80000000;
    workBuffer.fill(0, 5, BLOCK_INTS - 1);
    // size (in bits) = 80 * 8 =  640
    workBuffer[BLOCK_INTS - 1] = 640;

    hash.set(midstate, 0);
    processBlock(workBuffer, hash);

    workBuffer.set(hash, 0);
    workBuffer[8] = 0x80000000; // padding
    workBuffer.fill(0, 9, BLOCK_INTS - 1);
    workBuffer[BLOCK_INTS - 1] = 256; // size (16 * 16)

    hash.set(DEFAULT_H, 0);
    processBlock(workBuffer, hash);
  };
}

export function processBlock(workBuffer: Uint32Array, target: Uint32Array): void {
  let a = target[0],
      b = target[1],
      c = target[2],
      d = target[3],
      e = target[4],
      f = target[5],
      g = target[6],
      h = target[7], T1, T2;

  for (let i = 16; i < BUFFER_INTS; i++) {
    workBuffer[i] =
      gamma1256(workBuffer[i - 2]) + workBuffer[i - 7] + gamma0256(workBuffer[i - 15]) + workBuffer[i - 16];
  }

  for (let i = 0; i < BUFFER_INTS; i++) {
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

export function newHashBuffer(): Uint32Array {
  return new Uint32Array(H_INTS);
}

/// <summary>
/// ROTR^n(value). Circular shift right
/// </summary>
function rotateRight(value: number, bits: number): number {
  return (value >>> bits) | (value << (32 - bits));
}

function shiftRight(value: number, bits: number): number {
  return value >>> bits;
}

function ch(x: number, y: number, z: number): number {
  return (x & y) ^ (~x & z);
}

function maj(x: number, y: number, z: number): number {
  return (x & y) ^ (x & z) ^ (y & z);
}

function sigma0256(x: number): number {
  return rotateRight(x, 2) ^ rotateRight(x, 13) ^ rotateRight(x, 22);
}

function sigma1256(x: number): number {
  return rotateRight(x, 6) ^ rotateRight(x, 11) ^ rotateRight(x, 25);
}

function gamma0256(x: number): number {
  return rotateRight(x, 7) ^ rotateRight(x, 18) ^ shiftRight(x, 3);
}

function gamma1256(x: number): number {
  return rotateRight(x, 17) ^ rotateRight(x, 19) ^ shiftRight(x, 10);
}

function newWorkBuffer(): Uint32Array {
  return new Uint32Array(BUFFER_INTS);
}

export function swap(i: number): number {
  return (
    ((i >> 24) & 0xff) | // move byte 3 to byte 0
    ((i << 8) & 0xff0000) | // move byte 1 to byte 2
    ((i >> 8) & 0xff00) | // move byte 2 to byte 1
    ((i << 24) & 0xff000000) // byte 0 to byte 3
  );
}
