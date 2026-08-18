package com.reflow.screendraw

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.os.Build
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class DrawingView(context: Context) : View(context) {

    var currentTool = "pen"
    var currentColor = Color.RED
    var strokeWidth = 6f

    private var bitmap: Bitmap? = null
    private var canvas: Canvas? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var startX = 0f; private var startY = 0f
    private var lastX = 0f; private var lastY = 0f
    private var isShapeTool = false
    private var previewBitmap: Bitmap? = null

    private val undoStack = ArrayList<Bitmap>()
    var currentBoardMode = "off" // off / white / black

    private val greenCandle = Color.parseColor("#26A65B")
    private val redCandle = Color.parseColor("#D93A2F")

    private var showIndicator = false
    private var indicatorX = 0f
    private var indicatorY = 0f
    private val indicatorOffset = 90f
    private var activePointerId = -1
    private var currentPressure = 1f
    private var currentToolType = MotionEvent.TOOL_TYPE_FINGER

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && bitmap == null) {
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            canvas = Canvas(bitmap!!)
        }
    }

    fun clear() {
        pushUndo()
        canvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        invalidate()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            bitmap = undoStack.removeAt(undoStack.size - 1)
            canvas = Canvas(bitmap!!)
            invalidate()
        }
    }

    private fun pushUndo() {
        bitmap?.let { undoStack.add(it.copy(Bitmap.Config.ARGB_8888, true)) }
        if (undoStack.size > 20) undoStack.removeAt(0)
    }

    fun setBoardMode(mode: String) {
        currentBoardMode = if (currentBoardMode == mode) "off" else mode
        setBackgroundColor(
            when (currentBoardMode) {
                "white" -> Color.WHITE
                "black" -> Color.parseColor("#121212")
                else -> Color.TRANSPARENT
            }
        )
    }

    override fun onDraw(canvasView: Canvas) {
        super.onDraw(canvasView)
        (previewBitmap ?: bitmap)?.let { canvasView.drawBitmap(it, 0f, 0f, null) }
        if (showIndicator) drawIndicator(canvasView)
    }

    private fun drawIndicator(canvasView: Canvas) {
        val ix = indicatorX
        val iy = indicatorY - indicatorOffset
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f
        p.color = Color.argb(120, 255, 255, 255)
        canvasView.drawLine(ix, iy, indicatorX, indicatorY, p)

        if (currentTool == "eraser") {
            val radius = (strokeWidth * 5 / 2).coerceIn(14f, 40f)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 3f
            p.color = Color.WHITE
            canvasView.drawCircle(ix, iy, radius, p)
            p.color = Color.argb(160, 0, 0, 0)
            p.strokeWidth = 1.5f
            canvasView.drawCircle(ix, iy, radius, p)
        } else {
            val radius = strokeWidth.coerceIn(8f, 26f)
            p.style = Paint.Style.FILL
            p.color = currentColor
            p.alpha = if (currentTool == "highlighter") 150 else 255
            canvasView.drawCircle(ix, iy, radius, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2f
            p.color = Color.WHITE
            canvasView.drawCircle(ix, iy, radius, p)
            p.color = Color.argb(160, 0, 0, 0)
            p.strokeWidth = 1f
            canvasView.drawCircle(ix, iy, radius + 1f, p)
        }
    }

    /** Palm rejection: only one pointer drives drawing at a time. Once a
     * stroke starts, any other simultaneous touch (a resting palm, a second
     * finger) is ignored entirely — it can't interrupt or redirect the
     * stroke. A stylus (if you're using one) always takes priority and will
     * take over even if a finger/palm touched first. Stylus strokes are also
     * pressure-sensitive: press harder for a thicker line. */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                currentToolType = event.getToolType(0)
                beginStroke(event.getX(0), event.getY(0), event.getPressure(0))
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val newIndex = event.actionIndex
                val isStylus = event.getToolType(newIndex) == MotionEvent.TOOL_TYPE_STYLUS
                val curIdx = event.findPointerIndex(activePointerId)
                val currentIsStylus = curIdx >= 0 && event.getToolType(curIdx) == MotionEvent.TOOL_TYPE_STYLUS
                if (isStylus && !currentIsStylus) {
                    previewBitmap = null
                    activePointerId = event.getPointerId(newIndex)
                    currentToolType = MotionEvent.TOOL_TYPE_STYLUS
                    beginStroke(event.getX(newIndex), event.getY(newIndex), event.getPressure(newIndex))
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val idx = event.findPointerIndex(activePointerId)
                if (idx < 0) return true
                moveStroke(event.getX(idx), event.getY(idx), event.getPressure(idx))
            }
            MotionEvent.ACTION_UP -> {
                val idx = event.findPointerIndex(activePointerId)
                val x = if (idx >= 0) event.getX(idx) else lastX
                val y = if (idx >= 0) event.getY(idx) else lastY
                endStroke(x, y)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val liftedIndex = event.actionIndex
                if (event.getPointerId(liftedIndex) == activePointerId) {
                    endStroke(event.getX(liftedIndex), event.getY(liftedIndex))
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                showIndicator = false
                previewBitmap = null
                activePointerId = -1
                invalidate()
            }
        }
        return true
    }

    /** S-Pen hovers above the screen before touching — show the preview
     * bubble during hover too, so you can see exactly where the tip is
     * aimed before you commit to the stroke. */
    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
            when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                    indicatorX = event.getX(0); indicatorY = event.getY(0)
                    showIndicator = true
                    invalidate()
                }
                MotionEvent.ACTION_HOVER_EXIT -> {
                    showIndicator = false
                    invalidate()
                }
            }
        }
        return super.onHoverEvent(event)
    }

    private fun beginStroke(x: Float, y: Float, pressure: Float) {
        indicatorX = x; indicatorY = y
        currentPressure = pressure
        pushUndo()
        startX = x; startY = y; lastX = x; lastY = y
        isShapeTool = currentTool !in listOf("pen", "highlighter", "eraser")
        showIndicator = true
        if (isShapeTool) previewBitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, true)
        else strokeSegment(x, y, x, y)
    }

    private fun moveStroke(x: Float, y: Float, pressure: Float) {
        indicatorX = x; indicatorY = y
        currentPressure = pressure
        showIndicator = true
        if (isShapeTool) {
            val preview = previewBitmap?.copy(Bitmap.Config.ARGB_8888, true)
            val pc = preview?.let { Canvas(it) }
            drawShapeInto(pc, startX, startY, x, y)
            previewBitmap = preview
            invalidate()
        } else {
            strokeSegment(lastX, lastY, x, y)
            lastX = x; lastY = y
        }
    }

    private fun endStroke(x: Float, y: Float) {
        showIndicator = false
        if (isShapeTool) {
            drawShapeInto(canvas, startX, startY, x, y)
            previewBitmap = null
        }
        activePointerId = -1
        invalidate()
    }

    private fun applyPaintForTool() {
        paint.style = Paint.Style.STROKE
        paint.xfermode = null
        val pressureFactor = if (currentToolType == MotionEvent.TOOL_TYPE_STYLUS)
            (0.4f + currentPressure * 1.3f).coerceIn(0.35f, 2f)
        else 1f
        when (currentTool) {
            "eraser" -> {
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                paint.strokeWidth = strokeWidth * 5
                paint.color = Color.TRANSPARENT
            }
            "highlighter" -> {
                paint.color = currentColor
                paint.alpha = 90
                paint.strokeWidth = strokeWidth * 4 * pressureFactor
            }
            else -> {
                paint.color = currentColor
                paint.alpha = 255
                paint.strokeWidth = strokeWidth * pressureFactor
            }
        }
    }

    private fun strokeSegment(x1: Float, y1: Float, x2: Float, y2: Float) {
        applyPaintForTool()
        canvas?.drawLine(x1, y1, x2, y2, paint)
        invalidate()
    }

    private fun drawShapeInto(target: Canvas?, x1: Float, y1: Float, x2: Float, y2: Float) {
        if (target == null) return
        applyPaintForTool()
        val left = minOf(x1, x2); val right = maxOf(x1, x2)
        val top = minOf(y1, y2); val bottom = maxOf(y1, y2)
        when {
            currentTool == "line" -> target.drawLine(x1, y1, x2, y2, paint)
            currentTool == "arrow" -> {
                target.drawLine(x1, y1, x2, y2, paint)
                drawArrowHead(target, x1, y1, x2, y2)
            }
            currentTool == "circle" -> {
                val side = maxOf(right - left, bottom - top)
                target.drawOval(RectF(left, top, left + side, top + side), paint)
            }
            currentTool == "square" -> {
                val side = maxOf(right - left, bottom - top)
                target.drawRect(RectF(left, top, left + side, top + side), paint)
            }
            currentTool == "rectangle" -> {
                target.drawRect(RectF(left, top, right, bottom), paint)
            }
            currentTool.startsWith("gc") || currentTool.startsWith("rc") -> {
                val isGreen = currentTool.startsWith("gc")
                val code = currentTool.substring(2)
                val rect = RectF(left, top, right, bottom)
                when (code) {
                    "nw" -> drawCandle(target, rect, isGreen, 0.62f, wick = false)
                    "doji" -> drawDoji(target, rect, isGreen)
                    else -> {
                        val ratios = floatArrayOf(0.78f, 0.52f, 0.28f, 0.08f)
                        val stage = (code.toIntOrNull() ?: 1) - 1
                        val ratio = ratios[stage.coerceIn(0, 3)]
                        drawCandle(target, rect, isGreen, ratio, wick = true)
                    }
                }
            }
        }
    }

    private fun drawArrowHead(target: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) {
        val angle = Math.atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
        val len = 30.0; val spread = Math.toRadians(28.0)
        for (sign in listOf(1, -1)) {
            val a = angle + sign * spread
            val ex = x2 - (len * Math.cos(a)).toFloat()
            val ey = y2 - (len * Math.sin(a)).toFloat()
            target.drawLine(x2, y2, ex, ey, paint)
        }
    }

    private fun drawCandle(target: Canvas, rect: RectF, isGreen: Boolean, ratio: Float, wick: Boolean) {
        val fill = if (isGreen) greenCandle else redCandle
        val wickX = rect.centerX()
        val bodyWidth = maxOf(20f, rect.width() * 0.55f)
        val bodyLeft = wickX - bodyWidth / 2
        val bodyHeight = maxOf(8f, rect.height() * ratio)
        val bodyTop = rect.top + (rect.height() - bodyHeight) / 2

        if (wick) {
            paint.style = Paint.Style.STROKE
            paint.color = darken(fill)
            paint.strokeWidth = maxOf(4f, strokeWidth / 2)
            target.drawLine(wickX, rect.top, wickX, rect.bottom, paint)
        }
        paint.style = Paint.Style.FILL; paint.color = fill
        target.drawRect(bodyLeft, bodyTop, bodyLeft + bodyWidth, bodyTop + bodyHeight, paint)
        paint.style = Paint.Style.STROKE; paint.color = darken(fill); paint.strokeWidth = 3f
        target.drawRect(bodyLeft, bodyTop, bodyLeft + bodyWidth, bodyTop + bodyHeight, paint)
    }

    private fun drawDoji(target: Canvas, rect: RectF, isGreen: Boolean) {
        val fill = if (isGreen) greenCandle else redCandle
        val wickX = rect.centerX()
        val bodyWidth = maxOf(20f, rect.width() * 0.55f)
        val wickWidth = maxOf(4f, strokeWidth / 2)

        paint.style = Paint.Style.STROKE
        paint.color = darken(fill); paint.strokeWidth = wickWidth
        target.drawLine(wickX, rect.top, wickX, rect.bottom, paint)

        // the doji's center line needs to stand out against whichever board is active —
        // near-black was invisible on the blackboard
        paint.color = if (currentBoardMode == "black") Color.parseColor("#F0F0F0") else Color.parseColor("#141414")
        paint.strokeWidth = wickWidth
        target.drawLine(wickX - bodyWidth / 2, rect.centerY(), wickX + bodyWidth / 2, rect.centerY(), paint)
    }

    private fun darken(color: Int): Int {
        val r = (Color.red(color) * 0.7f).toInt()
        val g = (Color.green(color) * 0.7f).toInt()
        val b = (Color.blue(color) * 0.7f).toInt()
        return Color.rgb(r, g, b)
    }

    fun saveToGallery(context: Context): Boolean {
        val bmp = bitmap ?: return false
        return try {
            val filename = "screendraw_" +
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".png"
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ScreenDraw")
                }
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            // BUG FIX: openOutputStream can return null (e.g. if the resolver/URI is invalid);
            // the old code used `out?.use{}` which silently skipped writing but still fell
            // through to `return true` below — a false "success" with nothing actually saved.
            val out: OutputStream = resolver.openOutputStream(uri) ?: return false
            out.use { stream -> bmp.compress(Bitmap.CompressFormat.PNG, 100, stream) }
            true
        } catch (e: Exception) {
            false
        }
    }
}
