package com.reflow.screendraw

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var drawingView: DrawingView
    private lateinit var toolbarWindow: LinearLayout
    private lateinit var scrollPanel: ScrollView
    private lateinit var panel: LinearLayout
    private lateinit var dot: TextView
    private lateinit var toolbarParams: WindowManager.LayoutParams
    private lateinit var drawParams: WindowManager.LayoutParams
    private lateinit var drawBtn: Button

    private var drawModeOn = false
    private var minimized = false
    private var candlesOpen = false

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

        drawingView = DrawingView(this)
        drawParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        windowManager.addView(drawingView, drawParams)

        buildToolbar(overlayType)
    }

    private fun setDrawMode(enabled: Boolean) {
        drawModeOn = enabled
        drawParams.flags = if (enabled) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        } else {
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        windowManager.updateViewLayout(drawingView, drawParams)
        drawBtn.background = GradientDrawable().apply {
            setColor(Color.parseColor(if (enabled) "#C9622F" else "#3F7D78"))
            cornerRadius = dp(10).toFloat()
        }
    }

    private fun roundedBg(radiusDp: Int, colorHex: String = "#EE16233A"): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(colorHex))
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    private fun buildToolbar(overlayType: Int) {
        toolbarParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(80)
        }

        toolbarWindow = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // --- minimized dot ---
        dot = TextView(this).apply {
            text = "⠿"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#F6F3EA"))
            textSize = 18f
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE16233A"))
                shape = GradientDrawable.OVAL
            }
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            visibility = View.GONE
        }
        var dragStartRawX = 0f; var dragStartRawY = 0f
        var dragStartX = 0; var dragStartY = 0
        var moved = false
        dot.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = toolbarParams.x; dragStartY = toolbarParams.y
                    dragStartRawX = event.rawX; dragStartRawY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - dragStartRawX).toInt()
                    val dy = (event.rawY - dragStartRawY).toInt()
                    if (Math.abs(dx) > 6 || Math.abs(dy) > 6) moved = true
                    toolbarParams.x = dragStartX + dx
                    toolbarParams.y = dragStartY + dy
                    windowManager.updateViewLayout(toolbarWindow, toolbarParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) toggleMinimize()
                    true
                }
                else -> false
            }
        }

        // --- full panel, inside a ScrollView so it can never exceed the screen ---
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(10), dp(8), dp(10))
        }

        scrollPanel = ScrollView(this).apply {
            addView(panel)
            background = roundedBg(16)
        }
        val maxHeight = (resources.displayMetrics.heightPixels * 0.82).toInt()
        scrollPanel.layoutParams = LinearLayout.LayoutParams(dp(60), maxHeight)

        addDragHandle()

        drawBtn = Button(this).apply {
            text = "●"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#3F7D78"))
                cornerRadius = dp(10).toFloat()
            }
            setOnClickListener { setDrawMode(!drawModeOn) }
        }
        panel.addView(drawBtn, fullWidthParams(dp(36)))

        addDivider()

        addIconTool(IconFactory.pen(dp(28)), "pen")
        addIconTool(IconFactory.highlighter(dp(28)), "highlighter")
        addIconTool(IconFactory.eraser(dp(28)), "eraser")
        addIconTool(IconFactory.circleIcon(dp(28)), "circle")
        addIconTool(IconFactory.squareIcon(dp(28)), "square")
        addIconTool(IconFactory.lineIcon(dp(28)), "line")
        addIconTool(IconFactory.arrowIcon(dp(28)), "arrow")
