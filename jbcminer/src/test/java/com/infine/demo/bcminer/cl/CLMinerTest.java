package com.infine.demo.bcminer.cl;

import com.infine.demo.bcminer.TestHeader;
import com.infine.demo.bcminer.cl.clsupport.CLDevice;
import com.infine.demo.bcminer.cl.clsupport.CLUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CLMinerTest {

    @Test
    void mine() {
        CLDevice clDevice = CLUtil.selectDevice();
        if (clDevice == null)
            throw new AssertionError("No opencl device");
        try (var miner = new CLMiner(clDevice, 16, 256, 1024)) {
            Integer matchedNonce = miner.mine(TestHeader.TEST_HEADER, TestHeader.EXPECTED_NONCE - 10_000_000);
            assertNotNull(matchedNonce);
            assertEquals(TestHeader.EXPECTED_NONCE, matchedNonce);
        }
    }

}