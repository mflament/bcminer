package com.infine.demo.clbcminer;

import com.infine.demo.clbcminer.clsupport.CLContext;
import com.infine.demo.clbcminer.clsupport.CLDevice;
import com.infine.demo.clbcminer.clsupport.CLException;
import com.infine.demo.clbcminer.clsupport.CLKernel;
import com.infine.demo.clbcminer.clsupport.CLProgram;
import com.infine.demo.clbcminer.clsupport.CLUtils;
import com.infine.demo.jbcminer.Bench;
import com.infine.demo.jbcminer.BlockHeader;
import com.infine.demo.jbcminer.BlockHeader.HashPredicate;
import com.infine.demo.jbcminer.java.Sha256;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

import static com.infine.demo.clbcminer.clsupport.CLException.check;
import static org.lwjgl.opencl.CL10.*;

public class CLMiner implements Bench.IMiner {

    private static final int DEFAULT_GLOBAL_WORK_SIZE = 1000000;

    private final BlockHeader header;
    private final int globalWorkSize;

    private final HashPredicate hashPredicate;

    private final CLContext context;
    private final long kernel;
    private final long queue;

    private long totalHashes = 0;

    private CLMiner(CLDevice device, BlockHeader header, Integer globalWorkSize) {
        this.header = header;
        this.globalWorkSize = globalWorkSize == null ? DEFAULT_GLOBAL_WORK_SIZE : globalWorkSize;

        hashPredicate = header.hashPredicate();

        context = CLContext.create(device);

        CLProgram program = CLProgram.buildProgram(context, loadProgram(), null);
        kernel = CLKernel.getKernel(program.id(), "hash_nonce").id();
        int[] errorBuffer = new int[1];
        queue = clCreateCommandQueue(context.id(), device.id(), 0, errorBuffer);
        CLException.check(errorBuffer[0]);
    }

    @Override
    public long getTotalHashes() {
        return totalHashes;
    }

    @Override
    public Integer mine(long startNonce) {
        // the buffer for global work
        PointerBuffer gwsBuffer = PointerBuffer.allocateDirect(1);
        gwsBuffer.put(globalWorkSize).flip();

        // the buffer used to read the single int result
        IntBuffer resultsBuffer = BufferUtils.createIntBuffer(2);

        int[] midstate = Sha256.createMidstate(header);
        long clBlockData = createBlockDataBuffer(midstate);

        IntBuffer errorBuffer = BufferUtils.createIntBuffer(1);
        long clResult = clCreateBuffer(context.id(), CL_MEM_WRITE_ONLY, 2 * Integer.BYTES, errorBuffer);
        check(errorBuffer.get(0));

        PointerBuffer bufferArg = PointerBuffer.allocateDirect(1);
        clSetKernelArg(kernel, 0, bufferArg.put(clBlockData).flip());
        clSetKernelArg(kernel, 1, bufferArg.put(clResult).flip());

        totalHashes = 0;
        long nonce = startNonce;
        IntBuffer nonceBuffer = BufferUtils.createIntBuffer(1);
        Integer matchedNonce = null;
        while (matchedNonce == null && nonce >= 0) {
            nonceBuffer.put(0, (int) nonce);
            check(clSetKernelArg(kernel, 2, nonceBuffer));

            check(clEnqueueNDRangeKernel(queue, kernel, 1, null, gwsBuffer, null, null, null));
            check(clFlush(queue));
            check(clEnqueueReadBuffer(queue, clResult, true, 0, resultsBuffer, null, null));
            if (resultsBuffer.get(0) != 0)
                matchedNonce = resultsBuffer.get(1);

            nonce += globalWorkSize;
            totalHashes += globalWorkSize;
        }
        return matchedNonce;
    }

    private long createBlockDataBuffer(int[] midstate) {
        ByteBuffer hostBuffer = createHostBlockDataBuffer(midstate);
        long id = clCreateBuffer(context.id(), CL_MEM_READ_ONLY, hostBuffer.capacity(), null);
        check(clEnqueueWriteBuffer(queue, id, true, 0, hostBuffer, null, null));
        return id;
    }

    private ByteBuffer createHostBlockDataBuffer(int[] midstate) {
        ByteBuffer buffer = BufferUtils.createByteBuffer(14 * 4);
        int[] headerData = header.data();
        buffer.putInt(headerData[BlockHeader.TIME_OFFSET - 1]);
        buffer.putInt(headerData[BlockHeader.TIME_OFFSET]);
        buffer.putInt(headerData[BlockHeader.NBITS_OFFSET]);
        buffer.putInt(0); // align : vec3 are in fact vec4...

        for (int i : midstate) buffer.putInt(i);

        buffer.putInt(hashPredicate.hOffset());
        buffer.putInt(hashPredicate.mask());

        return buffer.flip();
    }

    private static String loadProgram() {
        try (InputStream is = CLMiner.class.getResourceAsStream("/miner.cl")) {
            if (is == null) throw new FileNotFoundException("program not found");
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        CLDevice device = CLUtils.selectDevice();
        if (device == null)
            return;
        Bench.start(header -> new CLMiner(device, header, null), null);
    }

}
