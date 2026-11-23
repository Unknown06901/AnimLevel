package com.shaml.animlevel

import java.io.DataOutputStream

/** All logic for reading and writing `persist.sys.oplus.anim_level` and rebooting. */
object AnimLevelManager {

    const val PROP_KEY = "persist.sys.oplus.anim_level"

    // Animation tier definitions:
    // Level 1: Flagship Tier (Top-tier profile: full Gaussian blur, complex fluid physics, stacked iOS recents)
    // Level 2: Balanced Tier (Mid-range profile: standard transitions & balanced performance)
    // Level 3: Low-end Tier (Budget devices profile: simplified animations & reduced GPU load)
    val LEVEL_INFO = listOf(
        LevelItem(1, "Level 1", "Flagship Tier", "Top-tier profile: full Gaussian blur, complex physics & stacked recents"),
        LevelItem(2, "Level 2", "Balanced Tier", "Mid-range profile: standard transitions & balanced performance"),
        LevelItem(3, "Level 3", "Low-end Tier", "Budget device profile: simplified animations & reduced GPU load")
    )

    data class LevelItem(
        val value: Int,
        val label: String,
        val subLabel: String,
        val description: String
    )

    // ----- Read -----

    /** Read persist prop via getprop shell command. */
    fun readPropViaShell(): Int? {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("getprop", PROP_KEY))
            val value = p.inputStream.bufferedReader().readLine()?.trim()
            p.destroy()
            value?.toIntOrNull()
        } catch (_: Exception) { null }
    }

    // ----- Write: Root path -----

    /** Set prop via `su -c setprop`. */
    fun setViaRoot(level: Int): Boolean {
        return try {
            val p = Runtime.getRuntime().exec("su")
            val dos = DataOutputStream(p.outputStream)
            dos.writeBytes("setprop $PROP_KEY $level\n")
            dos.writeBytes("exit\n")
            dos.flush()
            p.waitFor() == 0
        } catch (_: Exception) { false }
    }

    // ----- Reboot -----

    /** Reboot device via root or shell. */
    fun rebootDevice(viaRoot: Boolean): Boolean {
        return if (viaRoot) {
            try {
                val p = Runtime.getRuntime().exec("su")
                val dos = DataOutputStream(p.outputStream)
                dos.writeBytes("reboot\n")
                dos.flush()
                true
            } catch (_: Exception) { false }
        } else {
            ShizukuHelper.runShellCommand("reboot")
        }
    }

    /** Soft-restart SystemUI and Launcher without full device reboot. */
    fun restartUiProcesses(viaRoot: Boolean): Boolean {
        val cmds = listOf("am crash com.android.launcher", "am crash com.android.systemui")
        return if (viaRoot) {
            try {
                val p = Runtime.getRuntime().exec("su")
                val dos = DataOutputStream(p.outputStream)
                for (cmd in cmds) {
                    dos.writeBytes("$cmd\n")
                }
                dos.writeBytes("exit\n")
                dos.flush()
                p.waitFor() == 0
            } catch (_: Exception) { false }
        } else {
            for (cmd in cmds) {
                ShizukuHelper.runShellCommand(cmd)
            }
            true
        }
    }

    fun buildSetpropCmd(level: Int): String = "setprop $PROP_KEY $level"

    fun isRooted(): Boolean {
        return try {
            val p = Runtime.getRuntime().exec("su -c id")
            val out = p.inputStream.bufferedReader().readLine() ?: ""
            p.destroy()
            out.contains("uid=0")
        } catch (_: Exception) { false }
    }
}
