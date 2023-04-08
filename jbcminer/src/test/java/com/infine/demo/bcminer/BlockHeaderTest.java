package com.infine.demo.bcminer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockHeaderTest {

    private static final String TEST_HEADER_HEX = "02000000"  // Block version: 2
            + "b6ff0b1b1680a2862a30ca44d346d9e8910d334beb48ca0c0000000000000000"  // Hash of previous block's header
            + "9d10aa52ee949386ca9385695f04ede270dda20810decd12bc9b048aaab31471" // Merkle root
            + "24d95a54" // [Unix time][unix epoch time]: 1415239972
            + "30c31b18" // Target: 0x1bc330 * 256**(0x18-3) : 404472624
            + "fe9f0864"; // Nonce : 1678286846

    private static final int EXPECTED_VERSION = 2;
    private static final int EXPECTED_TIME = 1415239972;
    private static final int EXPECTED_NBITS = 404472624;
    private static final int EXPECTED_NONCE = 1678286846;

    @Test
    void parse() {
        BlockHeader header = BlockHeader.parse(TEST_HEADER_HEX);
        assertEquals(EXPECTED_VERSION, header.version());
        assertEquals(EXPECTED_TIME, header.time());
        assertEquals(EXPECTED_NBITS, header.nbits());
        assertEquals(EXPECTED_NONCE, header.nonce());
        assertEquals(TEST_HEADER_HEX, header.toString());
    }

}