package com.infine.demo.bcminer.cuda;

import com.infine.demo.bcminer.Bench;
import com.infine.demo.bcminer.BlockHeader;
import com.infine.demo.bcminer.IMiner;
import org.lwjgl.PointerBuffer;
import org.lwjgl.cuda.CU;
import org.lwjgl.cuda.CUDA;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

import static org.lwjgl.cuda.CU.*;
import static org.lwjgl.cuda.NVRTC.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;

public class CudaMiner implements IMiner {

    private static final String KERNEL_NAME = "mine";

    private static final boolean SHOW_PTX = false;

    private final int groupCount;
    private final int groupSize;
    private final int chunkSize;

    private final Kernel kernel;
    private long ctx;
    private long totalHashes;

    public CudaMiner(int deviceIndex, int groupCount, int groupSize, int chunkSize) {
        this.groupCount = groupCount;
        this.groupSize = groupSize;
        this.chunkSize = chunkSize;
        Device device = createDevice(deviceIndex);
        ctx = createContext(device);
        kernel = createKernel(device);
    }

    @Override
    public MinerStats getStats(double elapsedSecs) {
        return new MinerStats(totalHashes, totalHashes / elapsedSecs);
    }

    @Override
    public Integer mine(BlockHeader header, int startNonce) {
        int chunkNonces = chunkSize * groupSize;
        check(cuCtxSetCurrent(ctx));

        try (MemoryStack stack = stackPush()) {
            PointerBuffer pp = stack.pointers(0);

            ByteBuffer hostDataBuffer = stack.malloc(13 * Integer.BYTES);
            header.copyData(hostDataBuffer);

            check(cuMemAlloc(pp, hostDataBuffer.capacity()));
            long deviceData = pp.get(0);
            check(cuMemcpyHtoD(deviceData, hostDataBuffer));

//            IntBuffer pi = stack.mallocInt(1);
//            check(cuFuncGetAttribute(pi, CU_FUNC_ATTRIBUTE_MAX_THREADS_PER_BLOCK, kernel.function));
//            int maxThreadPerBlock = pi.get(0);
//            System.out.println(maxThreadPerBlock);

            IntBuffer baseNonce = stack.ints(startNonce);
            IntBuffer nonceCount = stack.ints(chunkNonces);
            check(cuMemAlloc(pp, 2 * Integer.BYTES));
            long deviceResult = pp.get(0);

            PointerBuffer params = stack.pointers(memAddress(stack.longs(deviceData)),
                    memAddress(baseNonce),
                    memAddress(nonceCount),
                    memAddress(stack.longs(deviceResult)));

            System.out.printf("Starting cuda miner with group count %d, group size %d, chunk size: %d (chunk nonce: %d)%n", groupCount, groupSize, chunkSize, chunkNonces);

            IntBuffer hostResult = stack.ints(0, 0);
            totalHashes = 0;
            int nonce = startNonce;
            while (totalHashes < 0xFFFFFFFFL) {
                baseNonce.put(0, nonce);
                check(cuLaunchKernel(kernel.function, groupCount, 1, 1, // grid dim
                        groupSize, 1, 1, // block dim
                        groupSize * Integer.BYTES, 0,
                        params, null));
                cuCtxSynchronize();
                // read result
                check(cuMemcpyDtoH(hostResult, deviceResult));
                if (hostResult.get(0) != 0)
                    break;
                totalHashes += chunkNonces;
                nonce += chunkNonces;
            }

            Integer matchedNonce = hostResult.get(0) != 0 ? hostResult.get(1) : null;
            cuMemFree(deviceData);
            cuMemFree(deviceResult);
            return matchedNonce;
        }
    }


    @Override
    public void close() {
        if (ctx != NULL) {
            cuCtxDetach(ctx);
            ctx = NULL;
        }
    }

