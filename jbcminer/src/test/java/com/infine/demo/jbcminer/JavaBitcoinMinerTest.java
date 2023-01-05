package com.infine.demo.jbcminer;

import com.infine.demo.jbcminer.java.JavaBitcoinMiner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JavaBitcoinMinerTest {

    @Test
    void mine() {
        var miner = new JavaBitcoinMiner(TestHeader.TEST_HEADER, 1);
        Integer matchedNonce = miner.mine(0xB89BEB3A - 10);
        assertNotNull(matchedNonce);
        assertEquals(0xB89BEB3A, matchedNonce);
        assertEquals(11, miner.getTotalHashes());
    }

    @Test
    void concurrentMine() {
        var miner = new JavaBitcoinMiner(TestHeader.TEST_HEADER, 4);
        Integer matchedNonce = miner.mine(0xB89BEB3A - 400);
        assertNotNull(matchedNonce);
        assertEquals(0xB89BEB3A, matchedNonce);
    }
}