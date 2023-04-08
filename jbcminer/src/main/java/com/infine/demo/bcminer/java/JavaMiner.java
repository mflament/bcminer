package com.infine.demo.bcminer.java;

import com.infine.demo.bcminer.Bench;
import com.infine.demo.bcminer.BlockHeader;
import com.infine.demo.bcminer.IMiner;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static com.infine.demo.bcminer.java.Sha256.H_INTS;

/**
 * For a given {@link BlockHeader}, iterate over all nonce to find one matching the header target nBits.<br/>
 * Multithread implementation, each threads handle nonce per thread index<br/>
 * Use the midstate optimization by sharing the precomputed midstate across all workers.<br/>
 */
public class JavaMiner implements IMiner {

    private final int concurrency;

    // shared midstate
    private final int[] midstate = new int[H_INTS];

    // shared hash predicate
    private Predicate<int[]> hashPredicate;

    private final AtomicReference<Integer> matchedNonce = new AtomicReference<>(null);

    // number of hash per thread processed so far
    private final int[] threadHashes;

    public JavaMiner(int concurrency) {
        this.concurrency = concurrency;
        threadHashes = new int[concurrency];
    }

    @Override
    public MinerStats getStats(double elapsedSecs) {
        long totalHashes = 0;
        for (int threadHash : threadHashes) {
            totalHashes += threadHash;
        }
        elapsedSecs = Math.max(elapsedSecs, 1E-6);
        return new MinerStats(totalHashes, totalHashes / elapsedSecs);
    }

    @Override
    public Integer mine(BlockHeader header, int startNonce) {
        hashPredicate = header.hashPredicate();
        Sha256.createMidstate(midstate, header);
        ThreadGroup threadGroup = new ThreadGroup("BCMiner");
        Arrays.fill(threadHashes, 0);
        System.out.printf("Starting JavaMiner with %d threads%n", concurrency);
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
        while (matchedNonce.get() == null && nonce < 0xFFFFFFFFL) {
            Sha256.updateHash(hash, data, midstate, workBuffer, (int) nonce);
            if (hashPredicate.test(hash))
                matchedNonce.set((int) nonce);
            threadHashes[threadIndex]++;
            nonce = nonce + concurrency;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        int threads = Runtime.getRuntime().availableProcessors();
        Bench.start(() -> new JavaMiner(threads), 0);
    }

}
