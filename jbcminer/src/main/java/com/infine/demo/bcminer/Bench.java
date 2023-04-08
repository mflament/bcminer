package com.infine.demo.bcminer;

import com.infine.demo.bcminer.cl.CLMiner;
import com.infine.demo.bcminer.cl.clsupport.CLDevice;
import com.infine.demo.bcminer.cl.clsupport.CLPlatform;
import com.infine.demo.bcminer.cuda.CudaMiner;
import com.infine.demo.bcminer.java.JavaMiner;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
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

    public static void start(BlockHeader header, IMiner miner, int count) throws InterruptedException {
        int expectedNonce = header.nonce();
        int startNonce = count == 0 ? 0 : expectedNonce - count;

        CompletableFuture<Integer> future = new CompletableFuture<>();
        long startTime = System.currentTimeMillis();
        new Thread(() -> start(future, miner, header, startNonce)).start();
        while (!await(future))
            printStats(miner.getStats((System.currentTimeMillis() - startTime) * 1E-3));

        double elapsedSecs = (System.currentTimeMillis() - startTime) * 1E-3;
        printStats(miner.getStats(elapsedSecs));
        Integer nonce = get(future);
        if (nonce == null)
            System.out.printf("Nonce not found (%.2f s)%n", elapsedSecs);
        else
            System.out.printf("Matched nonce %s (%.2f s)%n", Integer.toUnsignedLong(nonce), elapsedSecs);
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

    private static void printStats(IMiner.MinerStats stats) {
        double hashed = stats.totalHashes() * 1E-6;
        System.out.printf("Hashed %.2f million: %.2f million hash/s%n", hashed, stats.hps() * 1E-6);
    }

    public static void main(String[] args) throws InterruptedException {
        try (IMiner miner = createMiner(args)) {
            if (miner == null)
                return;
            start(BlockHeader.testHeader(), miner, 0);
        }
    }

    private static IMiner createMiner(String[] args) {
        String minerName = args.length > 0 ? args[0] : "opencl";
        IMiner miner;
        switch (minerName) {
            case "java" -> {
                Map<String, String> options = parseOptions(args, Set.of("threads"));
                int threads = parseInt(options, "threads", Runtime.getRuntime().availableProcessors());
                miner = new JavaMiner(threads);
            }
            case "opencl" -> {
                Map<String, String> options = parseOptions(args, Set.of("platform", "device", "gc", "gs", "cs"));
                int platformIndex = parseInt(options, "platform", 0);
                int deviceIndex = parseInt(options, "device", 0);
                CLDevice clDevice;
                try {
                    clDevice = CLDevice.list(CLPlatform.list().get(platformIndex)).get(deviceIndex);
                } catch (ArrayIndexOutOfBoundsException e) {
                    System.err.println("Opencl device not found");
                    return null;
                }
                int gc = parseInt(options, "gc", 16);
                int gs = parseInt(options, "gs", 256);
                int cs = parseInt(options, "cc", 1024 * 1024);
                miner = new CLMiner(clDevice, gc, gs, cs);
            }
            case "cuda" -> {
                Map<String, String> options = parseOptions(args, Set.of("device", "gc", "gs", "cs"));
                int deviceIndex = parseInt(options, "device", 0);
                int gc = parseInt(options, "gc", 16);
                int gs = parseInt(options, "gs", 256);
                int cs = parseInt(options, "cc", 1024 * 1024);
                miner = new CudaMiner(deviceIndex, gc, gs, cs);
            }
            default -> {
                System.out.println("Invalid miner name");
                miner = null;
            }
        }
        return miner;
    }

    private static int parseInt(Map<String, String> options, @Nullable String name, int defaultValue) {
        String value = options.get(name);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                System.out.printf("Error parsing '%s' value : %s : %s", name, value, e.getMessage());
            }
        }
        return defaultValue;
    }

    private static Map<String, String> parseOptions(String[] args, Set<String> names) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("-")) {
                String opt = args[i].substring(1);
                final String value;
                if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                    value = args[i + 1];
                    i++;
                } else {
                    value = null;
                }
                if (names.contains(opt))
                    values.put(opt, value);
            }
        }
        return values;
    }
}
