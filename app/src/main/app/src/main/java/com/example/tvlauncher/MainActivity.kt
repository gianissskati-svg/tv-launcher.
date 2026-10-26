package com.example.tvlauncher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Scenic wallpaper drawn in code: sky gradient, sun glow, 3 mountain layers
class Scenery(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(0xFF1B2A49.toInt(), 0xFF6B6FA8.toInt(), 0xFFF2A97B.toInt()),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
        cv.drawRect(0f, 0f, w, h, p)
        p.shader = RadialGradient(w * 0.72f, h * 0.55f, h * 0.35f, 0x66FFE2B8, 0x00FFE2B8, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, h, p)
        p.shader = null
        val layers = listOf(
            Triple(0.62f, 0xFF4A4A7A.toInt(), 1.0f),
            Triple(0.72f, 0xFF35365F.toInt(), 1.7f),
            Triple(0.82f, 0xFF1F2140.toInt(), 2.6f)
        )
        for ((base, color, freq) in layers) {
            p.color = color
            val path = Path()
            path.moveTo(0f, h)
            var x = 0f
            while (x <= w + 20f) {
                val y = h * base - (Math.sin(x / w * 6.0 * freq + freq * 2) * h * 0.05 +
                        Math.sin(x / w * 13.0 * freq) * h * 0.02).toFloat()
                path.lineTo(x, y)
                x += 20f
            }
            path.lineTo(w, h)
            path.close()
            cv.drawPath(path, p)
        }
    }
}

