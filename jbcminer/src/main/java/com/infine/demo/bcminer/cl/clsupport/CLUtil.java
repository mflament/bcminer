package com.infine.demo.bcminer.cl.clsupport;

import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;

import javax.annotation.Nullable;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

import static org.lwjgl.opencl.CL10.*;
import static org.lwjgl.opencl.CL10.clGetKernelWorkGroupInfo;
import static org.lwjgl.opencl.CL11.CL_KERNEL_PREFERRED_WORK_GROUP_SIZE_MULTIPLE;
import static org.lwjgl.opencl.CL11.CL_KERNEL_PRIVATE_MEM_SIZE;
import static org.lwjgl.opencl.CL12.CL_KERNEL_GLOBAL_WORK_SIZE;

public final class CLUtil {

    private static final String LS = System.lineSeparator();

    private CLUtil() {
    }

    public static List<CLDevice> listDevices() {
        List<CLDevice> devices = new ArrayList<>();
        for (CLPlatform platform : CLPlatform.list()) {
            List<CLDevice> platformDevices = platform.getDevices();
            devices.addAll(platformDevices);
        }
        return devices;
    }

    @Nullable
    public static CLDevice selectDevice() {
        List<CLDevice> devices = listDevices();
        int index = selectDevice(devices);
        if (index < 0)
           return null;
        return devices.get(index);
    }

    public static int selectDevice(List<CLDevice> devices) {
        if (devices.isEmpty()) {
            System.out.println("No Open CL devices found");
            return -1;
        }
        if (devices.size() == 1)
            return 0;

        Scanner scanner = new Scanner(System.in);
        StringBuffer prompt = new StringBuffer();
        String platform = null;
        for (CLDevice device : devices) {
            String devicePlatform = device.platform().name();
            if (!Objects.equals(platform, devicePlatform)) {
                prompt.append(devicePlatform).append(LS);
                platform = devicePlatform;
            }
            prompt.append(String.format("    %2d : %s (%s / %s)%n", devices.size(), device.name(), device.vendor(), device.openCLVersion()));
            devices.add(device);
        }
        prompt.append("-1 : Exit");
        System.out.println(prompt);
        while (true) {
            String input = scanner.nextLine();
            try {
                int i = Integer.parseInt(input);
                if (i < devices.size())
                    return i;
            } catch (NumberFormatException e) {
                // swallow
            }
            System.out.println("Invalid device index");
        }
    }

    public static void dumpKernelInfo(long device, long kernel) {
        StringBuilder sb = new StringBuilder();
        PointerBuffer sizes = PointerBuffer.allocateDirect(3);
        clGetKernelWorkGroupInfo(kernel, device, CL_KERNEL_GLOBAL_WORK_SIZE, sizes, null);
        sb.append("CL_KERNEL_GLOBAL_WORK_SIZE: ").append(sizes.get(0)).append(", ").append(sizes.get(1)).append(", ").append(sizes.get(2)).append("\n");

        clGetKernelWorkGroupInfo(kernel, device, CL_KERNEL_WORK_GROUP_SIZE, sizes, null);
        sb.append("CL_KERNEL_WORK_GROUP_SIZE: ").append(sizes.get(0)).append("\n");

        clGetKernelWorkGroupInfo(kernel, device, CL_KERNEL_COMPILE_WORK_GROUP_SIZE, sizes, null);
        sb.append("CL_KERNEL_COMPILE_WORK_GROUP_SIZE: ").append(sizes.get(0)).append(", ").append(sizes.get(1)).append(", ").append(sizes.get(2)).append("\n");

        clGetKernelWorkGroupInfo(kernel, device, CL_KERNEL_LOCAL_MEM_SIZE, sizes, null);
        sb.append("CL_KERNEL_LOCAL_MEM_SIZE: ").append(sizes.get(0)).append("\n");

        clGetKernelWorkGroupInfo(kernel, device, CL_KERNEL_PREFERRED_WORK_GROUP_SIZE_MULTIPLE, sizes, null);
        sb.append("CL_KERNEL_PREFERRED_WORK_GROUP_SIZE_MULTIPLE: ").append(sizes.get(0)).append("\n");

        clGetKernelWorkGroupInfo(kernel, device, CL_KERNEL_PRIVATE_MEM_SIZE, sizes, null);
        sb.append("CL_KERNEL_PRIVATE_MEM_SIZE: ").append(sizes.get(0)).append("\n");
        System.out.println(sb);
    }

    /**
     * Read char from buffer, stopping on end of string (0)
     */
    public static String readString(ByteBuffer buffer) {
        StringBuilder sb = new StringBuilder();
        while (buffer.hasRemaining()) {
            byte b = buffer.get();
            if (b == 0) break;
            sb.append((char) b);
        }
        return sb.toString();
    }

    public static String readStringInfo(CLOperation<PointerBuffer> readSize, CLOperation<ByteBuffer> readData) {
        return readString(readInfo(readSize, readData));
    }

    public static ByteBuffer readInfo(CLOperation<PointerBuffer> readSize, CLOperation<ByteBuffer> readData) {
        PointerBuffer sizeBuffer = PointerBuffer.allocateDirect(1);
        CLException.check("Error reading size", readSize.execute(sizeBuffer));
        long size = sizeBuffer.get(0);
        if (size > Integer.MAX_VALUE)
            throw new BufferOverflowException();
        ByteBuffer dataBuffer = BufferUtils.createByteBuffer((int) size);
        CLException.check("Error reading data", readData.execute(dataBuffer));
        return dataBuffer;
    }

    @FunctionalInterface
    public interface CLOperation<T> {
        int execute(T data);
    }

}
