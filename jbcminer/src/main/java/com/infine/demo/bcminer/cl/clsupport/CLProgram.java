package com.infine.demo.bcminer.cl.clsupport;

import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.List;

import static org.lwjgl.opencl.CL10.*;
import static org.lwjgl.opencl.CL12.CL_PROGRAM_KERNEL_NAMES;

public record CLProgram(long context, long id) {

    public static CLProgram buildProgram(CLContext context, String source, @Nullable String compilerOptions) {
        return buildProgram(context.id(), context.devices(), source, compilerOptions);
    }

    public static CLProgram buildProgram(long context, long[] devices, String source, @Nullable String compilerOptions) {
        IntBuffer errorBuffer = BufferUtils.createIntBuffer(1);
        long id = clCreateProgramWithSource(context, source, errorBuffer);
        CLException.check("Error creating program", errorBuffer.get(0));

        PointerBuffer devicesBuffer = PointerBuffer.allocateDirect(devices.length);
        for (long device : devices) {
            devicesBuffer.put(device);
        }
        devicesBuffer.flip();

        if (compilerOptions == null)
            compilerOptions = "";
        int error = clBuildProgram(id, devicesBuffer, compilerOptions, null, 0);
        if (error != CL_SUCCESS) {
            String log = createLog(id, devices);
            throw new IllegalArgumentException("Error building program : " + CLException.ERROR_MESSAGES.get(error) + System.lineSeparator() + log);
        }
        return new CLProgram(context, id);
    }

    public List<String> getKernelNames() {
        String kernelNames = CLUtil.readStringInfo(
                sizeBuffer -> clGetProgramInfo(id, CL_PROGRAM_KERNEL_NAMES, (ByteBuffer) null, sizeBuffer),
                dataBuffer -> clGetProgramInfo(id, CL_PROGRAM_KERNEL_NAMES, dataBuffer, null)
        );
        return Arrays.asList(kernelNames.split(";"));
    }

    private static String createLog(long programId, long[] devices) {
        final StringBuilder sb = new StringBuilder();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int[] buffer = new int[1];
            for (long device : devices) {
                CLException.check(clGetProgramBuildInfo(programId, device, CL_PROGRAM_BUILD_STATUS, buffer, null));
                if (buffer[0] == CL_BUILD_ERROR) {
                    PointerBuffer sizeBuffer = stack.mallocPointer(1);
                    CLException.check(clGetProgramBuildInfo(programId, device, CL_PROGRAM_BUILD_LOG, (ByteBuffer) null, sizeBuffer));
                    ByteBuffer logBuffer = stack.malloc((int) sizeBuffer.get(0));
                    CLException.check(clGetProgramBuildInfo(programId, device, CL_PROGRAM_BUILD_LOG, logBuffer, null));
                    sb.append("Device ").append(device).append(" build error :").append(System.lineSeparator());
                    sb.append(CLUtil.readString(logBuffer));
                }
            }
        }
        return sb.toString();
    }

}
