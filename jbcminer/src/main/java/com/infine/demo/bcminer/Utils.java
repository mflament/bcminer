package com.infine.demo.bcminer;

public final class Utils {
    private Utils() {
    }

    /**
     * Reverse bytes string byte wise (<a href="https://btcinformation.org/en/developer-reference#hash-byte-order">used in block header</a>)
     */
    public static String reverse(String hex) {
        char[] result = new char[hex.length()];
        for (int i = 0; i < hex.length(); i += 2) {
            int dst = hex.length() - 2 - i;
            result[dst] = hex.charAt(i);
            result[dst + 1] = hex.charAt(i + 1);
        }
        return new String(result);
    }

    public static int[] parse(String hex) {
        if (hex.length() % 8 != 0)
            throw new IllegalArgumentException("Invalid hex length " + hex.length());
        int length = hex.length() / 8;
        int[] data = new int[length];
        parse(hex, data, 0);
        return data;
    }

    // decode encoded little endian integers hex bytes
    public static void parse(String hex, int[] target, int targetIndex) {
        if (hex.length() % 8 != 0)
            throw new IllegalArgumentException("Invalid hex length " + hex.length());
        int intCount = hex.length() / 8;
        if (targetIndex + intCount > target.length)
            throw new IndexOutOfBoundsException("Target index out of bound " + (targetIndex + intCount) + " >= " + target.length);
        for (int i = 0; i < intCount; i++) {
            int value = 0;
            for (int j = 0; j < 4; j++) {
                int offset = i * 8 + j * 2;
                int b = Integer.parseInt(hex.substring(offset, offset + 2), 16);
                value |= b << ((3 - j) * 8);
            }
            target[targetIndex + i] = value;
        }
    }

    public static String print(int[] buffer) {
        StringBuilder sb = new StringBuilder(buffer.length * 4 * 2);
        print(sb, buffer, 0, buffer.length);
        return sb.toString();
    }

    public static void print(StringBuilder target, int[] buffer, int index, int count) {
        for (int i = index; i < index + count; i++) {
            int value = buffer[i];
            for (int j = 0; j < 4; j++) {
                int b = (value >> ((3 - j) * 8)) & 0xFF;
                String s = Integer.toString(b, 16).toLowerCase();
                if (s.length() < 2) target.append("0");
                target.append(s);
            }
        }
    }

    public static int flipEndianess(int i) {
        int res = (i >> 24) & 0xFF;
        res |= (i << 24) & 0xFF000000;
        res |= (i >> 8) & 0xFF00;
        res |= (i << 8) & 0xFF0000;
        return res;
    }


}
