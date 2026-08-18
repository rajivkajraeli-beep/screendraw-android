package com.reflow.screendraw

import android.graphics.*

object IconFactory {

    private fun newBitmap(size: Int): Pair<Bitmap, Canvas> {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        return Pair(bmp, Canvas(bmp))
    }

    private val ink = Color.parseColor("#F6F3EA")

    fun pen(size: Int): Bitmap {
        val (bmp, c) = newBitmap(size)
        c.save()
        c.translate(size / 2f, size / 2f)
        c.rotate(-40f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val L = size * 0.86f
        val w = size * 0.16f
        val eraserLen = L * 0.16f
        val ferruleLen = L * 0.08f
        val pointLen = L * 0.24f
        val woodLen = L - eraserLen - ferruleLen - pointLen
        val x0 = -L / 2f

        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#E996A0")
        c.drawRoundRect(RectF(x0, -w / 2, x0 + eraserLen, w / 2), 4f, 4f, p)

        val x1 = x0 + eraserLen
        p.color = Color.parseColor("#CDCDD2")
        c.drawRect(RectF(x1, -w / 2, x1 + ferruleLen, w / 2), p)

        val x2 = x1 + ferruleLen
        p.color = Color.parseColor("#FAC748")
        c.drawRect(RectF(x2, -w / 2, x2 + woodLen, w / 2), p)

        val x3 = x2 + woodLen
        val coneLen = pointLen * 0.55f
        val path1 = Path()
        path1.moveTo(x3, -w / 2); path1.lineTo(x3, w / 2); path1.lineTo(x3 + coneLen, 0f); path1.close()
        p.color = Color.parseColor("#E8C796")
        c.drawPath(path1, p)

        val x4 = x3 + coneLen
        val tipLen = pointLen - coneLen
        val tipW = w * 0.3f
        val path2 = Path()
        path2.moveTo(x4, -tipW / 2); path2.lineTo(x4, tipW / 2); path2.lineTo(x4 + tipLen, 0f); path2.close()
        p.color = Color.parseColor("#2D2D2D")
        c.drawPath(path2, p)

        c.restore()
        return bmp
    }

    fun highlighter(size: Int): Bitmap {
        val (bmp, c) = newBitmap(size)
        c.save(); c.translate(size / 2f, size / 2f); c.rotate(-40f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG); p.style = Paint.Style.FILL
        val bodyLen = size * 0.55f; val w = size * 0.3f
        p.color = ink
        c.drawRoundRect(RectF(-bodyLen / 2, -w / 2, bodyLen / 2, w / 2), 3f, 3f, p)
        p.color = Color.parseColor("#FFD60A")
        c.drawRect(RectF(-bodyLen / 2 - size * 0.15f, -w / 2, -bodyLen / 2, w / 2), p)
        c.restore()
        return bmp
    }

    fun eraser(size: Int): Bitmap {
        val (bmp, c) = newBitmap(size)
        c.save(); c.translate(size / 2f, size / 2f); c.rotate(-30f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG); p.style = Paint.Style.FILL
        val w2 = size * 0.55f; val h2 = size * 0.32f
        p.color = Color.parseColor("#F0969A")
        c.drawRoundRect(RectF(-w2 / 2, -h2 / 2, w2 / 2, h2 / 2), 4f, 4f, p)
        p.color = ink
        c.drawRoundRect(RectF(-w2 / 2, -h2 / 2, -w2 / 2 + w2 * 0.45f, h2 / 2), 4f, 4f, p)
        c.restore()
        return bmp
    }

    fun circleIcon(size: Int): Bitmap {
        val (bmp, c) = newBitmap(size)
        val p = Paint(Paint.ANTI_ALIAS_FLAG); p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f; p.color = ink
        c.drawOval(RectF(size * 0.18f, size * 0.18f, size * 0.82f, size * 0.82f), p)
        return bmp
    }

    fun squareIcon(size: Int): Bitmap {
        val (bmp, c) = newBitmap(size)
        val p = Paint(Paint.ANTI_ALIAS_FLAG); p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f; p.color = ink
        c.drawRoundRect(RectF(size * 0.18f, size * 0.18f, size * 0.82f, size * 0.82f), 3f, 3f, p)
        return bmp
    }

    fun rectangleIcon(size: Int): Bitmap {
        val (bmp, c) = newBitmap(size)
        val p = Paint(Paint.ANTI_ALIAS_FLAG); p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f; p.color = ink
        c.drawRoundRect(RectF(size * 0.12f, size * 0.3f, size * 0.88f, size * 0.7f), 3f, 3f, p)
        return bmp
    }

    fun toolboxIcon(size: Int): Bitmap {
        val (bmp, c) = newBitmap(size)
        val p = Paint(Paint.ANTI_ALIAS_FLAG); p.style = Paint.Style.STROKE; p.strokeWidth = 2.2f; p.color = ink
        val gap = size * 0.14f
        val cellSize = size * 0.32f
        val positions = listOf(
            Pair(size * 0.18f, size * 0.18f),
            Pair(size * 0.18f + cellSize + gap, size * 0.18f),
            Pair(size * 0.18f, size * 0.18f + cellSize + gap),
            Pair(size * 0.18f + cellSize + gap, size * 0.18f + cellSize + gap)
        )
        for ((x, y) in positions) c.drawRect(x, y, x + cellSize, y + cellSize, p)
        return bmp
    }

    fun paletteIcon(size: Int): Bitmap {
        val (bmp, c) = newBitmap(size)
        val colors = intArrayOf(
            Color.parseColor("#FF3B30"), Color.parseColor("#FFD60A"),
            Color.parseColor("#34C759"), Color.parseColor("#0A84FF")
        )
        val r = size * 0.19f
        val positions = listOf(
            Pair(size * 0.28f, size * 0.28f), Pair(size * 0.72f, size * 0.28f),
            Pair(size * 0.28f, size * 0.72f), Pair(size * 0.72f, size * 0.72f)
        )
        val p2 = Paint(Paint.ANTI_ALIAS_FLAG); p2.style = Paint.Style.FILL
        positions.forEachIndexed { i, (x, y) -> p2.color = colors[i]; c.drawCircle(x, y, r, p2) }
        return bmp
    }

    fun lineIcon(size: Int): Bitmap {
        val (bmp, c) = newBitmap(size)
        val p = Paint(Paint.ANTI_ALIAS_FLAG); p.strokeWidth = 2.5f; p.color = ink; p.strokeCap = Paint.Cap.ROUND
        c.drawLine(size * 0.2f, size * 0.8f, size * 0.8f, size * 0.2f, p)
        return bmp
    }

    fun arrowIcon(size: Int): Bitmap {
        val (bmp, c) = newBitmap(size)
        val p = Paint(Paint.ANTI_ALIAS_FLAG); p.strokeWidth = 2.5f; p.color = ink; p.strokeCap = Paint.Cap.ROUND
        val x1 = size * 0.2f; val y1 = size * 0.8f; val x2 = size * 0.82f; val y2 = size * 0.18f
        c.drawLine(x1, y1, x2, y2, p)
        val angle = Math.atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
        val len = size * 0.28; val spread = Math.toRadians(28.0)
        for (sign in listOf(1, -1)) {
            val a = angle + sign * spread
            val ex = x2 - (len * Math.cos(a)).toFloat()
            val ey = y2 - (len * Math.sin(a)).toFloat()
            c.drawLine(x2, y2, ex, ey, p)
        }
        return bmp
    }

    fun thicknessIcon(size: Int): Bitmap {
        val (bmp, c) = newBitmap(size)
        val p = Paint(Paint.ANTI_ALIAS_FLAG); p.style = Paint.Style.STROKE; p.color = ink; p.strokeCap = Paint.Cap.ROUND
        val widths = floatArrayOf(1.5f, 2.5f, 4f)
        val ys = floatArrayOf(size * 0.3f, size * 0.52f, size * 0.76f)
        for (i in 0..2) {
            p.strokeWidth = widths[i]
            c.drawLine(size * 0.18f, ys[i], size * 0.82f, ys[i], p)
        }
        return bmp
    }

    fun undoIcon(size: Int): Bitmap {
        val (bmp, c) = newBitmap(size)
        val p = Paint(Paint.ANTI_ALIAS_FLAG); p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f; p.color = ink
        c.drawArc(RectF(size * 0.2f, size * 0.2f, size * 0.8f, size * 0.8f), 40f, 260f, false, p)
        val path = Path()
        path.moveTo(size * 0.22f, size * 0.32f); path.lineTo(size * 0.14f, size * 0.42f); path.lineTo(size * 0.32f, size * 0.46f)
        path.close()
        p.style = Paint.Style.FILL
        c.drawPath(path, p)
        return bmp
    }

    fun clearIcon(size: Int): Bitmap {
        val (bmp, c) = newBitmap(size)
        val p = Paint(Paint.ANTI_ALIAS_FLAG); p.style = Paint.Style.STROKE; p.strokeWidth = 2.2f; p.color = ink
        c.drawRoundRect(RectF(size * 0.28f, size * 0.32f, size * 0.72f, size * 0.85f), 2f, 2f, p)
        c.drawLine(size * 0.2f, size * 0.28f, size * 0.8f, size * 0.28f, p)
        c.drawLine(size * 0.4f, size * 0.15f, size * 0.6f, size * 0.15f, p)
        return bmp
    }

    fun whiteboardIcon(size: Int): Bitmap {
        val (bmp, c) = newBitmap(size)
        val p = Paint(Paint.ANTI_ALIAS_FLAG); p.style = Paint.Style.FILL; p.color = Color.WHITE
        c.drawRoundRect(RectF(size * 0.15f, size * 0.2f, size * 0.85f, size * 0.8f), 3f, 3f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = ink
        c.drawRoundRect(RectF(size * 0.15f, size * 0.2f, size * 0.85f, size * 0.8f), 3f, 3f, p)
        return bmp
    }

    fun blackboardIcon(size: Int): Bitmap {
        val (bmp, c) = newBitmap(size)
        val p = Paint(Paint.ANTI_ALIAS_FLAG); p.style = Paint.Style.FILL; p.color = Color.parseColor("#1A1A1A")
        c.drawRoundRect(RectF(size * 0.15f, size * 0.2f, size * 0.85f, size * 0.8f), 3f, 3f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = ink
        c.drawRoundRect(RectF(size * 0.15f, size * 0.2f, size * 0.85f, size * 0.8f), 3f, 3f, p)
        return bmp
    }

    fun saveIcon(size: Int): Bitmap {
        val (bmp, c) = newBitmap(size)
        val p = Paint(Paint.ANTI_ALIAS_FLAG); p.style = Paint.Style.STROKE; p.strokeWidth = 2.2f; p.color = ink
        c.drawRoundRect(RectF(size * 0.2f, size * 0.18f, size * 0.8f, size * 0.82f), 3f, 3f, p)
        c.drawRect(RectF(size * 0.32f, size * 0.18f, size * 0.68f, size * 0.38f), p)
        return bmp
    }

    fun quitIcon(size: Int): Bitmap {
        val (bmp, c) = newBitmap(size)
        val p = Paint(Paint.ANTI_ALIAS_FLAG); p.strokeWidth = 2.5f; p.color = Color.parseColor("#E06A5A"); p.strokeCap = Paint.Cap.ROUND
        c.drawLine(size * 0.28f, size * 0.28f, size * 0.72f, size * 0.72f, p)
        c.drawLine(size * 0.72f, size * 0.28f, size * 0.28f, size * 0.72f, p)
        return bmp
    }

    fun expandIcon(size: Int): Bitmap {
        val (bmp, c) = newBitmap(size)
        val p = Paint(Paint.ANTI_ALIAS_FLAG); p.style = Paint.Style.STROKE; p.strokeWidth = 2.2f; p.color = ink
        c.drawRoundRect(RectF(size * 0.18f, size * 0.18f, size * 0.82f, size * 0.82f), 2f, 2f, p)
        p.strokeCap = Paint.Cap.ROUND
        c.drawLine(size * 0.34f, size * 0.34f, size * 0.66f, size * 0.66f, p)
        return bmp
    }

    fun orientationIcon(size: Int): Bitmap {
        val (bmp, c) = newBitmap(size)
        val p = Paint(Paint.ANTI_ALIAS_FLAG); p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = ink; p.strokeCap = Paint.Cap.ROUND
        c.drawRoundRect(RectF(size * 0.12f, size * 0.35f, size * 0.88f, size * 0.65f), 3f, 3f, p)
        c.drawLine(size * 0.5f, size * 0.15f, size * 0.5f, size * 0.85f, p)
        return bmp
    }

    fun candle(size: Int, isGreen: Boolean, ratio: Float, wick: Boolean = true, lineBody: Boolean = false): Bitmap {
        val (bmp, c) = newBitmap(size)
        val fill = if (isGreen) Color.parseColor("#26A65B") else Color.parseColor("#D93A2F")
        val darker = darken(fill)
        val cx = size / 2f
        val top = size * 0.12f; val bottom = size * 0.88f
        val bodyW = size * 0.46f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        if (wick) {
            p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = darker
            c.drawLine(cx, top, cx, bottom, p)
        }
        if (lineBody) {
            p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = Color.parseColor("#E8E8E8")
            c.drawLine(cx - bodyW / 2, size / 2f, cx + bodyW / 2, size / 2f, p)
        } else {
            val bodyH = (bottom - top) * ratio
            val bodyTop = top + ((bottom - top) - bodyH) / 2
            p.style = Paint.Style.FILL; p.color = fill
            c.drawRect(cx - bodyW / 2, bodyTop, cx + bodyW / 2, bodyTop + bodyH, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f; p.color = darker
            c.drawRect(cx - bodyW / 2, bodyTop, cx + bodyW / 2, bodyTop + bodyH, p)
        }
        return bmp
    }

    private fun darken(color: Int, factor: Float = 0.7f): Int {
        val r = (Color.red(color) * factor).toInt()
        val g = (Color.green(color) * factor).toInt()
        val b = (Color.blue(color) * factor).toInt()
        return Color.rgb(r, g, b)
    }
}
