package com.shaml.animlevel

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku

/** Manages the Shizuku connection and executes privileged shell commands via it. */
object ShizukuHelper {

    private const val TAG = "AnimLevel/Shizuku"
    private const val SHIZUKU_PERMISSION_CODE = 1001

    // ---- State ----
    var isAvailable: Boolean = false
        private set
    var isPermissionGranted: Boolean = false
        private set

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Shizuku binder received, version=${Shizuku.getVersion()}")
        isAvailable = true
        isPermissionGranted = checkPermission()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d(TAG, "Shizuku binder dead")
        isAvailable = false
        isPermissionGranted = false
    }

    private val requestPermissionListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            isPermissionGranted = grantResult == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Shizuku permission result: $isPermissionGranted")
        }

    fun init() {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(requestPermissionListener)
        if (Shizuku.pingBinder()) {
            isAvailable = true
            isPermissionGranted = checkPermission()
        }
    }

    fun destroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(requestPermissionListener)
    }

    fun requestPermission() {
        if (!isAvailable) return
        if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) return
        Shizuku.requestPermission(SHIZUKU_PERMISSION_CODE)
    }

    fun checkPermission(): Boolean {
        return try {
            if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) false
            else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e(TAG, "checkPermission failed: $e")
            false
        }
    }

    /**
     * Run a shell command string via /system/bin/sh -c
     */
    fun runShellCommand(cmd: String): Boolean {
        if (!isAvailable || !checkPermission()) return false
        return try {
            val res = ShizukuProcessHelper.runCommandDetailed(arrayOf("/system/bin/sh", "-c", cmd))
            if (res.exitCode != 0) {
                Log.w(TAG, "Command failed (code ${res.exitCode}): $cmd | stderr: ${res.stderr}")
            }
            res.exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "runShellCommand failed: $e")
            false
        }
    }

    /**
     * Run direct executable array
     */
    fun runDirectCommand(args: Array<String>): Boolean {
        if (!isAvailable || !checkPermission()) return false
        return try {
            val res = ShizukuProcessHelper.runCommandDetailed(args)
            if (res.exitCode != 0) {
                Log.w(TAG, "Direct command failed (code ${res.exitCode}): ${args.joinToString(" ")} | stderr: ${res.stderr}")
            }
            res.exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "runDirectCommand failed: $e")
            false
        }
    }

    /**
     * Set `persist.sys.oplus.anim_level` via Shizuku.
     * Tries:
     * 1. Native IShizukuService.setSystemProperty (zero-process direct IPC)
     * 2. Direct /system/bin/setprop binary execution
     * 3. Shell /system/bin/sh -c "setprop ..."
     */
    fun setAnimLevel(level: Int): Boolean {
        val levelStr = level.toString()

        // Method 1: Native Shizuku AIDL service call
        if (ShizukuProcessHelper.setSystemPropertyDirect(AnimLevelManager.PROP_KEY, levelStr)) {
            Log.d(TAG, "setAnimLevel succeeded via AIDL setSystemProperty")
            return true
        }

        // Method 2: Direct /system/bin/setprop execution
        if (runDirectCommand(arrayOf("/system/bin/setprop", AnimLevelManager.PROP_KEY, levelStr))) {
            Log.d(TAG, "setAnimLevel succeeded via /system/bin/setprop")
            return true
        }

        // Method 3: Shell invocation
        return runShellCommand("/system/bin/setprop ${AnimLevelManager.PROP_KEY} $levelStr")
    }
}
