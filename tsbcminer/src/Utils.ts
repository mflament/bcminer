export function checkNull<T>(v: T | null, message = 'value'): T {
  if (v === null) throw new Error(message + ' is null');
  return v;
}

export function timeout(): Promise<void> {
  return new Promise(resolve => setTimeout(resolve));
}

export function printHex(ints: Uint32Array): string {
  let res = '';
  const data = new DataView(ints.buffer);
  for (let i = 0; i < ints.length; i++) {
    for (let j = 3; j >= 0; j--) {
      const byteOffset = i * 4 + j;
      res += data.getUint8(byteOffset).toString(16).padStart(2, '0');
    }
  }
  return res;
}

export function flipEndianness(hash: string): string {
  let result = '';
  for (let i = hash.length - 1; i > 0; i -= 2) {
    result += hash.substring(i - 1, i + 1);
  }
  return result;
}

export function parseHex(hex: string): Uint32Array {
  const res = new Uint32Array(hex.length / 8);
  for (let i = 0; i < res.length; i++) {
    let value = 0;
    for (let j = 0; j < 4; j++) {
      const offset = i * 8 + j * 2;
      const byte = parseInt(hex.substring(offset, offset + 2), 16);
      value |= byte << ((3 - j) * 8);
    }
    res[i] = value;
  }
  return res;
}
