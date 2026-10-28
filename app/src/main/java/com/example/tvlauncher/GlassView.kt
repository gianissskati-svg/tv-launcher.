package com.example.tvlauncher

import android.content.Context
import android.graphics.*
import android.widget.FrameLayout

class Glass(c: Context, private val scenery: Scenery, private val radius: Float) : FrameLayout(c) {
    private val bp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shape = Path()
    private var cache: Bitmap? = null

    init {
        setWillNotDraw(false)
        edge.style = Paint.Style.STROKE
    }

    fun refresh() {
        cache = null
        invalidate()
    }

    private fun blurred(): Bitmap? {
        val sw = scenery.width
        val sh = scenery.height
        if (sw <= 0 || sh <= 0) return null
        var b = cache
        if (b == null) {
            b = Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888)
            val cv = Canvas(b)
            cv.scale(64f / sw, 36f / sh)
            scenery.draw(cv)
            cache = b
        }
        return b
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val d = resources.displayMetrics.density
        shape.reset()
        shape.addRoundRect(RectF(0f, 0f, w, h), radius, radius, Path.Direction.CW)
        val bmp = blurred()
        cv.save()
        cv.clipPath(shape)
        if (bmp != null) {
            val loc = IntArray(2)
            getLocationInWindow(loc)
            cv.save()
            cv.translate(-loc[0].toFloat(), -loc[1].toFloat())
            cv.drawBitmap(bmp, null, RectF(0f, 0f, scenery.width.toFloat(), scenery.height.toFloat()), bp)
            cv.restore()
        }
        fill.shader = LinearGradient(0f, 0f, 0f, h, 0x40FFFFFF, 0x14FFFFFF, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, h, fill)
        cv.restore()
        edge.strokeWidth = 1.5f * d
        edge.shader = LinearGradient(0f, 0f, 0f, h, 0xA6FFFFFF.toInt(), 0x33FFFFFF, Shader.TileMode.CLAMP)
        cv.drawRoundRect(RectF(d, d, w - d, h - d), radius, radius, edge)
    }
}
