package com.infine.demo.bcminer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infine.demo.bcminer.api.BlockChainResponse;
import com.infine.demo.bcminer.java.Sha256;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

/**
 * <a href="https://developer.bitcoin.org/reference/block_chain.html">blockchain header format</a><br/>
 * <a href="https://dev.to/icesofty/understanding-the-concept-of-the-nonce-sha3-256-in-a-blockchain-with-nodejs-205h">nonce explained</a><br/>
 * <a href="https://bitcoin.stackexchange.com/questions/61956/nbit-to-leading-zeros-in-block-hash">nbits clearly explained</a><br/>
 *
 * @param data : int[HEADER_INTS] little endian encoded ints of block header values
 */
public record BlockHeader(int[] data) {

    public static BlockHeader parse(String hex) {
        return new BlockHeader(Utils.parse(hex));
    }

    public static final int HEADER_INTS = 20;

    // index in data
    public static final int VERSION = 0;
    public static final int PREV_HASH = 1; // 8 ints
    public static final int MRKL_ROOT = 9; // 8 ints
    public static final int TIME = 17;
    public static final int NBITS = 18;
    public static final int NONCE = 19;

    public int version() {
        return Utils.flipEndianess(data[VERSION]);
    }

    public int time() {
        return Utils.flipEndianess(data[TIME]);
    }

    public int nonce() {
        return Utils.flipEndianess(data[NONCE]);
    }

    public void nonce(int nonce) {
        data[NONCE] = Utils.flipEndianess(data[NONCE]);;
    }

    public int nbits() {
        return Utils.flipEndianess(data[NBITS]);
    }

    @Override
    public String toString() {
        return Utils.print(data);
    }

    public HashPredicate hashPredicate() {
        int nbitsExp = Byte.toUnsignedInt((byte) data[NBITS]);
        int leadingBytes = 32 - nbitsExp; // expected MSB 0
        int hOffset = 8 - leadingBytes / 4 - 1;
        int mask = 0;
        for (int i = 0; i < leadingBytes % 4; i++)
            mask |= 0xFF << (i * 8);
        return new HashPredicate(hOffset, mask);
    }

    static BlockHeader testHeader() {
        ObjectMapper om = BlockChainResponse.createObjectMapper();
        try (InputStream is = Bench.class.getResourceAsStream("/block_239711.json")) {
            BlockChainResponse bcr = om.readValue(is, BlockChainResponse.class);
            return bcr.createBlockHeader();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
    public void copyData(ByteBuffer dst) {
        int[] headerData = data();
        dst.putInt(headerData[BlockHeader.TIME - 1]);
        dst.putInt(headerData[BlockHeader.TIME]);
        dst.putInt(headerData[BlockHeader.NBITS]);

        int[] midstate = Sha256.createMidstate(this);
        for (int i : midstate) dst.putInt(i);

        HashPredicate hashPredicate = hashPredicate();
        dst.putInt(hashPredicate.hOffset());
        dst.putInt(hashPredicate.mask());

        assert dst.remaining() == 0;
        dst.flip();
    }
}
