package com.infine.demo.clbcminer.clsupport;

import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

public final class CLUtil {

    private CLUtil() {
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