    private static Device createDevice(int deviceIndex) {
        try (MemoryStack stack = stackPush()) {
            PointerBuffer pp = stack.mallocPointer(1);
            IntBuffer pi = stack.mallocInt(1);
            // initialize
            if (CUDA.isPerThreadDefaultStreamSupported()) {
                Configuration.CUDA_API_PER_THREAD_DEFAULT_STREAM.set(true);
            }
            System.out.format("- Initializing...\n");
            check(cuInit(0));

            check(cuDeviceGetCount(pi));
            if (pi.get(0) == 0) {
                throw new IllegalStateException("Error: no devices supporting CUDA");
            }

            // get first CUDA device
            check(cuDeviceGet(pi, deviceIndex));
            int device = pi.get(0);

            // get device name
            ByteBuffer pb = stack.malloc(100);
            check(cuDeviceGetName(pb, device));
            System.out.format("> Using device 0: %s\n", memASCII(memAddress(pb)));

            // get compute capabilities and the device name
            IntBuffer minor = stack.mallocInt(1);
            check(cuDeviceComputeCapability(pi, minor, device));
            System.out.format("> GPU Device has SM %d.%d compute capability\n", pi.get(0), minor.get(0));

            // get memory size
            check(cuDeviceTotalMem(pp, device));
            System.out.format("  Total amount of global memory:   %d bytes\n", pp.get(0));
            System.out.format("  64-bit Memory Address:           %s\n", (pp.get(0) > 4 * 1024 * 1024 * 1024L) ? "YES" : "NO");
            return new Device(device, pi.get(0), minor.get(0));
        }
    }

    private static long createContext(Device device) {
        try (MemoryStack stack = stackPush()) {
            PointerBuffer pp = stack.mallocPointer(1);
            // create context
            check(cuCtxCreate(pp, 0, device.device()));
            return pp.get(0);
        }
    }

    private static Kernel createKernel(Device device) {
        String source;
        try (InputStream is = CudaMiner.class.getResourceAsStream("/cuda/miner.cu")) {
            if (is == null)
                throw new FileNotFoundException("/cuda/miner.cu");
            source = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        try (MemoryStack stack = stackPush()) {
            IntBuffer major = stack.mallocInt(1);
            IntBuffer minor = stack.mallocInt(1);
            checkNVRTC(nvrtcVersion(major, minor));
            System.out.println("Compiling kernel with NVRTC v" + major.get(0) + "." + minor.get(0));

            PointerBuffer pp = stack.mallocPointer(1);

            checkNVRTC(nvrtcCreateProgram(pp, source, "miner.cu", null, null));
            long program = pp.get(0);

            ByteBuffer option1 = stack.ASCII(String.format("-arch=sm_%d%d", device.major(), device.minor()), true);
            ByteBuffer option2 = stack.ASCII("-rdc=true", true);

            PointerBuffer options = stack.mallocPointer(2);
            options.put(0, MemoryUtil.memAddress(option1));
            options.put(1, MemoryUtil.memAddress(option2));
            int compilationStatus = nvrtcCompileProgram(program, options);
            checkNVRTC(nvrtcGetProgramLogSize(program, pp));
            if (pp.get(0) > 1L) {
                ByteBuffer log = stack.malloc((int) pp.get(0) - 1);
                checkNVRTC(nvrtcGetProgramLog(program, log));
                System.err.println("Compilation log:");
                System.err.println("----------------");
                System.err.println(memASCII(log));
            }
            checkNVRTC(compilationStatus);

            checkNVRTC(nvrtcGetPTXSize(program, pp));
            ByteBuffer PTX = memAlloc((int) pp.get(0));
            checkNVRTC(nvrtcGetPTX(program, PTX));

            if (SHOW_PTX) {
                System.out.println("\nCompiled PTX:");
                System.out.println("-------------");
                System.out.println(memASCII(PTX));
            }

            // load kernel
            check(cuModuleLoadData(pp, PTX));
            long module = pp.get(0);
            check(cuModuleGetFunction(pp, module, KERNEL_NAME));
            long function = pp.get(0);

            return new Kernel(module, function);
        }

    }

    record Device(int device, int major, int minor) {
    }

    record Kernel(long module, long function) {
    }

    private static void check(int err) {
        if (err != CUDA_SUCCESS) {
            String errorName = "?";
            try (MemoryStack stack = stackPush()) {
                PointerBuffer ptr = stack.pointers(1);
                int nerr = CU.cuGetErrorName(err, ptr);
                if (nerr == CUDA_SUCCESS) {
                    errorName = memASCII(ptr.get(0));
                }
            }
            throw new IllegalStateException(String.format("%s (%d)", errorName, err));
        }
    }

    private static void checkNVRTC(int err) {
        if (err != NVRTC_SUCCESS) {
            throw new IllegalStateException(nvrtcGetErrorString(err));
        }
    }


    public static void main(String[] args) throws InterruptedException {
        Bench.start(() -> new CudaMiner(0, 16, 256, 1024 * 1024), 0);
    }

}
