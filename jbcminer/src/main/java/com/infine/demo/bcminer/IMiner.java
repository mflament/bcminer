package com.infine.demo.bcminer;

public interface IMiner extends AutoCloseable {

    MinerStats getStats(double elapsedSecs);

    Integer mine(BlockHeader header, int startNonce);

    record MinerStats(long totalHashes, double hps) {
    }

    @Override
    default void close() {
    }
}
