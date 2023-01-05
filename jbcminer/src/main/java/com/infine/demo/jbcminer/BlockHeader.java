package com.infine.demo.jbcminer;

import com.infine.demo.jbcminer.java.Sha256;

import java.util.function.Predicate;

/**
 * <a href="https://developer.bitcoin.org/reference/block_chain.html">blockchain header format</a><br/>
 * <a href="https://dev.to/icesofty/understanding-the-concept-of-the-nonce-sha3-256-in-a-blockchain-with-nodejs-205h">nonce explained</a><br/>
 * <a href="https://bitcoin.stackexchange.com/questions/61956/nbit-to-leading-zeros-in-block-hash">nbits clearly explained</a><br/>
 *
 * @param data : header data (must be HEADER_INTS ints length).
 */
public record BlockHeader(int[] data) {

    public static BlockHeader parse(String hex) {
        return new BlockHeader(Utils.parse(hex));
    }

    public static final int HEADER_INTS = 20;

    public static final int TIME_OFFSET = 17;
    public static final int NBITS_OFFSET = 18;
    public static final int NONCE_OFFSET = 19;

    public int nonce() {
        return data[NONCE_OFFSET];
    }

    public void nonce(int nonce) {
        data[NONCE_OFFSET] = nonce;
    }

    public int nbits() {
        return data[NBITS_OFFSET];
    }

    @Override
    public String toString() {
        return Utils.print(data);
    }

    public HashPredicate hashPredicate() {
        int nbitsExp = Byte.toUnsignedInt((byte) nbits());
        int leadingBytes = 32 - nbitsExp; // expected MSB 0
        int hOffset = 8 - leadingBytes / 4 - 1;
        int mask = 0;
        for (int i = 0; i < leadingBytes % 4; i++)
            mask |= 0xFF << (i * 8);
        return new HashPredicate(hOffset, mask);
    }

    /**
     * Predicate to test a hash.
     * Use an int bit mask  for the first int in the h and check for 0 for other int from the start offset.
     *
     * @param hOffset
     * @param mask
     */
    public record HashPredicate(int hOffset, int mask) implements Predicate<int[]> {
        @Override
        public boolean test(int[] hash) {
            int sum = hash[hOffset] & mask;
            for (int i = hOffset + 1; i < Sha256.H_INTS; i++) {
                sum |= hash[i];
            }
            return sum == 0;
        }
    }


}
