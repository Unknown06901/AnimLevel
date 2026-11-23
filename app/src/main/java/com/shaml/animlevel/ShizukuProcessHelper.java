package com.shaml.animlevel;

import java.lang.reflect.Method;

/**
 * Executes shell commands via Shizuku's remote process via reflection.
 */
public class ShizukuProcessHelper {
    public static int runCommand(String[] cmd) {
        try {
            Class<?> clazz = Class.forName("rikka.shizuku.Shizuku");
            Method method = clazz.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
            method.setAccessible(true);
            Process process = (Process) method.invoke(null, cmd, null, null);
            if (process == null) return -1;
            return process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }
}
