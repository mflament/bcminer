package com.infine.demo.bcminer.cl.clsupport;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opencl.CL10.CL_CONTEXT_PLATFORM;
import static org.lwjgl.opencl.CL10.clCreateContext;

public record CLContext(long id, long[] devices) {

    public static CLContext create(CLDevice device) {
        return create(device.platform().id(), device.id());
    }

    /**
     * Single device context
     */
    public static CLContext create(long platform, long... devices) {
        int[] errorBuffer = new int[1];

        PointerBuffer propsBuffer = PointerBuffer.allocateDirect(3);
        propsBuffer.put(CL_CONTEXT_PLATFORM).put(platform).put(0).flip();

        PointerBuffer devicesBuffer = PointerBuffer.allocateDirect(devices.length);
        for (long device : devices) {
            devicesBuffer.put(device);
        }
        devicesBuffer.flip();

        long id = clCreateContext(propsBuffer, devicesBuffer, CLContext::errorCallback, 0, errorBuffer);
        return new CLContext(id, devices);
    }

    private static void errorCallback(long errinfo, long private_info, long cb, long user_data) {
        System.err.println(MemoryUtil.memUTF8(errinfo));
    }
}
