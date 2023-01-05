package com.infine.demo.jbcminer.java;

import com.infine.demo.jbcminer.Bench;
import com.infine.demo.jbcminer.BlockHeader;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static com.infine.demo.jbcminer.java.Sha256.BUFFER_INTS;
import static com.infine.demo.jbcminer.java.Sha256.H_INTS;

/**
 * For a given {@link BlockHeader}, iterate over all nonce to find one matching the header target nBits.<br/>
 * Multithread implementation, each threads handle nonce per thread index<br/>
 * Use the midstate optimization by sharing the precomputed midstate across all workers.<br/>
 */
public class JavaBitcoinMiner implements Bench.IMiner {

    private final BlockHeader header;
    private final int concurrency;

    // shared hash predicate
    private final Predicate<int[]> hashPredicate;

    // shared midstate
    private final int[] midstate;

    private final AtomicReference<Integer> matchedNonce = new AtomicReference<>(null);

    // number of hash per thread processed so far
    private final int[] threadHashes;

    public JavaBitcoinMiner(BlockHeader header, int concurrency) {
        this.header = Objects.requireNonNull(header, "header is null");
        this.concurrency = concurrency;
        this.hashPredicate = header.hashPredicate();
        this.midstate = Sha256.createMidstate(header);
        threadHashes = new int[concurrency];
    }

    @Override
    public long getTotalHashes() {
        long res = 0;
        for (int threadHash : threadHashes) {
            res += threadHash;
        }
        return res;
    }

    @Override
    public Integer mine(int startNonce) {
        ThreadGroup threadGroup = new ThreadGroup("BCMiner");
        Arrays.fill(threadHashes, 0);
        for (int i = 0; i < concurrency - 1; i++) {
            final int threadIndex = i;
            new Thread(threadGroup, () -> miningTask(startNonce, threadIndex), "BCMiner[" + i + "]").start();
        }
        mine(startNonce, concurrency - 1);
        return matchedNonce.get();
    }

    private void miningTask(int startNonce, int threadIndex) {
        try {
            mine(startNonce, threadIndex);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void mine(int startNonce, int threadIndex) {
        int[] data = header.data(), hash = new int[H_INTS], workBuffer = new int[BUFFER_INTS];

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
        Bench.start(header -> new JavaBitcoinMiner(header, threads), 0);
    }

}
