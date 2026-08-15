package com.reflow.screendraw

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class DrawingView(context: Context) : View(context) {

    enum class Tool { PEN, HIGHLIGHTER, ERASER, CIRCLE, SQUARE, LINE, ARROW }

    var currentTool = Tool.PEN
    var currentColor = Color.RED
    var strokeWidth = 6f

    private var bitmap: Bitmap? = null
    private var canvas: Canvas? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var isShapeTool = false
    private var previewBitmap: Bitmap? = null

    private val undoStack = ArrayList<Bitmap>()
    private var whiteboard = false

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

    fun toggleWhiteboard() {
        whiteboard = !whiteboard
        setBackgroundColor(if (whiteboard) Color.WHITE else Color.TRANSPARENT)
    }

    override fun onDraw(canvasView: Canvas) {
        super.onDraw(canvasView)
        (previewBitmap ?: bitmap)?.let { canvasView.drawBitmap(it, 0f, 0f, null) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                pushUndo()
                startX = x; startY = y; lastX = x; lastY = y
                isShapeTool = currentTool !in listOf(Tool.PEN, Tool.HIGHLIGHTER, Tool.ERASER)
                if (isShapeTool) previewBitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, true)
                else strokeSegment(x, y, x, y)
            }
            MotionEvent.ACTION_MOVE -> {
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
            MotionEvent.ACTION_UP -> {
                if (isShapeTool) {
                    drawShapeInto(canvas, startX, startY, x, y)
                    previewBitmap = null
                    invalidate()
                }
            }
        }
        return true
    }

    private fun applyPaintForTool() {
        paint.style = Paint.Style.STROKE
        paint.xfermode = null
        when (currentTool) {
            Tool.ERASER -> {
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                paint.strokeWidth = strokeWidth * 3
                paint.color = Color.TRANSPARENT
            }
            Tool.HIGHLIGHTER -> {
                paint.color = currentColor
                paint.alpha = 90
                paint.strokeWidth = strokeWidth * 4
            }
            else -> {
                paint.color = currentColor
                paint.alpha = 255
                paint.strokeWidth = strokeWidth
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
        when (currentTool) {
            Tool.LINE -> target.drawLine(x1, y1, x2, y2, paint)
            Tool.ARROW -> {
                target.drawLine(x1, y1, x2, y2, paint)
                drawArrowHead(target, x1, y1, x2, y2)
            }
            Tool.CIRCLE -> {
                val side = maxOf(right - left, bottom - top)
                target.drawOval(RectF(left, top, left + side, top + side), paint)
            }
            Tool.SQUARE -> {
                val side = maxOf(right - left, bottom - top)
                target.drawRect(RectF(left, top, left + side, top + side), paint)
            }
            else -> {}
        }
    }

    private fun drawArrowHead(target: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) {
        val angle = Math.atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
        val len = 30.0
        val spread = Math.toRadians(28.0)
        for (sign in listOf(1, -1)) {
            val a = angle + sign * spread
            val ex = x2 - (len * Math.cos(a)).toFloat()
            val ey = y2 - (len * Math.sin(a)).toFloat()
            target.drawLine(x2, y2, ex, ey, paint)
        }
    }

    /** Saves just the strokes layer (transparent PNG) to Pictures/ScreenDraw.
     * Android doesn't allow capturing the actual screen behind the overlay
     * without a MediaProjection consent flow, so this saves your annotation
     * layer only — not a composite with whatever app was behind it. */
    fun saveToGallery(context: Context) {
        val bmp = bitmap ?: return
        val filename = "screendraw_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".png"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ScreenDraw")
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        val out: OutputStream? = resolver.openOutputStream(uri)
        out?.use { stream -> bmp.compress(Bitmap.CompressFormat.PNG, 100, stream) }
    }
}
