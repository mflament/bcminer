package com.infine.demo.bcminer;

import java.util.Map;
import java.util.function.Function;

public interface IMiner extends AutoCloseable {

    MinerStats getStats(double elapsedSecs);

    Integer mine(BlockHeader header, int startNonce);

    @Override
    default void close() {
    }

    class MinerStats {
        protected long totalHashes;
        protected double hps;

        public long totalHashes() {
            return totalHashes;
        }

        public double hps() {
            return hps;
        }

        @Override
        public String toString() {
            double hashed = totalHashes * 1E-6;
            return String.format("Hashed %.2f million: %.2f million hash/s", hashed, hps * 1E-6);
        }

        public MinerStats update(long total, double elapsed) {
            this.totalHashes = total;
            this.hps = totalHashes / Math.max(elapsed, 1E-8);
            return this;
        }
    }

    interface Factory<M extends IMiner> extends Function<MinerOptions.ParsedOptions, M> {
    }

}
