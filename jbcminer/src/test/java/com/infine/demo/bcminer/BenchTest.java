package com.infine.demo.bcminer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("resource")
class BenchTest {
    @Test
    void benchAll() throws InterruptedException {
        TestMiner miner = new TestMiner();
        Bench.start(() -> miner, -1);
        assertEquals(Integer.toUnsignedLong(TestHeader.EXPECTED_NONCE) + 1, miner.total);
    }

    @Test
    void bench1() throws InterruptedException {
        TestMiner miner = new TestMiner();
        Bench.start(() -> miner, 1);
        assertEquals(1, miner.total);
    }

    @Test
    void benchNoMatch() throws InterruptedException {
        NoMatchTestMiner miner = new NoMatchTestMiner();
        Bench.start(() -> miner, 0);
        assertEquals(0xFFFFFFFFL + 1, miner.total);
    }

    static class TestMiner implements IMiner {
        private long total = 0;
        private final MinerStats stats = new MinerStats();

        @Override
        public MinerStats getStats() {
            return stats.update(0);
        }

        @Override
        public Integer mine(BlockHeader header, int startNonce) {
            int nonce = startNonce;
            do {
                nonce++;
                total++;
            } while (nonce != TestHeader.EXPECTED_NONCE);
            return nonce;
        }
    }

    static class NoMatchTestMiner implements IMiner {
        private long total = 0;
        private final MinerStats stats = new MinerStats();

        @Override
        public MinerStats getStats() {
            return stats.update(0);
        }

        @Override
        public Integer mine(BlockHeader header, int startNonce) {
            int nonce = startNonce;
            do {
                nonce++;
                total++;
            } while (Integer.toUnsignedLong(nonce) != Integer.toUnsignedLong(startNonce));
            return nonce;
        }
    }
}