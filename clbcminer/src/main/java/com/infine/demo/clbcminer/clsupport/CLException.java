package com.infine.demo.clbcminer.clsupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.lwjgl.opencl.CL10.CL_SUCCESS;

public class CLException extends RuntimeException {

    public static void check(int error) {
        check("OpenCL error ", error);
    }

    public static void check(String message, int error) {
        if (error != CL_SUCCESS)
            throw new CLException(message, error);
    }

    public static final Map<Integer, String> ERROR_MESSAGES = loadMessages();

    public CLException(String message, int error) {
        super(String.format("%s : %d: %s", message , error, ERROR_MESSAGES.get(error)));
    }

    private static Map<Integer, String> loadMessages() {
        Properties properties = new Properties();
        try (InputStream is = CLException.class.getResourceAsStream("/clerrors.properties")) {
            if (is == null)
                throw new IllegalStateException("clerrors.properties not found");
            properties.load(is);
            Map<Integer, String> errors = new HashMap<>();
            properties.forEach((key, value) -> errors.put(Integer.parseInt(key.toString()), value.toString()));
            return Map.copyOf(errors);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
