import java.io.IOException;
import java.io.ObjectInput;

public class Sample {

    public Object readPrimive(Class<?> type, ObjectInput input) throws IOException {
        if (type == byte.class) {
            return input.readByte();
        } else if (type == short.class) {
            return input.readShort();
        } else if (type == int.class) {
            return input.readInt();
        } else if (type == long.class) {
            return input.readLong();
        } else if (type == float.class) {
            return input.readFloat();
        } else if (type == double.class) {
            return input.readDouble();
        } else if (type == boolean.class) {
            return input.readBoolean();
        } else
            throw new IllegalArgumentException("");
    }

    public enum E {
        A,B,C;
    }

    public static void test(E e) {
        switch (e) {
            case A:
                return;
            case B:
                return;
            case C:
                return;
        }
    }
}
