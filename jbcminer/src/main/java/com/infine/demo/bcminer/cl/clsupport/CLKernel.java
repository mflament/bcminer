package com.infine.demo.bcminer.cl.clsupport;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.opencl.CL10.*;
import static org.lwjgl.opencl.CL20.*;

public record CLKernel(long program, long id) {

    public static CLKernel getKernel(long program, String name) {
        int[] errorBuffer = new int[1];
        long id = clCreateKernel(program, name, errorBuffer);
        CLException.check("Error creating kernel " + name, errorBuffer[0]);
        return new CLKernel(program, id);
    }

    public long getWorkGroupSize() {
        return readLongInfo(CL_KERNEL_WORK_GROUP_SIZE);
    }

    public long[] getCompileWorkGroupSize() {
        return readLongArrayInfo(CL_KERNEL_COMPILE_WORK_GROUP_SIZE);
    }

    public long getLocalMemSize() {
        return readLongInfo(CL_KERNEL_LOCAL_MEM_SIZE);
    }

    public long[] getPreferredWorkGroupSizeMultiple() {
        return readLongArrayInfo(CL_KERNEL_PREFERRED_WORK_GROUP_SIZE_MULTIPLE);
    }

    public long[] getPrivateMemSize() {
        return readLongArrayInfo(CL_KERNEL_PRIVATE_MEM_SIZE);
    }

    public long[] getGlobalWorkSize() {
        return readLongArrayInfo(CL_KERNEL_GLOBAL_WORK_SIZE);
    }

    private long readLongInfo(int name) {
        ByteBuffer data = CLUtil.readInfo(
                sizeBuffer -> clGetKernelInfo(id, name, (ByteBuffer) null, sizeBuffer),
                dataBuffer -> clGetKernelInfo(id, name, dataBuffer, null)
        );
        return data.getLong(0);
    }

    private long[] readLongArrayInfo(int name) {
        ByteBuffer data = CLUtil.readInfo(
                sizeBuffer -> clGetKernelInfo(id, name, (ByteBuffer) null, sizeBuffer),
                dataBuffer -> clGetKernelInfo(id, name, dataBuffer, null)
        );
        LongBuffer longBuffer = data.asLongBuffer();
        int count = longBuffer.remaining();
        long[] res = new long[count];
        int index = 0;
        while (longBuffer.hasRemaining())
            res[index] = longBuffer.get();
        return res;
    }
}
