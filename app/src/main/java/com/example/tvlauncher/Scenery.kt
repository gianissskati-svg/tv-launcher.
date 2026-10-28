package com.example.tvlauncher

import android.content.Context
import android.graphics.*
import android.view.View

class Wall(
    val name: String, val sky: IntArray, val glow: Int, val gx: Float, val gy: Float,
    val peaks: IntArray, val shift: Double, val stars: Boolean
)

val WALLS = listOf(
    Wall(
        "Dusk", intArrayOf(0xFF1B2A49.toInt(), 0xFF6B6FA8.toInt(), 0xFFF2A97B.toInt()),
        0x66FFE2B8, 0.72f, 0.55f,
        intArrayOf(0xFF4A4A7A.toInt(), 0xFF35365F.toInt(), 0xFF1F2140.toInt()), 0.0, false
    ),
    Wall(
        "Night", intArrayOf(0xFF05070F.toInt(), 0xFF0E1A35.toInt(), 0xFF24385F.toInt()),
        0x4D9DB8FF, 0.25f, 0.30f,
        intArrayOf(0xFF1A2542.toInt(), 0xFF111A33.toInt(), 0xFF0A1022.toInt()), 1.7, true
    ),
    Wall(
        "Forest", intArrayOf(0xFF2C5364.toInt(), 0xFF5B8C85.toInt(), 0xFFE9D8A6.toInt()),
        0x55FFF3C4, 0.30f, 0.62f,
        intArrayOf(0xFF3E6B5B.toInt(), 0xFF2B4F43.toInt(), 0xFF173328.toInt()), 3.1, false
    ),
    Wall(
        "Ocean", intArrayOf(0xFF0B3C5D.toInt(), 0xFF3A8FB7.toInt(), 0xFFF4D6A0.toInt()),
        0x55FFE0A0, 0.65f, 0.60f,
        intArrayOf(0xFF2A6F97.toInt(), 0xFF1D5578.toInt(), 0xFF0F3552.toInt()), 4.6, false
    )
)

class Scenery(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    var wall: Wall = WALLS[0]
        set(v) {
            field = v
            invalidate()
        }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.shader = LinearGradient(0f, 0f, 0f, h, wall.sky, floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, h, p)
        p.shader = RadialGradient(
            w * wall.gx, h * wall.gy, h * 0.4f, wall.glow, wall.glow and 0x00FFFFFF, Shader.TileMode.CLAMP
        )
        cv.drawRect(0f, 0f, w, h, p)
        p.shader = null
        if (wall.stars) {
            val r = java.util.Random(7)
            p.color = 0xCCFFFFFF.toInt()
            for (i in 0 until 90) {
                cv.drawCircle(r.nextFloat() * w, r.nextFloat() * h * 0.55f, 1f + r.nextFloat() * 2f, p)
            }
        }
        val bases = floatArrayOf(0.62f, 0.72f, 0.82f)
        val freqs = floatArrayOf(1.0f, 1.7f, 2.6f)
        for (i in 0..2) {
            p.color = wall.peaks[i]
            val path = Path()
            path.moveTo(0f, h)
            var x = 0f
            while (x <= w + 20f) {
                val y = h * bases[i] - (Math.sin(x / w * 6.0 * freqs[i] + freqs[i] * 2.0 + wall.shift) * h * 0.05 +
                        Math.sin(x / w * 13.0 * freqs[i]) * h * 0.02).toFloat()
                path.lineTo(x, y)
                x += 20f
            }
            path.lineTo(w, h)
            path.close()
            cv.drawPath(path, p)
        }
    }
}
