package com.reflow.screendraw

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var permBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 160, 60, 60)
        }

        val title = TextView(this).apply {
            text = "ScreenDraw"
            textSize = 26f
        }
        val subtitle = TextView(this).apply {
            text = "Draw over any app on your screen — freehand, shapes, and colors."
            textSize = 14f
            setPadding(0, 12, 0, 60)
        }

        permBtn = Button(this).apply {
            text = "1. Grant overlay permission"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
            }
        }

        val startBtn = Button(this).apply {
            text = "2. Start ScreenDraw"
            setOnClickListener {
                if (Settings.canDrawOverlays(this@MainActivity)) {
                    val svc = Intent(this@MainActivity, OverlayService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(svc)
                    } else {
                        startService(svc)
                    }
                    finish()
                } else {
                    TextView(this@MainActivity).text = "Grant the overlay permission first (step 1)."
                }
            }
        }

        val stopBtn = Button(this).apply {
            text = "Stop ScreenDraw"
            setOnClickListener {
                stopService(Intent(this@MainActivity, OverlayService::class.java))
            }
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(permBtn)
        root.addView(startBtn)
        root.addView(stopBtn)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        if (Settings.canDrawOverlays(this)) {
            permBtn.text = "1. Overlay permission ✓ granted"
        }
    }
}
