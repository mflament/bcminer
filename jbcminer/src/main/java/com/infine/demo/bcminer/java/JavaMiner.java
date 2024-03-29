package com.infine.demo.bcminer.java;

import com.infine.demo.bcminer.Bench;
import com.infine.demo.bcminer.BlockHeader;
import com.infine.demo.bcminer.IMiner;
import com.infine.demo.bcminer.MinerOptions;
import com.infine.demo.bcminer.MinerStats;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static com.infine.demo.bcminer.java.Sha256.H_INTS;

/**
 * For a given {@link BlockHeader}, iterate over all nonce to find one matching the header target nBits.<br/>
 * Multithread implementation, each threads handle nonce per thread index<br/>
 * Use the midstate optimization by sharing the precomputed midstate across all workers.<br/>
 */
public class JavaMiner implements IMiner {

    public static final MinerOptions OPTIONS = new JavaMinerOptions();

    private static final class JavaMinerOptions extends MinerOptions {
        public final Option<Integer> concurrency;

        public JavaMinerOptions() {
            super("java");
            concurrency = addInt("threads", "number of mining threads", Runtime.getRuntime().availableProcessors());
        }

        @Override
        public IMiner createMiner(ParsedOptions options) {
            return new JavaMiner(options.get(concurrency));
        }
    }

    private final int concurrency;

    // shared midstate
    private final int[] midstate = new int[H_INTS];

    // shared hash predicate
    private Predicate<int[]> hashPredicate;

    private final AtomicReference<Integer> matchedNonce = new AtomicReference<>(null);

    private final MinerStats stats = new MinerStats();

    public JavaMiner(int concurrency) {
        this.concurrency = concurrency;
    }

    @Override
    public MinerStats getStats() {
        return stats;
    }

    @Override
    public Integer mine(BlockHeader header, int startNonce) {
        hashPredicate = header.hashPredicate();
        Sha256.createMidstate(midstate, header);
        ThreadGroup threadGroup = new ThreadGroup("BCMiner");
        System.out.printf("Starting JavaMiner with %d threads%n", concurrency);
        stats.start();
        for (int i = 0; i < concurrency - 1; i++) {
            final int threadIndex = i;
            new Thread(threadGroup, () -> miningTask(header, startNonce, threadIndex), "BCMiner[" + i + "]").start();
        }
        mine(header, startNonce, concurrency - 1);
        return matchedNonce.get();
    }

    private void miningTask(BlockHeader header, int startNonce, int threadIndex) {
        try {
            mine(header, startNonce, threadIndex);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void mine(BlockHeader header, int startNonce, int threadIndex) {
        int[] data = header.data(), hash = new int[H_INTS], workBuffer = new int[Sha256.BUFFER_INTS];

        long nonce = startNonce + threadIndex;
        int chunk = 0;
        while (matchedNonce.get() == null && nonce < 0xFFFFFFFFL) {
            Sha256.updateHash(hash, data, midstate, workBuffer, (int) nonce);
            if (hashPredicate.test(hash))
                matchedNonce.set((int) nonce);
            chunk++;
            if (chunk == 1000) {
                stats.update(chunk);
                chunk = 0;
            }
            nonce = nonce + concurrency;
        }
        if (chunk > 0)
            stats.update(chunk);
    }

    public static void main(String[] args) throws InterruptedException {
        int threads = Runtime.getRuntime().availableProcessors();
        Bench.start(() -> new JavaMiner(threads), 0);
    }

}
