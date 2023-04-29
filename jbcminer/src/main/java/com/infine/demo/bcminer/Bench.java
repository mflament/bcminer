package com.infine.demo.bcminer;

import com.infine.demo.bcminer.cl.CLMiner;
import com.infine.demo.bcminer.cuda.CudaMiner;
import com.infine.demo.bcminer.java.JavaMiner;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public class Bench {

    public static void start(Supplier<IMiner> supplier, int count) throws InterruptedException {
        try (IMiner miner = supplier.get()) {
            start(BlockHeader.testHeader(), miner, count);
        }
    }

    public static void start(BlockHeader header, IMiner miner, long count) throws InterruptedException {
        int expectedNonce = header.nonce();
        int startNonce = count < 0 ? -1 : (int) (Integer.toUnsignedLong(expectedNonce) - count);

        CompletableFuture<Integer> future = new CompletableFuture<>();
        new Thread(() -> start(future, miner, header, startNonce)).start();
        while (!await(future)) {
            System.out.println(miner.getStats());
        }
        System.out.println(miner.getStats());
        Integer nonce = get(future);
        if (nonce == null)
            System.out.printf("Nonce not found%n");
        else
            System.out.printf("Matched nonce %s%n", Integer.toUnsignedLong(nonce));
    }

    private static void start(CompletableFuture<Integer> future, IMiner miner, BlockHeader header, int startNonce) {
        try {
            Integer matched = miner.mine(header, startNonce);
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

    public static void main(String[] args) throws InterruptedException {
        List<MinerOptions> minerOptions = List.of(JavaMiner.OPTIONS, CLMiner.OPTIONS, CudaMiner.OPTIONS);
        try (IMiner miner = MinerOptions.parseCommandLine(args, minerOptions)) {
            if (miner == null)
                return;
            start(BlockHeader.testHeader(), miner, -1);
        }
    }

}
