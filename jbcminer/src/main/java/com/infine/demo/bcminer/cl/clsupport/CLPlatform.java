package com.infine.demo.bcminer.cl.clsupport;

import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.infine.demo.bcminer.cl.clsupport.CLUtil.readStringInfo;
import static org.lwjgl.opencl.CL10.*;

public record CLPlatform(long id, String name) {

    public static List<CLPlatform> list() {
        int[] countBuffer = new int[1];
        // get platforms count
        CLException.check(clGetPlatformIDs(null, countBuffer));
        int count = countBuffer[0];
        if (count == 0)
            return Collections.emptyList();
        // get platforms ids
        PointerBuffer platformIds = BufferUtils.createPointerBuffer(count);
        CLException.check(clGetPlatformIDs(platformIds, countBuffer));

        List<CLPlatform> platforms = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long platformId = platformIds.get(i);
            String name = readStringInfo(
                    sizeBuffer -> clGetPlatformInfo(platformId, CL_PLATFORM_NAME, (ByteBuffer) null, sizeBuffer),
                    dataBuffer -> clGetPlatformInfo(platformId, CL_PLATFORM_NAME, dataBuffer, null));
            platforms.add(new CLPlatform(platformId, name));
        }
        return platforms;
    }

    @Nullable
    public static CLPlatform first() {
        List<CLPlatform> platforms = list();
        if (platforms.isEmpty())
            return null;
        return platforms.get(0);
    }

    public List<CLDevice> getDevices() {
        return CLDevice.list(this);
    }
}
