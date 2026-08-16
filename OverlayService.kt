package com.reflow.screendraw

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
        val quickBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundedBg(12)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            visibility = View.GONE
        }
        quickBarRef = quickBar
        fun quickBtn(icon: Bitmap, action: () -> Unit): ImageButton = ImageButton(this).apply {
            setImageBitmap(icon)
            background = GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = dp(6).toFloat() }
            scaleType = ImageView.ScaleType.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
            setOnClickListener {
                action()
                quickBar.visibility = View.GONE
                windowManager.updateViewLayout(toolbarWindow, toolbarParams)
            }
        }
        quickBar.addView(quickBtn(IconFactory.pen(dp(26))) { drawingView.currentTool = "pen"; setDrawMode(true) })
        quickBar.addView(quickBtn(IconFactory.highlighter(dp(26))) { drawingView.currentTool = "highlighter"; setDrawMode(true) })
        quickBar.addView(quickBtn(IconFactory.eraser(dp(26))) { drawingView.currentTool = "eraser"; setDrawMode(true) })
        quickBar.addView(quickBtn(IconFactory.undoIcon(dp(26))) { drawingView.undo() })

        var dragStartRawX = 0f; var dragStartRawY = 0f
        var dragStartX = 0; var dragStartY = 0
        var moved = false
        var longPressTriggered = false
        val longPressHandler = Handler(Looper.getMainLooper())
        val longPressRunnable = Runnable {
            longPressTriggered = true
            quickBar.visibility = if (quickBar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            windowManager.updateViewLayout(toolbarWindow, toolbarParams)
        }
        dot.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = toolbarParams.x; dragStartY = toolbarParams.y
                    dragStartRawX = event.rawX; dragStartRawY = event.rawY
                    moved = false
                    longPressTriggered = false
                    longPressHandler.postDelayed(longPressRunnable, 350)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - dragStartRawX).toInt()
                    val dy = (event.rawY - dragStartRawY).toInt()
                    if (Math.abs(dx) > 6 || Math.abs(dy) > 6) {
                        moved = true
                        longPressHandler.removeCallbacks(longPressRunnable)
                    }
                    if (!longPressTriggered) {
                        toolbarParams.x = dragStartX + dx
                        toolbarParams.y = dragStartY + dy
                        windowManager.updateViewLayout(toolbarWindow, toolbarParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    if (!longPressTriggered && !moved) toggleMinimize()
                    true
                }
                else -> false
            }
        }

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

        addDivider()

        val candleToggle = Button(this).apply {
            text = "▸"
            textSize = 12f
            setTextColor(Color.parseColor("#C3CEDD"))
            background = null
        }
        panel.addView(candleToggle, fullWidthParams(dp(24)))

        val candleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val stages = floatArrayOf(0.78f, 0.52f, 0.28f, 0.08f)
        for (i in 0..3) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(smallIconButton(IconFactory.candle(dp(26), true, stages[i])) { drawingView.currentTool = "gc${i + 1}" })
            row.addView(smallIconButton(IconFactory.candle(dp(26), false, stages[i])) { drawingView.currentTool = "rc${i + 1}" })
            candleContainer.addView(row)
        }
        val nwRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        nwRow.addView(smallIconButton(IconFactory.candle(dp(26), true, 0.62f, wick = false)) { drawingView.currentTool = "gcnw" })
        nwRow.addView(smallIconButton(IconFactory.candle(dp(26), false, 0.62f, wick = false)) { drawingView.currentTool = "rcnw" })
        candleContainer.addView(nwRow)
        val dojiRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        dojiRow.addView(smallIconButton(IconFactory.candle(dp(26), true, 0f, wick = true, lineBody = true)) { drawingView.currentTool = "gcdoji" })
        dojiRow.addView(smallIconButton(IconFactory.candle(dp(26), false, 0f, wick = true, lineBody = true)) { drawingView.currentTool = "rcdoji" })
        candleContainer.addView(dojiRow)

        candleToggle.setOnClickListener {
            candlesOpen = !candlesOpen
            candleContainer.visibility = if (candlesOpen) View.VISIBLE else View.GONE
            candleToggle.text = if (candlesOpen) "▾" else "▸"
        }
        panel.addView(candleContainer)

        addDivider()

        val seek = SeekBar(this).apply {
            max = 60; progress = 6
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                    drawingView.strokeWidth = value.toFloat().coerceAtLeast(2f)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        panel.addView(seek, fullWidthParams(dp(30)))

        addDivider()

        val actionRow1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actionRow1.addView(smallIconButton(IconFactory.undoIcon(dp(24))) { drawingView.undo() })
        actionRow1.addView(smallIconButton(IconFactory.clearIcon(dp(24))) { drawingView.clear() })
        panel.addView(actionRow1)

        val actionRow2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actionRow2.addView(smallIconButton(IconFactory.whiteboardIcon(dp(24))) { drawingView.setBoardMode("white") })
        actionRow2.addView(smallIconButton(IconFactory.blackboardIcon(dp(24))) { drawingView.setBoardMode("black") })
        panel.addView(actionRow2)

        panel.addView(smallIconButton(IconFactory.saveIcon(dp(24))) {
            val ok = drawingView.saveToGallery(this)
            Toast.makeText(this, if (ok) "Saved to Pictures/ScreenDraw" else "Save failed", Toast.LENGTH_SHORT).show()
        }, fullWidthParams(dp(36)))

        addDivider()

        val colorGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val colors = listOf(
            Color.parseColor("#FF3B30"), Color.parseColor("#FFD60A"),
            Color.parseColor("#34C759"), Color.parseColor("#0A84FF"),
            Color.BLACK, Color.WHITE
        )
        var row: LinearLayout? = null
        colors.forEachIndexed { i, cColor ->
            if (i % 2 == 0) {
                row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                colorGrid.addView(row)
            }
            val swatch = View(this).apply {
                background = GradientDrawable().apply { setColor(cColor); shape = GradientDrawable.OVAL }
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
                setOnClickListener { drawingView.currentColor = cColor }
            }
            row?.addView(swatch)
        }
        panel.addView(colorGrid)

        addDivider()

        panel.addView(smallIconButton(IconFactory.quitIcon(dp(24))) { stopSelf() }, fullWidthParams(dp(36)))

        toolbarWindow.addView(dot)
        toolbarWindow.addView(quickBar)
        toolbarWindow.addView(scrollPanel)
        windowManager.addView(toolbarWindow, toolbarParams)
    }

    private fun fullWidthParams(height: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, height
    ).apply { setMargins(0, dp(2), 0, dp(2)) }

    private fun addDivider() {
        val line = View(this).apply {
            setBackgroundColor(Color.parseColor("#33F6F3EA"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                setMargins(0, dp(4), 0, dp(4))
            }
        }
        panel.addView(line)
    }

    private fun addDragHandle() {
        val handle = TextView(this).apply {
            text = "⠿ ⠿ ⠿"
            setTextColor(Color.parseColor("#8FA3BD"))
            gravity = Gravity.CENTER
            textSize = 9f
        }
        var lastRawX = 0f; var lastRawY = 0f
        var startX = 0; var startY = 0
        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = toolbarParams.x; startY = toolbarParams.y
                    lastRawX = event.rawX; lastRawY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    toolbarParams.x = startX + (event.rawX - lastRawX).toInt()
                    toolbarParams.y = startY + (event.rawY - lastRawY).toInt()
                    windowManager.updateViewLayout(toolbarWindow, toolbarParams)
                    true
                }
                else -> false
            }
        }
        panel.addView(handle, fullWidthParams(dp(20)))

        val minBtn = TextView(this).apply {
            text = "—"
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#8FA3BD"))
            setOnClickListener { toggleMinimize() }
        }
        panel.addView(minBtn, fullWidthParams(dp(16)))
    }

    private lateinit var quickBarRef: LinearLayout

    private fun toggleMinimize() {
        minimized = !minimized
        scrollPanel.visibility = if (minimized) View.GONE else View.VISIBLE
        dot.visibility = if (minimized) View.VISIBLE else View.GONE
        if (::quickBarRef.isInitialized) quickBarRef.visibility = View.GONE
    }

    private val toolButtons = mutableListOf<ImageButton>()

    private fun addIconTool(icon: Bitmap, tool: String) {
        val btn = ImageButton(this).apply {
            setImageBitmap(icon)
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dp(6).toFloat()
            }
            scaleType = ImageView.ScaleType.CENTER
            setOnClickListener {
                drawingView.currentTool = tool
                toolButtons.forEach { b -> b.background = GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = dp(6).toFloat() } }
                background = GradientDrawable().apply { setColor(Color.parseColor("#3F7D78")); cornerRadius = dp(6).toFloat() }
            }
        }
        toolButtons.add(btn)
        panel.addView(btn, fullWidthParams(dp(36)))
    }

    private fun smallIconButton(icon: Bitmap, onClick: () -> Unit): ImageButton {
        return ImageButton(this).apply {
            setImageBitmap(icon)
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dp(6).toFloat()
            }
            scaleType = ImageView.ScaleType.CENTER
            layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f)
            setOnClickListener { onClick() }
        }
    }

    private fun startForegroundNotification() {
        val channelId = "screendraw_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "ScreenDraw", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
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
        if (::toolbarWindow.isInitialized) windowManager.removeView(toolbarWindow)
    }
}