class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var dock: HorizontalScrollView
    private lateinit var row: LinearLayout
    private lateinit var overlay: FrameLayout
    private val soft = 0xE6FFFFFF.toInt()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun dpf(v: Int) = v * resources.displayMetrics.density

    private fun glass(radius: Float, oval: Boolean = false) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x4DFFFFFF, 0x1AFFFFFF)
    ).apply {
        if (oval) shape = GradientDrawable.OVAL else cornerRadius = radius
        setStroke(dp(1), 0x66FFFFFF)
    }

    private fun focusFx(v: View, radius: Float) {
        v.isFocusable = true
        v.setOnFocusChangeListener { _, f ->
            val s = if (f) 1.05f else 1f
            v.animate().scaleX(s).scaleY(s).translationZ(if (f) dpf(6) else 0f).setDuration(200).start()
            v.foreground = if (f) GradientDrawable().apply {
                cornerRadius = radius
                setStroke(dp(2), soft)
            } else null
        }
    }

    private fun query(cat: String): List<ResolveInfo> =
        packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)
            .filter { it.activityInfo.packageName != packageName }

    private fun label(ri: ResolveInfo) = ri.loadLabel(packageManager).toString()

    private fun launch(ri: ResolveInfo) {
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .setClassName(ri.activityInfo.packageName, ri.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
        }
    }

    private fun tile(ri: ResolveInfo): FrameLayout {
        val pm = packageManager
        val fl = FrameLayout(this)
        val iv = ImageView(this)
        val banner = ri.activityInfo.loadBanner(pm)
        if (banner != null) {
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.setImageDrawable(banner)
        } else {
            fl.setBackgroundColor(0xFF232838.toInt())
            iv.scaleType = ImageView.ScaleType.CENTER_INSIDE
            iv.setPadding(dp(20), dp(10), dp(20), dp(10))
            iv.setImageDrawable(ri.loadIcon(pm))
        }
        fl.addView(iv, FrameLayout.LayoutParams(-1, -1))
        fl.clipToOutline = true
        fl.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dpf(14))
            }
        }
        focusFx(fl, dpf(14))
        fl.setOnClickListener { launch(ri) }
        return fl
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        root = FrameLayout(this)
        root.addView(Scenery(this), FrameLayout.LayoutParams(-1, -1))

        // bottom shading for readability
        val scrim = View(this)
        scrim.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x00000000, 0x66000000)
        )
        root.addView(scrim, FrameLayout.LayoutParams(-1, dp(300), Gravity.BOTTOM))

        // upper left: date (weather comes later)
        val date = TextView(this)
        date.text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())
        date.setTextColor(soft)
        date.textSize = 20f
        root.addView(date, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            setMargins(dp(64), dp(40), 0, 0)
        })

        // upper right: clock + circular glass settings button
        val header = LinearLayout(this)
        header.gravity = Gravity.CENTER_VERTICAL
        val clock = TextClock(this)
        clock.format24Hour = "HH:mm"
        clock.format12Hour = "HH:mm"
        clock.textSize = 30f
        clock.setTextColor(soft)
        clock.fontFeatureSettings = "tnum"
        clock.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        header.addView(clock)
        val gear = ImageView(this)
        gear.setImageResource(android.R.drawable.ic_menu_preferences)
        gear.setColorFilter(soft)
        gear.setPadding(dp(12), dp(12), dp(12), dp(12))
        gear.background = glass(0f, true)
        focusFx(gear, dpf(26))
        gear.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
            }
        }
        header.addView(gear, LinearLayout.LayoutParams(dp(52), dp(52)).apply { setMargins(dp(20), 0, 0, 0) })
        root.addView(header, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
            setMargins(0, dp(28), dp(64), 0)
        })

        // glass dock
        val tv = query(Intent.CATEGORY_LEANBACK_LAUNCHER).sortedBy { label(it).lowercase() }
        val all = (tv + query(Intent.CATEGORY_LAUNCHER))
            .distinctBy { it.activityInfo.packageName + "/" + it.activityInfo.name }
            .sortedBy { label(it).lowercase() }

        row = LinearLayout(this)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(18), dp(18), dp(18), dp(18))
        fun lp() = LinearLayout.LayoutParams(dp(144), dp(84)).apply { setMargins(0, 0, dp(16), 0) }
        tv.forEach { row.addView(tile(it), lp()) }
        val more = TextView(this)
        more.text = "All apps"
        more.gravity = Gravity.CENTER
        more.setTextColor(soft)
        more.textSize = 18f
        more.background = glass(dpf(14))
        focusFx(more, dpf(14))
        more.setOnClickListener { showAll(all) }
        row.addView(more, lp())

        dock = HorizontalScrollView(this)
        dock.isHorizontalScrollBarEnabled = false
        dock.clipToPadding = false
        dock.background = glass(dpf(28))
        dock.addView(row)
        root.addView(dock, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(64), 0, dp(64), dp(56))
        })

        overlay = FrameLayout(this)
        overlay.setBackgroundColor(0xE60B0F1E.toInt())
        overlay.visibility = View.GONE
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))

        setContentView(root)
        root.post { row.getChildAt(0)?.requestFocus() }
    }

    private fun showAll(all: List<ResolveInfo>) {
        overlay.removeAllViews()
        val grid = GridLayout(this)
        grid.columnCount = 5
        all.forEach { ri ->
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.addView(tile(ri), LinearLayout.LayoutParams(dp(144), dp(84)))
            val name = TextView(this)
            name.text = label(ri)
            name.setTextColor(soft)
            name.textSize = 14f
            name.maxLines = 1
            name.ellipsize = TextUtils.TruncateAt.END
            name.gravity = Gravity.CENTER
            name.setPadding(0, dp(8), 0, 0)
            cell.addView(name, LinearLayout.LayoutParams(dp(144), -2))
            grid.addView(cell, GridLayout.LayoutParams().apply { setMargins(dp(8), dp(8), dp(8), dp(14)) })
        }
        val sv = ScrollView(this)
        sv.clipToPadding = false
        sv.setPadding(dp(56), dp(104), dp(56), dp(48))
        sv.addView(grid)
        overlay.addView(sv, FrameLayout.LayoutParams(-1, -1))
        val title = TextView(this)
        title.text = "All apps"
        title.setTextColor(soft)
        title.textSize = 28f
        overlay.addView(title, FrameLayout.LayoutParams(-2, -2).apply { setMargins(dp(64), dp(36), 0, 0) })
        overlay.alpha = 0f
        overlay.visibility = View.VISIBLE
        overlay.animate().alpha(1f).setDuration(250).start()
        ((grid.getChildAt(0) as? ViewGroup)?.getChildAt(0))?.requestFocus()
    }

    private fun hideAll() {
        overlay.animate().alpha(0f).setDuration(200).withEndAction {
            overlay.visibility = View.GONE
        }.start()
        row.getChildAt(row.childCount - 1)?.requestFocus()
    }

    override fun onBackPressed() {
        if (overlay.visibility == View.VISIBLE) hideAll()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (overlay.visibility == View.VISIBLE) hideAll()
        dock.smoothScrollTo(0, 0)
        row.getChildAt(0)?.requestFocus()
    }
}
