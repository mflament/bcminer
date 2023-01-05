package com.infine.demo.glbcminer.glsupport;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.lwjgl.opengl.GL33.*;

public final class GLSupport {
    private GLSupport() {
    }

    public static int loadProgram(String vsResource, String fsResource) {
        int program = check(glCreateProgram());
        attachShader(program, vsResource, GL_VERTEX_SHADER);
        attachShader(program, fsResource, GL_FRAGMENT_SHADER);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) != GL_TRUE) {
            String log = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            throw new IllegalStateException(log);
        }
        return program;
    }

    private static void attachShader(int program, String resource, int shaderType) {
        int shader = check(glCreateShader(shaderType));
        String source = loadShader(resource);
        glShaderSource(shader, source);
        glCompileShader(shader);
        glAttachShader(program, shader);
        glDeleteShader(shader);
    }

    private static String loadShader(String resource) {
        ClassLoader classLoader = GLSupport.class.getClassLoader();
        try (InputStream is = classLoader.getResourceAsStream(resource)) {
            if (is == null) throw new FileNotFoundException("Shader resource not found " + resource);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static int check(int result) {
        if (result == 0)
            throw new IllegalStateException("Error creating GL resource");
        return result;
    }

}
