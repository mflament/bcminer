package com.infine.demo.jbcminer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BenchTest {
    @Test
    void benchAll() throws InterruptedException {
        TestMiner miner = new TestMiner();
        Bench.start(blockHeader -> miner, 0);
        assertEquals(Integer.toUnsignedLong(Bench.EXPECTED_NONCE), miner.total);
    }

    @Test
    void bench1() throws InterruptedException {
        TestMiner miner = new TestMiner();
        Bench.start(blockHeader -> miner, 1);
        assertEquals(1, miner.total);
    }

    @Test
    void benchNoMatch() throws InterruptedException {
        NoMatchTestMiner miner = new NoMatchTestMiner();
        Bench.start(blockHeader -> miner, 0);
        assertEquals(0xFFFFFFFFL + 1, miner.total);
    }

    static class TestMiner implements Bench.IMiner {
        private long total = 0;

        @Override
        public long getTotalHashes() {
            return total;
        }

        @Override
        public Integer mine(int startNonce) {
            int nonce = startNonce;
            do {
                nonce++;
                total++;
            } while (nonce != Bench.EXPECTED_NONCE);
            return nonce;
        }
    }

    static class NoMatchTestMiner implements Bench.IMiner {
        private long total = 0;

        @Override
        public long getTotalHashes() {
            return total;
        }

        @Override
        public Integer mine(int startNonce) {
            int nonce = startNonce;
            do {
                nonce++;
                total++;
            } while (Integer.toUnsignedLong(nonce) != Integer.toUnsignedLong(startNonce));
            return nonce;
        }
    }
}