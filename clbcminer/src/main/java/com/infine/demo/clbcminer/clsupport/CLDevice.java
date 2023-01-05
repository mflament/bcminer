package com.infine.demo.clbcminer.clsupport;

import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL10;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opencl.CL10.*;
import static org.lwjgl.opencl.CL11.CL_DEVICE_OPENCL_C_VERSION;

public record CLDevice(CLPlatform platform, long id, String name, String vendor, String openCLVersion) {

    public static List<CLDevice> list(CLPlatform platform) {
        int[] devicesCount = new int[1];
        long platformId = platform.id();
        int error = clGetDeviceIDs(platformId, CL_DEVICE_TYPE_ALL, null, devicesCount);
        if (error == CL10.CL_DEVICE_NOT_FOUND)
            return List.of();

        CLException.check(error);
        if (devicesCount[0] == 0)
            return List.of();

        PointerBuffer deviceIds = PointerBuffer.allocateDirect(devicesCount[0]);
        CLException.check(clGetDeviceIDs(platformId, CL_DEVICE_TYPE_ALL, deviceIds, devicesCount));
        List<CLDevice> devices = new ArrayList<>(devicesCount[0]);
        for (int i = 0; i < devicesCount[0]; i++) {
            long deviceId = deviceIds.get(i);
            String name = readDeviceInfo(deviceId, CL_DEVICE_NAME);
            String vendor = readDeviceInfo(deviceId, CL_DEVICE_VENDOR);
            String openclVersion = readDeviceInfo(deviceId, CL_DEVICE_OPENCL_C_VERSION);
            devices.add(new CLDevice(platform, deviceId, name, vendor, openclVersion));
        }
        return devices;
    }

    public int getMaxWorkItemDimensions() {
        ByteBuffer buffer = CLUtil.readInfo(
                sizeBuffer -> clGetDeviceInfo(id, CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS, (ByteBuffer) null, sizeBuffer),
                dataBuffer -> clGetDeviceInfo(id, CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS, dataBuffer, null)
        );
        return buffer.getInt(0);
    }

    public int getAddressBits() {
        ByteBuffer buffer = CLUtil.readInfo(
                sizeBuffer -> clGetDeviceInfo(id, CL_DEVICE_ADDRESS_BITS, (ByteBuffer) null, sizeBuffer),
                dataBuffer -> clGetDeviceInfo(id, CL_DEVICE_ADDRESS_BITS, dataBuffer, null)
        );
        return buffer.getInt(0);
    }

    private static String readDeviceInfo(long deviceId, int infoName) {
        return CLUtil.readStringInfo(
                sizeBuffer -> clGetDeviceInfo(deviceId, infoName, (ByteBuffer) null, sizeBuffer),
                dataBuffer -> clGetDeviceInfo(deviceId, infoName, dataBuffer, null)
        );
    }
}
