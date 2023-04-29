package com.infine.demo.bcminer.cl;

import com.infine.demo.bcminer.TestHeader;
import com.infine.demo.bcminer.cl.clsupport.CLDevice;
import com.infine.demo.bcminer.cl.clsupport.CLUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CLMinerTest {

    @Test
    void mine() {
        CLUtil.selectDevice();
        List<CLDevice> clDevices = CLUtil.listDevices();
        CLDevice clDevice = clDevices.isEmpty() ? null : clDevices.get(0);
        if (clDevice == null)
            throw new AssertionError("No opencl device");
        try (var miner = new CLMiner(clDevice, 16, 256, 1024)) {
            Integer matchedNonce = miner.mine(TestHeader.TEST_HEADER, TestHeader.EXPECTED_NONCE - 10_000_000);
            assertNotNull(matchedNonce);
            assertEquals(TestHeader.EXPECTED_NONCE, matchedNonce);
        }
    }

    public static void main(String[] args) {
        new CLMinerTest().mine();
    }
}