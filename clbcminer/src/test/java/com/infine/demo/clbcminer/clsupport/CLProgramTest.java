package com.infine.demo.clbcminer.clsupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static com.infine.demo.clbcminer.clsupport.CLException.check;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.lwjgl.opencl.CL10.*;

class CLProgramTest {

    private static final int N = 1000;

    private CLContext context;
    private long kernel;
    private long queue;

    @BeforeEach
    void setup() {
        CLPlatform platform = CLPlatform.first();
        if (platform == null)
            fail("No open cl platform");
        else {
            CLDevice device = platform.getDevices().get(0);
            System.out.println("Using device " + device);
            context = CLContext.create(device);
            CLProgram program = CLProgram.buildProgram(context, loadProgram(), null);
            queue = clCreateCommandQueue(context.id(), device.id(), 0, (int[]) null);
            kernel = CLKernel.getKernel(program.id(), "sum").id();
        }
    }

    @Test
    void sumKernel() {
        FloatBuffer hostA = randomBuffer();
        FloatBuffer hostB = randomBuffer();
        FloatBuffer hostC = BufferUtils.createFloatBuffer(N);
        long clA = createInputCLBuffer(hostA);
        long clB = createInputCLBuffer(hostB);
        long clC = clCreateBuffer(context.id(), CL_MEM_WRITE_ONLY, N * Float.BYTES, null);

        setArg(0, clA);
        setArg(1, clB);
        setArg(2, clC);
        check(clSetKernelArg(kernel, 3, new int[]{N}));

        PointerBuffer globalWorkSize = PointerBuffer.allocateDirect(1);
        globalWorkSize.put(0, N);

        check(clEnqueueNDRangeKernel(queue, kernel, 1, null, globalWorkSize, null, null, null));

        check(clFinish(queue));

        clEnqueueReadBuffer(queue, clC, true, 0, hostC, null, null);

        for (int i = 0; i < N; i++) {
            float a = hostA.get(i);
            float b = hostB.get(i);
            float c = hostC.get(i);
            assertEquals(a + b, c);
        }
    }

    private void setArg(int index, long clBuffer) {
        PointerBuffer argBuffer = PointerBuffer.allocateDirect(1);
        argBuffer.put(clBuffer).flip();
        check(clSetKernelArg(kernel, index, argBuffer));
    }

    private long createInputCLBuffer(FloatBuffer fb) {
        int bytes = N * Float.BYTES;
        long id = clCreateBuffer(context.id(), CL_MEM_READ_ONLY, bytes, null);
        check(clEnqueueWriteBuffer(queue, id, true, 0, fb, null, null));
        return id;
    }

    private static FloatBuffer randomBuffer() {
        FloatBuffer fb = BufferUtils.createFloatBuffer(N);
        Random random = new Random();
        for (int i = 0; i < N; i++)
            fb.put(random.nextFloat());
        return fb.flip();
    }

    private static String loadProgram() {
        try (InputStream is = CLProgramTest.class.getResourceAsStream("/sum.cl")) {
            if (is == null) throw new FileNotFoundException("program not found");
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            fail("Error reading program", e);
            return "";
        }
    }
}