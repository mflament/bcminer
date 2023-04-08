package com.infine.demo.bcminer.cl.clsupport;

import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static org.lwjgl.opencl.CL10.*;
import static org.lwjgl.opencl.CL10.clGetKernelWorkGroupInfo;
import static org.lwjgl.opencl.CL11.CL_KERNEL_PREFERRED_WORK_GROUP_SIZE_MULTIPLE;
import static org.lwjgl.opencl.CL11.CL_KERNEL_PRIVATE_MEM_SIZE;
import static org.lwjgl.opencl.CL12.CL_KERNEL_GLOBAL_WORK_SIZE;

public final class CLUtil {

    private CLUtil() {
    }

    public static CLDevice selectDevice() {
        Scanner scanner = new Scanner(System.in);
        String newLine = System.lineSeparator();
        List<CLPlatform> platforms = CLPlatform.list();
        List<CLDevice> devices = new ArrayList<>();
        StringBuffer message = new StringBuffer();
        for (CLPlatform platform : platforms) {
            message.append(platform.name()).append(newLine);
            List<CLDevice> platformDevices = platform.getDevices();
            for (CLDevice device : platformDevices) {
                message.append(String.format("    %2d : %s (%s / %s)%n", devices.size(), device.name(), device.vendor(), device.openCLVersion()));
                devices.add(device);
            }
        }
        message.append("-1 : Exit");
        if (devices.isEmpty()) {
            System.out.println("No Open CL devices found");
            return null;
        }
        if (devices.size() == 1)
            return devices.get(0);

        System.out.println(message);
        while (true) {
            String input = scanner.nextLine();
            try {
                int i = Integer.parseInt(input);
                if (i < 0)
                    return null;
                if (i < devices.size())
                    return devices.get(i);
            } catch (NumberFormatException e) {
                // swallow
            }
            System.out.println("Invalid index");
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
