package com.infine.demo.jbcminer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public class Bench {

    public interface IMiner {
        long getTotalHashes();

        Integer mine(int startNonce);
    }

    public static final String HEADER_HEX = "020000000AFFED3FC96851D8C74391C2D9333168FE62165EB228BCED7E000000000000004277B65E3BD527F0CEB5298BDB06B4AACBAE8A4A808C2C8AA414C20F252DB801130DAE516461011A00000000";

    public static final int EXPECTED_NONCE = 0xB89BEB3A;

    public static void start(Function<BlockHeader, IMiner> createMiner, int count) throws InterruptedException {
        BlockHeader header = BlockHeader.parse(HEADER_HEX);
        IMiner miner = createMiner.apply(header);

        int startNonce = count == 0 ? 0 : EXPECTED_NONCE - count;

        CompletableFuture<Integer> future = new CompletableFuture<>();
        long startTime = System.currentTimeMillis();
        new Thread(() -> start(future, miner, startNonce)).start();
        while (!await(future))
            printStats(startTime, miner.getTotalHashes());

        double elapsedSecs = (System.currentTimeMillis() - startTime) / 1000d;
        printStats(startTime, miner.getTotalHashes());
        Integer nonce = get(future);
        if (nonce == null)
            System.out.printf("Nonce not found (%.2f s)%n", elapsedSecs);
        else
            System.out.printf("Matched nonce %s (%.2f s)%n", Utils.print(new int[]{nonce}), elapsedSecs);
    }

    private static void start(CompletableFuture<Integer> future, IMiner miner, int startNonce) {
        try {
            Integer matched = miner.mine(startNonce);
            future.complete(matched);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
    }

    private static Integer get(Future<Integer> future) throws InterruptedException {
        try {
            return future.get();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean await(Future<Integer> future) throws InterruptedException {
        try {
            future.get(1, TimeUnit.SECONDS);
            return true;
        } catch (TimeoutException e) {
            return false;
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private static void printStats(long startTime, long total) {
        double elapsedSecs = (System.currentTimeMillis() - startTime) / 1000.0;
        double tmh = total / 1_000_000d;
        double mhps = total / elapsedSecs / 1_000_000d;
        System.out.printf("Hashed %.2f million: %.2f million hash/s%n", tmh, mhps);
    }

}
