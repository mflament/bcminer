package com.infine.demo.jbcminer;

public final class Utils {
    private Utils() {
    }

    public static int[] parse(String hex) {
        if (hex.length() % 8 != 0)
            throw new IllegalArgumentException("Invalid hex " + hex);
        int length = hex.length() / 8;
        int[] data = new int[length];
        for (int i = 0; i < length; i++) {
            int value = 0;
            for (int j = 0; j < 4; j++) {
                int offset = i * 8 + j * 2;
                int b = Integer.parseInt(hex.substring(offset, offset + 2), 16);
                value |= b << ((3 - j) * 8);
            }
            data[i] = value;
        }
        return data;
    }

    public static String print(int[] buffer) {
        return print(buffer, 0, buffer.length * 4);
    }

    public static String print(int[] buffer, int byteOffset, int byteCount) {
        StringBuilder sb = new StringBuilder(byteCount * 2); // 2 chars per byte in hex
        for (int i = byteOffset; i < Math.min(byteCount, buffer.length * 4); i++) {
            int intIndex = i / 4;
            int byteIndex = 3 - i % 4;
            int b = (buffer[intIndex] >> byteIndex * 8) & 0xFF;
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
