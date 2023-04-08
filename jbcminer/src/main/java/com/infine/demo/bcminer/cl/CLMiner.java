package com.infine.demo.bcminer.cl;

import com.infine.demo.bcminer.Bench;
import com.infine.demo.bcminer.BlockHeader;
import com.infine.demo.bcminer.IMiner;
import com.infine.demo.bcminer.cl.clsupport.CLContext;
import com.infine.demo.bcminer.cl.clsupport.CLDevice;
import com.infine.demo.bcminer.cl.clsupport.CLException;
import com.infine.demo.bcminer.cl.clsupport.CLKernel;
import com.infine.demo.bcminer.cl.clsupport.CLProgram;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static com.infine.demo.bcminer.cl.clsupport.CLException.check;
import static com.infine.demo.bcminer.cl.clsupport.CLUtil.selectDevice;
import static org.lwjgl.opencl.CL10.*;
import static org.lwjgl.opencl.CL12.CL_MEM_HOST_READ_ONLY;
import static org.lwjgl.opencl.CL12.clReleaseDevice;
import static org.lwjgl.system.MemoryStack.stackPush;

public class CLMiner implements IMiner {

    // kernel arguments index
    private static final int GLOBAL_DATA = 0;
    private static final int BASE_NONCE = 1;
    private static final int NONCE_COUNT = 2;
    private static final int RESULT = 3;
    private static final int LOCAL_MATCHES = 4;

    private final int groupCount;
    private final int groupSize;
    private final int chunkSize;

    private final CLDevice device;
    private final CLContext context;
    private final long program;
    private final long kernel;
    private final long queue;

    private long totalHashes;

    public CLMiner(CLDevice device, int groupCount, int groupSize, int chunkSize) {
        this.device = Objects.requireNonNull(device, "device is null");
        this.groupCount = groupCount;
        this.groupSize = groupSize;
        this.chunkSize = chunkSize;

        context = CLContext.create(device);
        program = CLProgram.buildProgram(context, loadProgram(), null).id();
        kernel = CLKernel.getKernel(program, "hash_nonces").id();
        int[] errorBuffer = new int[1];
        queue = clCreateCommandQueue(context.id(), device.id(), 0, errorBuffer);
        CLException.check(errorBuffer[0]);
    }

    @Override
    public MinerStats getStats(double elapsedSecs) {
        return new MinerStats(totalHashes, totalHashes / elapsedSecs);
    }

    @Override
    public Integer mine(BlockHeader header, int startNonce) {
        int chunkNonces = chunkSize * groupSize;

        try (MemoryStack stack = stackPush()) {
            IntBuffer errorBuffer = stack.mallocInt(1);

            // prepare block data
            ByteBuffer hostBuffer = stack.malloc(13 * Integer.BYTES);
            header.copyData(hostBuffer);
            long clBlockData = clCreateBuffer(context.id(), CL_MEM_READ_ONLY, hostBuffer.capacity(), null);
            check(clEnqueueWriteBuffer(queue, clBlockData, false, 0, hostBuffer, null, null));

            // create result device buffer
            long clResult = clCreateBuffer(context.id(), CL_MEM_ALLOC_HOST_PTR | CL_MEM_HOST_READ_ONLY | CL_MEM_READ_WRITE,
                    2 * Integer.BYTES, errorBuffer);
            check(errorBuffer.get(0));

            PointerBuffer ptr = stack.mallocPointer(1);
            clSetKernelArg(kernel, GLOBAL_DATA, ptr.put(0, clBlockData));
            clSetKernelArg(kernel, NONCE_COUNT, new int[]{chunkNonces}); // nonceCount
            clSetKernelArg(kernel, RESULT, ptr.put(0, clResult)); // result
            clSetKernelArg(kernel, LOCAL_MATCHES, groupSize * (long) Integer.BYTES); // localMatches

//        dumpKernelInfo(device.id(), kernel);

            // the buffer for global and local work size
            PointerBuffer gws = stack.mallocPointer(1), lws = stack.mallocPointer(1);
            lws.put(0, groupSize);
            gws.put(0, groupSize * (long) groupCount);
            System.out.printf("Starting opencl miner with group count %d, group size %d, chunk size: %d (chunk nonce: %d)%n", groupCount, groupSize, chunkSize, chunkNonces);

            IntBuffer baseNonceBuffer = stack.mallocInt(1);
            IntBuffer resultsBuffer = stack.mallocInt(2);
            PointerBuffer event = stack.mallocPointer(1);

            int nonce = startNonce;
            totalHashes = 0;
            while (totalHashes < 0xFFFFFFFFL) {
                baseNonceBuffer.put(0, nonce);
                clSetKernelArg(kernel, BASE_NONCE, baseNonceBuffer); // baseNonce
                check(clEnqueueNDRangeKernel(queue, kernel, 1, null, gws, lws, null, event));
                // read result
                check(clEnqueueReadBuffer(queue, clResult, true, 0, resultsBuffer, event, null));
                if (resultsBuffer.get(0) != 0)
                    break;
                totalHashes += chunkNonces;
                nonce += chunkNonces;
            }

            clReleaseMemObject(clResult);
            clReleaseMemObject(clBlockData);

            if (resultsBuffer.get(0) != 0)
                return resultsBuffer.get(1);
        }

        return null;
    }

    @Override
    public void close() {
        clReleaseKernel(kernel);
        clReleaseProgram(program);
        clReleaseCommandQueue(queue);
        clReleaseContext(context.id());
        clReleaseDevice(device.id());
    }

    private static String loadProgram() {
        try (InputStream is = CLMiner.class.getResourceAsStream("/cl/miner.cl")) {
            if (is == null) throw new FileNotFoundException("program not found");
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        CLDevice device = selectDevice();
        if (device == null)
            return;
        Bench.start(() -> new CLMiner(device, 16, 256, 1024 * 1024), 0);
    }

}
