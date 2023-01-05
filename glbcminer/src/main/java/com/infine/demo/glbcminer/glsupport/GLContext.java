package com.infine.demo.glbcminer.glsupport;

import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class GLContext {

    private final long window;

    public GLContext() {
        this.window = createWindow();
    }

    public void makeCurrent() {
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
    }

    private static long createWindow() {
        GLFW.glfwInit();
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_OPENGL_API);
        GLFW.glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        long window = glfwCreateWindow(1, 1, "GLBCMiner", 0, 0);
        if (window == NULL) {
            PointerBuffer pb = BufferUtils.createPointerBuffer(1024);
            int code = glfwGetError(pb);
            String message = "Failed to create the GLFW window " + Long.toHexString(code);
            long messagePtr = pb.get(0);
            if (messagePtr != 0) message += " : " + MemoryUtil.memASCII(pb.get(0));
            throw new RuntimeException(message);
        }
        return window;
    }

}
