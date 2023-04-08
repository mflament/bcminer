package com.infine.demo.bcminer.cuda;

import com.infine.demo.bcminer.TestHeader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CudaMinerTest {

    @Test
    void mine() {
        try (var miner = new CudaMiner(0, 16, 256, 1024)) {
            Integer matchedNonce = miner.mine(TestHeader.TEST_HEADER, TestHeader.EXPECTED_NONCE - 10_000_000);
            assertNotNull(matchedNonce);
            assertEquals(TestHeader.EXPECTED_NONCE, matchedNonce);
        }
    }

}