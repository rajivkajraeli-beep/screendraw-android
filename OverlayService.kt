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
    private lateinit var scrollPanel: View
    private lateinit var panel: LinearLayout
    private lateinit var dot: TextView
    private lateinit var toolbarParams: WindowManager.LayoutParams
    private lateinit var drawParams: WindowManager.LayoutParams
    private lateinit var drawBtn: Button
    private lateinit var colorPickerWindow: LinearLayout
    private lateinit var colorPickerParams: WindowManager.LayoutParams
    private lateinit var radialMenuWindow: FrameLayout
    private lateinit var radialMenuParams: WindowManager.LayoutParams
    private var radialMenuVisible = false
    private lateinit var toolOptionsWindow: FrameLayout
    private lateinit var toolOptionsParams: WindowManager.LayoutParams

    private var drawModeOn = false
    private var minimized = false
    private var candlesOpen = false
    private var isHorizontal = false
    private var pickerHue = 0f
    private var pickerSat = 0f
    private var pickerVal = 1f
    private val thicknessLevels = floatArrayOf(3f, 6f, 10f, 16f, 24f)
    private val toolThickness = mutableMapOf("pen" to 6f, "highlighter" to 6f, "eraser" to 6f)

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
        buildColorPicker(overlayType)
        buildToolOptionsPopup(overlayType)
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
        if (::dot.isInitialized) {
            dot.background = GradientDrawable().apply {
                setColor(Color.parseColor(if (enabled) "#EEC9622F" else "#EE16233A"))
                shape = GradientDrawable.OVAL
            }
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
        // --- radial quick-access menu: long-press the dot to get a circular popup
        //     with Pen / Eraser / Undo / Clear All / Expand, without opening the full panel ---
        buildRadialMenu(overlayType)

        var dragStartRawX = 0f; var dragStartRawY = 0f
        var dragStartX = 0; var dragStartY = 0
        var moved = false
        var longPressTriggered = false
        val longPressHandler = Handler(Looper.getMainLooper())
        val longPressRunnable = Runnable {
            longPressTriggered = true
            toggleRadialMenu()
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
                        hideRadialMenu()
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
                    // simple tap (not a drag, not a long-press) directly toggles draw mode —
                    // this is the fast "turn pen off and give my phone back" action.
                    // if the radial menu is open, a plain tap on the dot just closes it instead.
                    if (!longPressTriggered && !moved) {
                        if (radialMenuVisible) hideRadialMenu() else setDrawMode(!drawModeOn)
                    }
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

        scrollPanel = buildScrollWrapper(false)

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

        addDivider()

        val shapesToggle = Button(this).apply {
            text = "▸"
            textSize = 12f
            setTextColor(Color.parseColor("#C3CEDD"))
            background = null
        }
        panel.addView(shapesToggle, fullWidthParams(dp(24)))

        val shapesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        addIconTool(IconFactory.circleIcon(dp(28)), "circle", shapesContainer)
        addIconTool(IconFactory.squareIcon(dp(28)), "square", shapesContainer)
        addIconTool(IconFactory.lineIcon(dp(28)), "line", shapesContainer)
        addIconTool(IconFactory.arrowIcon(dp(28)), "arrow", shapesContainer)

        var shapesOpen = false
        shapesToggle.setOnClickListener {
            shapesOpen = !shapesOpen
            shapesContainer.visibility = if (shapesOpen) View.VISIBLE else View.GONE
            shapesToggle.text = if (shapesOpen) "▾" else "▸"
        }
        panel.addView(shapesContainer)

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

        // thickness is now set per-tool via long-press on Pen/Highlighter/Eraser (see addIconTool) —
        // no always-visible row here anymore, since it didn't fit in the slim vertical column.

        val actionRow1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actionRow1.addView(smallIconButton(IconFactory.undoIcon(dp(24))) { drawingView.undo() })
        actionRow1.addView(smallIconButton(IconFactory.clearIcon(dp(24))) { drawingView.clear() })
        panel.addView(actionRow1)

        val actionRow2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actionRow2.addView(smallIconButton(IconFactory.whiteboardIcon(dp(24))) { drawingView.setBoardMode("white") })
        actionRow2.addView(smallIconButton(IconFactory.blackboardIcon(dp(24))) { drawingView.setBoardMode("black") })
        panel.addView(actionRow2)

        val saveBtn = smallIconButton(IconFactory.saveIcon(dp(24))) {}
        saveBtn.setOnClickListener {
            Toast.makeText(this, "Saving…", Toast.LENGTH_SHORT).show()
            val ok = try { drawingView.saveToGallery(this) } catch (e: Exception) { false }
            Toast.makeText(this, if (ok) "Saved to Pictures/ScreenDraw" else "Save failed", Toast.LENGTH_SHORT).show()
            // flash the icon itself green/red too, since a Toast alone is easy to miss or
            // gets suppressed by some phones — this gives an unmistakable visual confirmation
            saveBtn.background = GradientDrawable().apply {
                setColor(Color.parseColor(if (ok) "#3F7D78" else "#C0392B"))
                cornerRadius = dp(6).toFloat()
            }
            Handler(Looper.getMainLooper()).postDelayed({
                saveBtn.background = GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = dp(6).toFloat() }
            }, 1200)
        }
        panel.addView(saveBtn, fullWidthParams(dp(36)))

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
        val customRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val customBtn = TextView(this).apply {
            text = "+"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 11f
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#555566"))
                shape = GradientDrawable.OVAL
            }
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
            setOnClickListener {
                colorPickerWindow.visibility = View.VISIBLE
                windowManager.updateViewLayout(colorPickerWindow, colorPickerParams)
            }
        }
        customRow.addView(customBtn)
        colorGrid.addView(customRow)
        panel.addView(colorGrid)

        addDivider()

        panel.addView(smallIconButton(IconFactory.quitIcon(dp(24))) { stopSelf() }, fullWidthParams(dp(36)))

        toolbarWindow.addView(dot)
        toolbarWindow.addView(scrollPanel)
        windowManager.addView(toolbarWindow, toolbarParams)
    }

    // --- radial quick-access menu: 5 buttons arranged in a circle around the dot ---
    private fun buildRadialMenu(overlayType: Int) {
        val containerSize = dp(210)
        val btnSize = dp(36)
        val radius = dp(66)
        val center = containerSize / 2

        radialMenuParams = WindowManager.LayoutParams(
            containerSize,
            containerSize,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        radialMenuWindow = FrameLayout(this).apply { visibility = View.GONE }

        fun radialBtn(icon: Bitmap, angleDeg: Double, onTap: () -> Unit, onLongPress: (() -> Unit)? = null): ImageButton {
            val rad = Math.toRadians(angleDeg)
            val cx = center + (radius * Math.cos(rad)).toInt() - btnSize / 2
            val cy = center + (radius * Math.sin(rad)).toInt() - btnSize / 2
            val btn = ImageButton(this).apply {
                setImageBitmap(icon)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#EE16233A"))
                    shape = GradientDrawable.OVAL
                }
                scaleType = ImageView.ScaleType.CENTER
                layoutParams = FrameLayout.LayoutParams(btnSize, btnSize).apply { leftMargin = cx; topMargin = cy }
            }
            if (onLongPress == null) {
                btn.setOnClickListener {
                    onTap()
                    hideRadialMenu()
                }
            } else {
                // this item supports both: a quick tap does the normal action, but holding it
                // down pops up extra options (used for Pen -> thickness + color disc)
                val handler = Handler(Looper.getMainLooper())
                var longPressed = false
                var moved = false
                var downX = 0f; var downY = 0f
                val longPressRunnable = Runnable {
                    longPressed = true
                    onLongPress()
                }
                btn.setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            longPressed = false; moved = false
                            downX = event.x; downY = event.y
                            handler.postDelayed(longPressRunnable, 350)
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (Math.abs(event.x - downX) > dp(10) || Math.abs(event.y - downY) > dp(10)) {
                                moved = true
                                handler.removeCallbacks(longPressRunnable)
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            handler.removeCallbacks(longPressRunnable)
                            if (!longPressed && !moved) {
                                v.performClick()
                                onTap()
                                hideRadialMenu()
                            }
                            true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            handler.removeCallbacks(longPressRunnable)
                            true
                        }
                        else -> false
                    }
                }
            }
            return btn
        }

        // 8 items spaced 45° apart around the circle, starting at the top.
        // Pen: tap selects it, long-press opens the Thickness + Color disc.
        // Highlighter & Eraser: tap selects, long-press opens Thickness only (no color needed for eraser).
        radialMenuWindow.addView(radialBtn(IconFactory.pen(dp(20)), -90.0, onTap = {
            drawingView.currentTool = "pen"; drawingView.strokeWidth = toolThickness["pen"] ?: 6f; setDrawMode(true)
        }, onLongPress = {
            hideRadialMenu()
            showToolOptionsDisc(dot, "pen")
        }))
        radialMenuWindow.addView(radialBtn(IconFactory.highlighter(dp(20)), -45.0, onTap = {
            drawingView.currentTool = "highlighter"; drawingView.strokeWidth = toolThickness["highlighter"] ?: 6f; setDrawMode(true)
        }, onLongPress = {
            hideRadialMenu()
            showThicknessPicker(dot, "highlighter")
        }))
        radialMenuWindow.addView(radialBtn(IconFactory.eraser(dp(20)), 0.0, onTap = {
            drawingView.currentTool = "eraser"; drawingView.strokeWidth = toolThickness["eraser"] ?: 6f; setDrawMode(true)
        }, onLongPress = {
            hideRadialMenu()
            showThicknessPicker(dot, "eraser")
        }))
        radialMenuWindow.addView(radialBtn(IconFactory.clearIcon(dp(20)), 45.0, onTap = {
            drawingView.clear()
        }))
        radialMenuWindow.addView(radialBtn(IconFactory.undoIcon(dp(20)), 90.0, onTap = {
            drawingView.undo()
        }))
        radialMenuWindow.addView(radialBtn(IconFactory.whiteboardIcon(dp(20)), 135.0, onTap = {
            drawingView.setBoardMode("white")
        }))
        radialMenuWindow.addView(radialBtn(IconFactory.blackboardIcon(dp(20)), 180.0, onTap = {
            drawingView.setBoardMode("black")
        }))
        radialMenuWindow.addView(radialBtn(IconFactory.expandIcon(dp(20)), 225.0, onTap = {
            toggleMinimize()
        }))

        windowManager.addView(radialMenuWindow, radialMenuParams)
    }

    private fun toggleRadialMenu() {
        if (radialMenuVisible) hideRadialMenu() else showRadialMenu()
    }

    private fun showRadialMenu() {
        // center the radial menu exactly on the dot's current on-screen position,
        // in case the dot was dragged since the menu was created
        radialMenuParams.x = toolbarParams.x + dp(24) - radialMenuParams.width / 2
        radialMenuParams.y = toolbarParams.y + dp(24) - radialMenuParams.height / 2
        windowManager.updateViewLayout(radialMenuWindow, radialMenuParams)
        radialMenuWindow.visibility = View.VISIBLE
        radialMenuVisible = true
    }

    private fun hideRadialMenu() {
        if (::radialMenuWindow.isInitialized) radialMenuWindow.visibility = View.GONE
        radialMenuVisible = false
    }

    private fun buildColorPicker(overlayType: Int) {
        colorPickerParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = roundedBg(16)
        }

        val wheelSize = dp(200)
        val wheelView = ImageView(this).apply {
            setImageBitmap(generateColorWheel(wheelSize))
            layoutParams = LinearLayout.LayoutParams(wheelSize, wheelSize)
        }

        val preview = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(wheelSize, dp(28)).apply { topMargin = dp(10) }
            setBackgroundColor(drawingView.currentColor)
        }

        fun updatePreview() {
            preview.setBackgroundColor(Color.HSVToColor(floatArrayOf(pickerHue, pickerSat, pickerVal)))
        }

        wheelView.setOnTouchListener { _, event ->
            val cx = wheelSize / 2f; val cy = wheelSize / 2f
            val dx = event.x - cx; val dy = event.y - cy
            val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            val radius = wheelSize / 2f
            if (dist <= radius) {
                var angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                if (angle < 0) angle += 360f
                pickerHue = angle
                pickerSat = (dist / radius).coerceIn(0f, 1f)
                updatePreview()
            }
            true
        }

        val brightnessLabel = TextView(this).apply {
            text = "Brightness"
            setTextColor(Color.parseColor("#8FA3BD"))
            textSize = 10f
            setPadding(0, dp(8), 0, 0)
        }
        val brightnessSeek = SeekBar(this).apply {
            max = 100; progress = 100
            layoutParams = LinearLayout.LayoutParams(wheelSize, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                    pickerVal = value / 100f
                    updatePreview()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(wheelSize, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) }
        }
        val okBtn = Button(this).apply {
            text = "Use color"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                drawingView.currentColor = Color.HSVToColor(floatArrayOf(pickerHue, pickerSat, pickerVal))
                colorPickerWindow.visibility = View.GONE
            }
        }
        val cancelBtn = Button(this).apply {
            text = "Cancel"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { colorPickerWindow.visibility = View.GONE }
        }
        btnRow.addView(okBtn)
        btnRow.addView(cancelBtn)

        card.addView(wheelView)
        card.addView(preview)
        card.addView(brightnessLabel)
        card.addView(brightnessSeek)
        card.addView(btnRow)

        colorPickerWindow = LinearLayout(this).apply { addView(card) }
        colorPickerWindow.visibility = View.GONE
        windowManager.addView(colorPickerWindow, colorPickerParams)
    }

    private fun generateColorWheel(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val radius = size / 2f
        for (x in 0 until size) {
            for (y in 0 until size) {
                val dx = x - radius; val dy = y - radius
                val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                if (dist <= radius) {
                    var angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    if (angle < 0) angle += 360f
                    bmp.setPixel(x, y, Color.HSVToColor(floatArrayOf(angle, (dist / radius).coerceIn(0f, 1f), 1f)))
                } else {
                    bmp.setPixel(x, y, Color.TRANSPARENT)
                }
            }
        }
        return bmp
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

        val controlRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val minBtn = TextView(this).apply {
            text = "—"
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#8FA3BD"))
            layoutParams = LinearLayout.LayoutParams(0, dp(18), 1f)
            setOnClickListener { toggleMinimize() }
        }
        val orientBtn = ImageButton(this).apply {
            setImageBitmap(IconFactory.orientationIcon(dp(14)))
            background = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
            scaleType = ImageView.ScaleType.CENTER
            layoutParams = LinearLayout.LayoutParams(0, dp(18), 1f)
            setOnClickListener { toggleOrientation() }
        }
        controlRow.addView(minBtn)
        controlRow.addView(orientBtn)
        panel.addView(controlRow, fullWidthParams(dp(18)))
    }

    private fun buildScrollWrapper(horizontal: Boolean): View {
        val maxHeight = (resources.displayMetrics.heightPixels * 0.82).toInt()
        val maxWidth = (resources.displayMetrics.widthPixels * 0.82).toInt()
        return if (horizontal) {
            // height must NOT be hardcoded to a large fixed value (that was the bug that made
            // the horizontal bar cover most of the screen) — wrap_content keeps it exactly as
            // tall as its content actually needs, whether that's one slim icon row or a
            // section temporarily expanded open. Width is still capped so it doesn't run off
            // the right edge of the screen; it scrolls sideways for anything wider.
            HorizontalScrollView(this).apply {
                addView(panel)
                background = roundedBg(16)
                layoutParams = LinearLayout.LayoutParams(maxWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
        } else {
            ScrollView(this).apply {
                addView(panel)
                background = roundedBg(16)
                layoutParams = LinearLayout.LayoutParams(dp(60), maxHeight)
            }
        }
    }

    private fun toggleOrientation() {
        isHorizontal = !isHorizontal
        val wasVisible = scrollPanel.visibility
        (panel.parent as? ViewGroup)?.removeView(panel)
        panel.orientation = if (isHorizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL

        toolbarWindow.removeView(scrollPanel)
        val newWrapper = buildScrollWrapper(isHorizontal)
        newWrapper.visibility = wasVisible
        scrollPanel = newWrapper
        toolbarWindow.addView(newWrapper)
        windowManager.updateViewLayout(toolbarWindow, toolbarParams)
    }

    private fun toggleMinimize() {
        minimized = !minimized
        scrollPanel.visibility = if (minimized) View.GONE else View.VISIBLE
        dot.visibility = if (minimized) View.VISIBLE else View.GONE
        hideRadialMenu()
        hideToolOptions()
    }

    private val toolButtons = mutableListOf<ImageButton>()

    private fun addIconTool(icon: Bitmap, tool: String, container: LinearLayout? = null) {
        val target = container ?: panel
        val hasOptions = tool == "pen" || tool == "highlighter" || tool == "eraser"
        val btn = ImageButton(this).apply {
            setImageBitmap(icon)
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dp(6).toFloat()
            }
            scaleType = ImageView.ScaleType.CENTER
        }
        fun selectTool() {
            drawingView.currentTool = tool
            drawingView.strokeWidth = toolThickness[tool] ?: 6f
            toolButtons.forEach { b -> b.background = GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = dp(6).toFloat() } }
            btn.background = GradientDrawable().apply { setColor(Color.parseColor("#3F7D78")); cornerRadius = dp(6).toFloat() }
        }
        if (hasOptions) {
            // Pen / Highlighter / Eraser: tap selects the tool as usual; long-press pops up
            // a small Thickness/Color disc for that specific tool (each keeps its own thickness)
            val handler = Handler(Looper.getMainLooper())
            var longPressed = false
            var moved = false
            var downX = 0f; var downY = 0f
            val longPressRunnable = Runnable {
                longPressed = true
                showThicknessPicker(btn, tool)
            }
            btn.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        longPressed = false; moved = false
                        downX = event.x; downY = event.y
                        handler.postDelayed(longPressRunnable, 350)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (Math.abs(event.x - downX) > dp(10) || Math.abs(event.y - downY) > dp(10)) {
                            moved = true
                            handler.removeCallbacks(longPressRunnable)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        handler.removeCallbacks(longPressRunnable)
                        if (!longPressed && !moved) { v.performClick(); selectTool() }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacks(longPressRunnable)
                        true
                    }
                    else -> false
                }
            }
        } else {
            btn.setOnClickListener { selectTool() }
        }
        toolButtons.add(btn)
        target.addView(btn, fullWidthParams(dp(36)))
    }

    // --- small popup shown on long-press of Pen/Highlighter/Eraser: Thickness + Color ---
    private fun buildToolOptionsPopup(overlayType: Int) {
        toolOptionsParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        toolOptionsWindow = FrameLayout(this).apply { visibility = View.GONE }
        windowManager.addView(toolOptionsWindow, toolOptionsParams)
    }

    private fun positionPopupNear(anchor: View, popupWidth: Int, popupHeight: Int) {
        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        var x = loc[0] + anchor.width / 2 - popupWidth / 2
        var y = loc[1] - popupHeight - dp(8)
        if (y < dp(24)) y = loc[1] + anchor.height + dp(8) // not enough room above -> show below instead
        if (x < 0) x = dp(4)
        val screenW = resources.displayMetrics.widthPixels
        if (x + popupWidth > screenW) x = screenW - popupWidth - dp(4)
        toolOptionsParams.x = x
        toolOptionsParams.y = y
    }

    private fun hideToolOptions() {
        if (::toolOptionsWindow.isInitialized) toolOptionsWindow.visibility = View.GONE
    }

    private fun showToolOptionsDisc(anchor: View, tool: String) {
        toolOptionsWindow.removeAllViews()
        val pillWidth = dp(96); val pillHeight = dp(44)
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundedBg(14)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val thicknessBtn = ImageButton(this).apply {
            setImageBitmap(IconFactory.thicknessIcon(dp(24)))
            background = GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = dp(6).toFloat() }
            scaleType = ImageView.ScaleType.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(36)).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
            setOnClickListener { showThicknessPicker(anchor, tool) }
        }
        val colorBtn = View(this).apply {
            background = GradientDrawable().apply { setColor(drawingView.currentColor); shape = GradientDrawable.OVAL }
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) }
            setOnClickListener {
                hideToolOptions()
                colorPickerWindow.visibility = View.VISIBLE
                windowManager.updateViewLayout(colorPickerWindow, colorPickerParams)
            }
        }
        pill.addView(thicknessBtn)
        pill.addView(colorBtn)
        toolOptionsWindow.addView(pill)
        positionPopupNear(anchor, pillWidth, pillHeight)
        windowManager.updateViewLayout(toolOptionsWindow, toolOptionsParams)
        toolOptionsWindow.visibility = View.VISIBLE
    }

    private fun showThicknessPicker(anchor: View, tool: String) {
        toolOptionsWindow.removeAllViews()
        val current = toolThickness[tool] ?: 6f
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundedBg(14)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        for (i in thicknessLevels.indices) {
            val selected = thicknessLevels[i] == current
            val numBtn = TextView(this).apply {
                text = (i + 1).toString()
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                textSize = 12f
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(if (selected) "#3F7D78" else "#33FFFFFF"))
                    shape = GradientDrawable.OVAL
                }
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
                setOnClickListener {
                    toolThickness[tool] = thicknessLevels[i]
                    if (drawingView.currentTool == tool) drawingView.strokeWidth = thicknessLevels[i]
                    hideToolOptions()
                }
            }
            row.addView(numBtn)
        }
        toolOptionsWindow.addView(row)
        positionPopupNear(anchor, dp(28) * 5 + dp(8), dp(36))
        windowManager.updateViewLayout(toolOptionsWindow, toolOptionsParams)
        toolOptionsWindow.visibility = View.VISIBLE
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
        if (::radialMenuWindow.isInitialized) windowManager.removeView(radialMenuWindow)
        if (::toolOptionsWindow.isInitialized) windowManager.removeView(toolOptionsWindow)
    }
}
