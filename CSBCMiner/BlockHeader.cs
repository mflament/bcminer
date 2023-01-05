namespace CSBCMiner
{
    public class BlockHeader
    {
        public const int TIME_OFFSET = 17;
        public const int NBITS_OFFSET = 18;
        public const int NONCE_OFFSET = 19;

        public readonly uint[] data;
      
        private readonly int hOffset;
        private readonly uint hMask;

        public BlockHeader(uint[] _data) { 
            data = _data;

            uint nbits = data[NBITS_OFFSET];
            byte nbitsExp = (byte)nbits;
            int leadingBytes = 32 - nbitsExp; // expected MSB 0
            hOffset = 8 - leadingBytes / 4 - 1; // leading 0 int from end of H

            hMask = 0;
            for (int i = 0; i < leadingBytes % 4; i++)
            {
                hMask |= 0xFFu << (i * 8);
            }
        }

        public uint Nonce {
            get => data[NONCE_OFFSET];
            set => data[NONCE_OFFSET] = value;
        }

        public bool TestHash(uint[] hash)
        {
            uint sum = hash[hOffset] & hMask;
            for (int i = hOffset + 1; i < Sha256.H_INTS; i++)
            {
                sum |= hash[i];
            }
            return sum == 0;
        }
    }

}
