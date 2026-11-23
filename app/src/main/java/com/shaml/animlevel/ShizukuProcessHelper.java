package com.shaml.animlevel;

import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import moe.shizuku.server.IShizukuService;
import rikka.shizuku.Shizuku;

/**
 * Handles communication with Shizuku daemon:
 * 1. Native AIDL setSystemProperty via IShizukuService (fastest, direct Android SystemProperties.set)
 * 2. Shell process fallback via newProcess (with full binary path & PATH environment)
 */
public class ShizukuProcessHelper {
    private static final String TAG = "AnimLevel/ShizukuExec";

    /**
     * Directly set system property using Shizuku's native IShizukuService AIDL interface.
     * This calls the server's internal SystemProperties.set() without spawning a shell process.
     */
    public static boolean setSystemPropertyDirect(String key, String val) {
        try {
            Method method = Shizuku.class.getDeclaredMethod("requireService");
            method.setAccessible(true);
            IShizukuService service = (IShizukuService) method.invoke(null);
            if (service != null) {
                service.setSystemProperty(key, val);
                Log.d(TAG, "Successfully invoked service.setSystemProperty(" + key + ", " + val + ")");
                return true;
            }
        } catch (Throwable t) {
            Log.w(TAG, "Direct setSystemProperty failed: " + t.getMessage(), t);
        }
        return false;
    }

    public static class ExecResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        public ExecResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

    public static ExecResult runCommandDetailed(String[] cmd) {
        try {
            Class<?> clazz = Class.forName("rikka.shizuku.Shizuku");
            Method method = clazz.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
            method.setAccessible(true);

            // Ensure standard PATH environment is present
            String[] env = new String[]{
                "PATH=/system/bin:/system/xbin:/vendor/bin:/apex/com.android.runtime/bin"
            };

            Process process = (Process) method.invoke(null, new Object[]{ cmd, env, null });
            if (process == null) {
                Log.e(TAG, "Shizuku.newProcess returned null");
                return new ExecResult(-1, "", "Process is null");
            }

            StringBuilder outSb = new StringBuilder();
            StringBuilder errSb = new StringBuilder();

            Thread tOut = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) outSb.append(line).append("\n");
                } catch (Exception ignored) {}
            });
            Thread tErr = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) errSb.append(line).append("\n");
                } catch (Exception ignored) {}
            });

            tOut.start();
            tErr.start();

            int exitCode = process.waitFor();
            tOut.join(1000);
            tErr.join(1000);

            String out = outSb.toString().trim();
            String err = errSb.toString().trim();
            Log.d(TAG, "Cmd exit: " + exitCode + ", stdout: " + out + ", stderr: " + err);
            return new ExecResult(exitCode, out, err);
        } catch (Throwable e) {
            Log.e(TAG, "Execution exception: " + e.getMessage(), e);
            return new ExecResult(-1, "", e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    public static int runCommand(String[] cmd) {
        return runCommandDetailed(cmd).exitCode;
    }
}
