package com.infine.demo.bcminer.cl;

import com.infine.demo.bcminer.Bench;
import com.infine.demo.bcminer.BlockHeader;
import com.infine.demo.bcminer.IMiner;
import com.infine.demo.bcminer.MinerOptions;
import com.infine.demo.bcminer.cl.clsupport.CLContext;
import com.infine.demo.bcminer.cl.clsupport.CLDevice;
import com.infine.demo.bcminer.cl.clsupport.CLException;
import com.infine.demo.bcminer.cl.clsupport.CLKernel;
import com.infine.demo.bcminer.cl.clsupport.CLProgram;
import com.infine.demo.bcminer.cl.clsupport.CLUtil;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static com.infine.demo.bcminer.cl.clsupport.CLException.check;
import static com.infine.demo.bcminer.cl.clsupport.CLUtil.selectDevice;
import static org.lwjgl.opencl.CL10.*;
import static org.lwjgl.opencl.CL12.CL_MEM_HOST_READ_ONLY;
import static org.lwjgl.opencl.CL12.clReleaseDevice;
import static org.lwjgl.system.MemoryStack.stackPush;

public class CLMiner implements IMiner {

    public static final MinerOptions OPTIONS = new CLMinerOptions();

    private static final class CLMinerOptions extends MinerOptions {

        private final Option<Integer> deviceIndex;
        private final Option<Integer> gridSize;
        private final Option<Integer> blockSize;
        private final Option<Integer> groupNonces;

        public CLMinerOptions() {
            super("cl");
            deviceIndex = addInt("device", "OpenCL device index", -1);
            gridSize = addInt("gs", "Grid size : number work groups", 64);
            blockSize = addInt("bs", "Block size : local work size", 64);
            groupNonces = addInt("gn", "nonce per group", 1024 * 1024);
        }


        @Override
        public IMiner createMiner(ParsedOptions options) {
            List<CLDevice> devices = CLUtil.listDevices();
            int index = options.get(deviceIndex);
            CLDevice device;
            if (index < 0 || index >= devices.size()) {
                index = selectDevice(devices);
                if (index < 0) {
                    System.exit(1);
                    return null;
                }
            }
            device = devices.get(index);
            return new CLMiner(device, options.get(gridSize), options.get(blockSize), options.get(groupNonces));
        }
    }

    public static final class CLMinerStats extends MinerStats {
        double kps; // kernel pass per seconds

        public CLMinerStats update(long total, double elapsed, long kernelCalls) {
            super.update(total, elapsed);
            this.kps = kernelCalls / elapsed;
            return this;
        }

        @Override
        public String toString() {
            return super.toString() + String.format(" kps: %.3f", kps);
        }
    }

    // kernel arguments index
    private static final int GLOBAL_DATA = 0;
    private static final int BASE_NONCE = 1;
    private static final int GROUP_NONCES = 2;
    private static final int RESULT = 3;
    private static final int LOCAL_MATCHES = 4;

    private final int groupCount;
    private final int groupThreads;
    private final int groupNonces;

    private final CLDevice device;
    private final CLContext context;
    private final long program;
    private final long kernel;
    private final long queue;

    private long kernelCalls;
    private long totalHashes;
    private final CLMinerStats stats = new CLMinerStats();

    public CLMiner(CLDevice device, int groupCount, int groupThreads, int groupNonces) {
        this.device = Objects.requireNonNull(device, "device is null");
        if (groupCount <= 0) {
            PointerBuffer pb = BufferUtils.createPointerBuffer(1);
            check(clGetDeviceInfo(device.id(), CL_DEVICE_MAX_COMPUTE_UNITS, pb, null));
            groupCount = (int) pb.get(0);
        }
        this.groupCount = groupCount;
        this.groupThreads = groupThreads;
        this.groupNonces = groupNonces;

        context = CLContext.create(device);
        program = CLProgram.buildProgram(context, loadProgram(), null).id();
        kernel = CLKernel.getKernel(program, "hash_nonces").id();
        int[] errorBuffer = new int[1];
        queue = clCreateCommandQueue(context.id(), device.id(), 0, errorBuffer);
        CLException.check(errorBuffer[0]);
    }

    @Override
    public MinerStats getStats(double elapsedSecs) {
        return stats.update(totalHashes, elapsedSecs, kernelCalls);
    }

    @Override
    public Integer mine(BlockHeader header, int startNonce) {
        try (MemoryStack stack = stackPush()) {
            IntBuffer errorBuffer = stack.mallocInt(1);
            PointerBuffer ptr = stack.mallocPointer(1);
            // prepare block data
            ByteBuffer hostBuffer = stack.malloc(13 * Integer.BYTES);
            header.copyData(hostBuffer);
            long clBlockData = clCreateBuffer(context.id(), CL_MEM_READ_ONLY, hostBuffer.capacity(), null);
            check(clEnqueueWriteBuffer(queue, clBlockData, false, 0, hostBuffer, null, null));

            // create result device buffer
            long clResult = clCreateBuffer(context.id(), CL_MEM_ALLOC_HOST_PTR | CL_MEM_HOST_READ_ONLY | CL_MEM_READ_WRITE,
                    2 * Integer.BYTES, errorBuffer);
            check(errorBuffer.get(0));

            long passNonces = (long) groupCount * groupNonces;
            clSetKernelArg(kernel, GLOBAL_DATA, ptr.put(0, clBlockData));
            clSetKernelArg(kernel, GROUP_NONCES, new int[]{groupNonces}); // nonces per workgroup
            clSetKernelArg(kernel, RESULT, ptr.put(0, clResult)); // result
            long groupSize = groupThreads;
            if (groupSize <= 0) {
                check(clGetKernelWorkGroupInfo(kernel, device.id(), CL_KERNEL_WORK_GROUP_SIZE, ptr, null));
                groupSize = ptr.get(0);
            }
            int work_dim = 1;
            PointerBuffer gws = stack.mallocPointer(work_dim), lws = stack.mallocPointer(work_dim);
            clSetKernelArg(kernel, LOCAL_MATCHES, groupSize * Integer.BYTES); // localMatches
            gws.put(0, (long) groupThreads * groupCount);
            lws.put(0, groupThreads);

//            dumpKernelInfo(device.id(), kernel);

            // the buffer for global and local work size
            System.out.printf("Starting opencl miner using device %s%n", device.name());
            System.out.printf("%d groups x %d nonces (%d threads per group) = %d nonces per pass%n", groupCount, groupNonces, groupThreads, passNonces);

            IntBuffer baseNonceBuffer = stack.mallocInt(1);
            IntBuffer resultsBuffer = stack.mallocInt(2);
            PointerBuffer event = stack.mallocPointer(1);

            int nonce = startNonce;
            totalHashes = 0;
            while (totalHashes < 0xFFFFFFFFL) {
                baseNonceBuffer.put(0, nonce);
                clSetKernelArg(kernel, BASE_NONCE, baseNonceBuffer); // baseNonce
                check(clEnqueueNDRangeKernel(queue, kernel, work_dim, null, gws, lws, null, event));
                // read result
                check(clEnqueueReadBuffer(queue, clResult, true, 0, resultsBuffer, event, null));
                if (resultsBuffer.get(0) != 0) {
                    int matched = resultsBuffer.get(1);
                    totalHashes += Integer.toUnsignedLong(matched) - Integer.toUnsignedLong(nonce);
                    break;
                }
                totalHashes += passNonces;
                kernelCalls++;
                nonce += passNonces;
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
        Bench.start(() -> new CLMiner(device, 48, 64, 1024 * 1024), -1);
    }

}
