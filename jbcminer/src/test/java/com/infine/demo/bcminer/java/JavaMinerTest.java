package com.infine.demo.bcminer.java;

import com.infine.demo.bcminer.TestHeader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JavaMinerTest {

    @Test
    void mine() {
        try (var miner = new JavaMiner(1)) {
            Integer matchedNonce = miner.mine(TestHeader.TEST_HEADER, TestHeader.EXPECTED_NONCE - 1000000);
            assertNotNull(matchedNonce);
            assertEquals(TestHeader.EXPECTED_NONCE, matchedNonce);
            assertEquals(1000001, miner.getStats(0).totalHashes());
        }
    }

    @Test
    void concurrentMine() {
        try (var miner = new JavaMiner(4)) {
            Integer matchedNonce = miner.mine(TestHeader.TEST_HEADER, TestHeader.EXPECTED_NONCE - 4 * 1000000);
            assertNotNull(matchedNonce);
            assertEquals(TestHeader.EXPECTED_NONCE, matchedNonce);
        }
    }
}