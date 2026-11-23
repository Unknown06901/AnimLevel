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
        if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
            // Old API – not supported, skip
            return
        }
        Shizuku.requestPermission(SHIZUKU_PERMISSION_CODE)
    }

    private fun checkPermission(): Boolean {
        return try {
            if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) false
            else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e(TAG, "checkPermission failed: $e")
            false
        }
    }

    /**
     * Run [cmd] as the ADB shell user via Shizuku's newProcess API.
     * Uses the Java wrapper to avoid Kotlin visibility issues with ShizukuRemoteProcess.
     * Returns true if the command exited with code 0.
     */
    fun runShellCommand(cmd: String): Boolean {
        if (!isAvailable || !isPermissionGranted) return false
        return try {
            val exitCode = ShizukuProcessHelper.runCommand(arrayOf("sh", "-c", cmd))
            if (exitCode != 0) Log.w(TAG, "Command exited with $exitCode: $cmd")
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "runShellCommand failed: $e")
            false
        }
    }

    /** Set `persist.sys.oplus.anim_level` via Shizuku shell. */
    fun setAnimLevel(level: Int): Boolean {
        val cmd = AnimLevelManager.buildSetpropCmd(level)
        Log.d(TAG, "Executing: $cmd")
        return runShellCommand(cmd)
    }
}
