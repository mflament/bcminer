package com.infine.demo.jbcminer;

import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class BlockHeaderTest {
    private static final String VALID_HASH = "5c8ad782c007cc563f8db735180b35dab8c983d172b57e2c2701000000000000";
    private static final String INVALID_HASH = "0658c4f948fe15a8bdc1c4fa8233ba41805638b07f3e2dc5c2af2300ffffdd00";

    @Test
    void parse() {
        BlockHeader header = BlockHeader.parse(TestHeader.TEST_HEADER_HEX);
        assertEquals(header.nbits(), TestHeader.EXPECTED_NBITS);
        assertEquals(header.nonce(), TestHeader.EXPECTED_NONCE);
    }

    @Test
    void hashPredicate() {
        BlockHeader header = BlockHeader.parse(TestHeader.TEST_HEADER_HEX);
        Predicate<int[]> predicate = header.hashPredicate();
        assertTrue(predicate.test(Utils.parse(VALID_HASH)));
        assertFalse(predicate.test(Utils.parse(INVALID_HASH)));
    }
}