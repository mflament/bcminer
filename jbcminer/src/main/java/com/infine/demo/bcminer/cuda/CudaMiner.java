package com.infine.demo.bcminer.cuda;

import com.infine.demo.bcminer.Bench;
import com.infine.demo.bcminer.BlockHeader;
import com.infine.demo.bcminer.IMiner;
import com.infine.demo.bcminer.MinerOptions;
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

    public static final MinerOptions OPTIONS = new CudaMiner.CudaMinerOptions();

    private static final class CudaMinerOptions extends MinerOptions {

        private final Option<Integer> deviceIndex;
        private final Option<Integer> groupCount;
        private final Option<Integer> groupSize;
        private final Option<Integer> groupNonces;

        public CudaMinerOptions() {
            super("cuda");
            deviceIndex = addInt("device", "CUDA device index", 0);
            groupCount = addInt("gs", "grid size", 64);
            groupSize = addInt("bs", "block size", 64);
            groupNonces = addInt("gn", "nonces per group per pass", 1024 * 1024);
        }

        @Override
        public IMiner createMiner(ParsedOptions options) {
            return new CudaMiner(options.get(deviceIndex), options.get(groupCount), options.get(groupSize), options.get(groupNonces));
        }
    }

    private static final String KERNEL_NAME = "mine";

    private static final boolean SHOW_PTX = false;

    private final Device device;
    private final int gridSize;
    private final int blockSize;
    private final int groupNonces;
    private final MinerStats stats = new MinerStats();

    private final Kernel kernel;
    private long ctx;
    private long totalHashes;


    public CudaMiner(int deviceIndex, int gridSize, int blockSize, int groupNonces) {
        this.gridSize = gridSize;
        this.blockSize = blockSize;
        this.groupNonces = groupNonces;

        device = createDevice(deviceIndex);
        ctx = createContext(device);
        kernel = createKernel(device);
    }

    @Override
    public MinerStats getStats(double elapsedSecs) {
        return stats.update(totalHashes, elapsedSecs);
    }

    @Override
    public Integer mine(BlockHeader header, int startNonce) {
        check(cuCtxSetCurrent(ctx));
        try (MemoryStack stack = stackPush()) {
            PointerBuffer pp = stack.pointers(0);
//            dumpInfo(device.device(), kernel.function());

            ByteBuffer hostDataBuffer = stack.malloc(13 * Integer.BYTES);
            header.copyData(hostDataBuffer);

            check(cuMemAlloc(pp, hostDataBuffer.capacity()));
            long deviceData = pp.get(0);
            check(cuMemcpyHtoD(deviceData, hostDataBuffer));

            int passNonces = gridSize * groupNonces;
            IntBuffer baseNonce = stack.ints(startNonce);
            IntBuffer nonceCount = stack.ints(groupNonces);
            check(cuMemAlloc(pp, 2 * Integer.BYTES));
            long deviceResult = pp.get(0);

            PointerBuffer params = stack.pointers(
                    memAddress(stack.longs(deviceData)),
                    memAddress(baseNonce),
                    memAddress(nonceCount),
                    memAddress(stack.longs(deviceResult))
            );

            System.out.println("Starting cuda miner");
            System.out.printf("%d groups x %d nonces (%d threads per group) = %d nonces per pass%n", gridSize, groupNonces, blockSize, passNonces);

            IntBuffer hostResult = stack.ints(0, 0);
            totalHashes = 0;
            int nonce = startNonce;
            while (totalHashes < 0xFFFFFFFFL) {
                baseNonce.put(0, nonce);
                check(cuLaunchKernel(kernel.function, gridSize, 1, 1, // grid dim
                        blockSize, 1, 1, // block dim
                        blockSize * Integer.BYTES, 0,
                        params, null));
                cuCtxSynchronize();
                // read result
                check(cuMemcpyDtoH(hostResult, deviceResult));
                if (hostResult.get(0) != 0)
                    break;
                totalHashes += passNonces;
                nonce += passNonces;
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

            // get CUDA device from index
            check(cuDeviceGet(pi, deviceIndex));
            int device = pi.get(0);

            // get device name
            ByteBuffer pb = stack.malloc(100);
            check(cuDeviceGetName(pb, device));
            System.out.format("> Using device %d: %s\n", deviceIndex, memASCII(memAddress(pb)));

            // get compute capabilities and the device name
            IntBuffer major = stack.mallocInt(1), minor = stack.mallocInt(1);
            check(cuDeviceComputeCapability(major, minor, device));
            System.out.format("> GPU Device has SM %d.%d compute capability\n", major.get(0), minor.get(0));

            // get memory size
            check(cuDeviceTotalMem(pp, device));
            System.out.format("  Total amount of global memory:   %d bytes\n", pp.get(0));
            System.out.format("  64-bit Memory Address:           %s\n", (pp.get(0) > 4 * 1024 * 1024 * 1024L) ? "YES" : "NO");
            return new Device(device, major.get(0), minor.get(0));
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


    private static void dumpInfo(int device, long function) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer attr = stack.mallocInt(1);
            System.out.println("Device ");
            cuDeviceGetAttribute(attr, CU_DEVICE_ATTRIBUTE_MULTIPROCESSOR_COUNT, device);
            System.out.printf("CU_DEVICE_ATTRIBUTE_MULTIPROCESSOR_COUNT %d%n", attr.get(0));
            cuDeviceGetAttribute(attr, CU_DEVICE_ATTRIBUTE_MAX_BLOCKS_PER_MULTIPROCESSOR, device);
            System.out.printf("CU_DEVICE_ATTRIBUTE_MAX_BLOCKS_PER_MULTIPROCESSOR %d%n", attr.get(0));
            cuDeviceGetAttribute(attr, CU_DEVICE_ATTRIBUTE_MAX_THREADS_PER_BLOCK, device);
            System.out.printf("CU_DEVICE_ATTRIBUTE_MAX_THREADS_PER_BLOCK %d%n", attr.get(0));
            cuDeviceGetAttribute(attr, CU_DEVICE_ATTRIBUTE_MAX_THREADS_PER_MULTIPROCESSOR, device);
            System.out.printf("CU_DEVICE_ATTRIBUTE_MAX_THREADS_PER_MULTIPROCESSOR %d%n", attr.get(0));
            cuDeviceGetAttribute(attr, CU_DEVICE_ATTRIBUTE_MAX_REGISTERS_PER_BLOCK, device);
            System.out.printf("CU_DEVICE_ATTRIBUTE_MAX_REGISTERS_PER_BLOCK %d%n", attr.get(0));
            cuDeviceGetAttribute(attr, CU_DEVICE_ATTRIBUTE_MAX_REGISTERS_PER_MULTIPROCESSOR, device);
            System.out.printf("CU_DEVICE_ATTRIBUTE_MAX_REGISTERS_PER_MULTIPROCESSOR %d%n", attr.get(0));
            cuDeviceGetAttribute(attr, CU_DEVICE_ATTRIBUTE_MAX_SHARED_MEMORY_PER_BLOCK, device);
            System.out.printf("CU_DEVICE_ATTRIBUTE_MAX_SHARED_MEMORY_PER_BLOCK %d%n", attr.get(0));
            cuDeviceGetAttribute(attr, CU_DEVICE_ATTRIBUTE_MAX_SHARED_MEMORY_PER_MULTIPROCESSOR, device);
            System.out.printf("CU_DEVICE_ATTRIBUTE_MAX_SHARED_MEMORY_PER_MULTIPROCESSOR %d%n", attr.get(0));
            System.out.println("Function ");
            cuFuncGetAttribute(attr, CU_FUNC_ATTRIBUTE_MAX_THREADS_PER_BLOCK, function);
            System.out.printf("CU_FUNC_ATTRIBUTE_MAX_THREADS_PER_BLOCK %d%n", attr.get(0));
            cuFuncGetAttribute(attr, CU_FUNC_ATTRIBUTE_CONST_SIZE_BYTES, function);
            System.out.printf("CU_FUNC_ATTRIBUTE_CONST_SIZE_BYTES %d%n", attr.get(0));
            cuFuncGetAttribute(attr, CU_FUNC_ATTRIBUTE_SHARED_SIZE_BYTES, function);
            System.out.printf("CU_FUNC_ATTRIBUTE_SHARED_SIZE_BYTES %d%n", attr.get(0));
            cuFuncGetAttribute(attr, CU_FUNC_ATTRIBUTE_LOCAL_SIZE_BYTES, function);
            System.out.printf("CU_FUNC_ATTRIBUTE_LOCAL_SIZE_BYTES %d%n", attr.get(0));
            cuFuncGetAttribute(attr, CU_FUNC_ATTRIBUTE_NUM_REGS, function);
            System.out.printf("CU_FUNC_ATTRIBUTE_NUM_REGS %d%n", attr.get(0));
        }
    }


    public static void main(String[] args) throws InterruptedException {
        Bench.start(() -> new CudaMiner(0, 48, 64, 1024 * 1024), -1);
    }

}
