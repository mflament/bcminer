package com.infine.demo.bcminer.cpp;

import com.infine.demo.bcminer.Bench;
import com.infine.demo.bcminer.BlockHeader;
import com.infine.demo.bcminer.IMiner;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

public class CppMiner implements IMiner {

    private final MinerStats stats = new MinerStats();
    private long totalHashes;

    @Override
    public MinerStats getStats(double elapsedSecs) {
        return stats.update(totalHashes, elapsedSecs);
    }

    @Override
    public Integer mine(BlockHeader header, int startNonce) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer dataBuffer = stack.malloc(13 * Integer.BYTES);
            header.copyData(dataBuffer);
            LongBuffer resultBuffer = stack.mallocLong(1);
            mine(dataBuffer, startNonce, resultBuffer);
            long totalHashes = getTotalHashes();
            System.out.println(totalHashes);
            long result = resultBuffer.get(0);
            if (result < 0)
                return null;
            return (int) result;
        }
    }

    native long getTotalHashes();

    native void mine(ByteBuffer data, int startNonce, LongBuffer result);

    public static void main(String[] args) throws InterruptedException {
        Runtime runtime = Runtime.getRuntime();
        runtime.loadLibrary("cppminer");
        Bench.start(CppMiner::new, -1);
    }
}
