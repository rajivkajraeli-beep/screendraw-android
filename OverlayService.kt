package com.reflow.screendraw

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.core.app.NotificationCompat

/**
 * Two overlay windows are added on top of everything else:
 *  1. drawingView  — full-screen, transparent, touch-through by default.
 *     Toggling "Draw mode" simply flips FLAG_NOT_TOUCHABLE, which Android
 *     handles natively and instantly — no ghosting, no black-screen tricks
 *     needed (unlike the Windows version, this is a well-supported OS
 *     capability, since it's exactly what floating chat-head apps use).
 *  2. toolbarPanel — small, always interactive, draggable via its handle.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var drawingView: DrawingView
    private lateinit var toolbarPanel: LinearLayout
    private lateinit var drawParams: WindowManager.LayoutParams
    private lateinit var toolbarParams: WindowManager.LayoutParams

    private var drawModeOn = false
    private val toolButtons = mutableListOf<Button>()

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
            x = 24
            y = 160
        }

        toolbarPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.parseColor("#EE16233A"))
        }

        addDragHandle()

        val drawBtn = Button(this).apply {
            text = "Draw: OFF"
            setOnClickListener {
                setDrawMode(!drawModeOn)
                text = if (drawModeOn) "Draw: ON" else "Draw: OFF"
            }
        }
        toolbarPanel.addView(drawBtn)

        addToolButton("Pen", DrawingView.Tool.PEN)
        addToolButton("Highlighter", DrawingView.Tool.HIGHLIGHTER)
        addToolButton("Eraser", DrawingView.Tool.ERASER)
        addToolButton("Circle", DrawingView.Tool.CIRCLE)
        addToolButton("Square", DrawingView.Tool.SQUARE)
        addToolButton("Line", DrawingView.Tool.LINE)
        addToolButton("Arrow", DrawingView.Tool.ARROW)

        addColorRow()
        addThicknessSlider()

        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actionRow.addView(Button(this).apply {
            text = "Undo"
            setOnClickListener { drawingView.undo() }
        })
        actionRow.addView(Button(this).apply {
            text = "Clear"
            setOnClickListener { drawingView.clear() }
        })
        toolbarPanel.addView(actionRow)

        toolbarPanel.addView(Button(this).apply {
            text = "Whiteboard"
            setOnClickListener { drawingView.toggleWhiteboard() }
        })
        toolbarPanel.addView(Button(this).apply {
            text = "Save"
            setOnClickListener { drawingView.saveToGallery(this@OverlayService) }
        })
        toolbarPanel.addView(Button(this).apply {
            text = "Quit"
            setOnClickListener { stopSelf() }
        })

        windowManager.addView(toolbarPanel, toolbarParams)
    }

    private fun addDragHandle() {
        val handle = TextView(this).apply {
            text = "⠿ ⠿ ⠿  drag"
            setTextColor(Color.parseColor("#8FA3BD"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        var lastRawX = 0f
        var lastRawY = 0f
        var startX = 0
        var startY = 0
        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = toolbarParams.x
                    startY = toolbarParams.y
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    toolbarParams.x = startX + (event.rawX - lastRawX).toInt()
                    toolbarParams.y = startY + (event.rawY - lastRawY).toInt()
                    windowManager.updateViewLayout(toolbarPanel, toolbarParams)
                    true
                }
                else -> false
            }
        }
        toolbarPanel.addView(handle)
    }

    private fun addToolButton(label: String, tool: DrawingView.Tool) {
        val b = Button(this).apply {
            text = label
            setOnClickListener { drawingView.currentTool = tool }
        }
        toolButtons.add(b)
        toolbarPanel.addView(b)
    }

    private fun addColorRow() {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val colors = listOf(
            Color.parseColor("#FF3B30"), Color.parseColor("#FFD60A"),
            Color.parseColor("#34C759"), Color.parseColor("#0A84FF"),
            Color.BLACK, Color.WHITE
        )
        colors.forEach { c ->
            val swatch = View(this).apply {
                setBackgroundColor(c)
                layoutParams = LinearLayout.LayoutParams(48, 48).apply { setMargins(4, 4, 4, 4) }
                setOnClickListener { drawingView.currentColor = c }
            }
            row.addView(swatch)
        }
        toolbarPanel.addView(row)
    }

    private fun addThicknessSlider() {
        val seek = SeekBar(this).apply {
            max = 40
            progress = 6
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                    drawingView.strokeWidth = value.toFloat().coerceAtLeast(2f)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        toolbarPanel.addView(seek)
    }

    private fun startForegroundNotification() {
        val channelId = "screendraw_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "ScreenDraw", NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("ScreenDraw is running")
            .setContentText("Tap the floating toolbar to draw on your screen.")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .build()
        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::drawingView.isInitialized) windowManager.removeView(drawingView)
        if (::toolbarPanel.isInitialized) windowManager.removeView(toolbarPanel)
    }
}
