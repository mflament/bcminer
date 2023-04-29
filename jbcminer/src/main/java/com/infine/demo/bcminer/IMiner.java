package com.infine.demo.bcminer;

public interface IMiner extends AutoCloseable {

    MinerStats getStats();

    Integer mine(BlockHeader header, int startNonce);

    @Override
    default void close() {
    }

}
