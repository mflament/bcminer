package com.infine.demo.clbcminer.clsupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public final class CLUtils {
    private CLUtils() {
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

}
