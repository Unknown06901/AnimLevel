package com.shaml.animlevel

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Init Shizuku
        ShizukuHelper.init()

        setContent {
            AnimLevelApp(
                onRequestShizukuPermission = { ShizukuHelper.requestPermission() },
                onShowToast = { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ShizukuHelper.destroy()
    }
}
