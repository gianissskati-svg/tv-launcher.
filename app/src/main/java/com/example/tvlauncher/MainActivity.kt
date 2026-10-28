package com.example.tvlauncher

import android.app.Activity
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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var scenery: Scenery
    private lateinit var weatherTemp: TextView
    private lateinit var weatherInfo: TextView
    private lateinit var clock: TextClock
    private lateinit var gear: ImageView
    private lateinit var dock: HorizontalScrollView
    private lateinit var row: LinearLayout
    private lateinit var overlay: FrameLayout
    private lateinit var panel: ScrollView
    private lateinit var panelList: LinearLayout
    private lateinit var wallRow: TextView
    private lateinit var clockRow: TextView
    private val soft = 0xE6FFFFFF.toInt()
    private var allApps = listOf<ResolveInfo>()
    private var panelOpen = false
    private var panelReturn: View? = null
    private var screenReturn: View? = null
    private var screenKind = ""
    private var lastWeather = 0L
    private var h24 = true
    private val prefs by lazy { getSharedPreferences("glass", MODE_PRIVATE) }

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
    private fun key(ri: ResolveInfo) = ri.activityInfo.packageName + "/" + ri.activityInfo.name

    private fun favSet(): HashSet<String> =
        HashSet<String>(prefs.getStringSet("fav", null) ?: emptySet<String>())

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

    private fun toggleFav(ri: ResolveInfo) {
        val set = favSet()
        val k = key(ri)
        val added = set.add(k)
        if (!added) set.remove(k)
        prefs.edit().putStringSet("fav", set).apply()
        Toast.makeText(this, if (added) "Saved to My Space" else "Removed from My Space", Toast.LENGTH_SHORT).show()
        if (overlay.visibility == View.VISIBLE && screenKind == "myspace") showMySpace(screenReturn)
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
        fl.setOnLongClickListener {
            toggleFav(ri)
            true
        }
        return fl
    }

    private fun panelRow(text: String, onClick: () -> Unit): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 18f
        t.setTextColor(soft)
        t.gravity = Gravity.CENTER_VERTICAL
        t.setPadding(dp(24), 0, dp(24), 0)
        t.background = glass(dpf(18))
        focusFx(t, dpf(18))
        t.setOnClickListener { onClick() }
        return t
    }

    private fun applyClock() {
        val f = if (h24) "HH:mm" else "h:mm a"
        clock.format24Hour = f
        clock.format12Hour = f
    }

    private fun refreshRows() {
        wallRow.text = "Wallpaper: " + WALLS[prefs.getInt("wp", 0) % WALLS.size].name
        clockRow.text = "Clock: " + (if (h24) "24-hour" else "12-hour")
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        h24 = prefs.getBoolean("h24", true)

        val root = FrameLayout(this)
        scenery = Scenery(this)
        scenery.wall = WALLS[prefs.getInt("wp", 0) % WALLS.size]
        root.addView(scenery, FrameLayout.LayoutParams(-1, -1))

        val scrim = View(this)
        scrim.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x00000000, 0x66000000)
        )
        root.addView(scrim, FrameLayout.LayoutParams(-1, dp(300), Gravity.BOTTOM))

        val info = LinearLayout(this)
        info.orientation = LinearLayout.VERTICAL
        weatherTemp = TextView(this)
        weatherTemp.textSize = 44f
        weatherTemp.setTextColor(soft)
        weatherTemp.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        weatherInfo = TextView(this)
        weatherInfo.textSize = 16f
        weatherInfo.setTextColor(soft)
        val dateTv = TextView(this)
        dateTv.text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())
        dateTv.textSize = 16f
        dateTv.setTextColor(0xB3FFFFFF.toInt())
        info.addView(weatherTemp)
        info.addView(weatherInfo)
        info.addView(dateTv)
        root.addView(info, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            setMargins(dp(64), dp(28), 0, 0)
        })

        val header = LinearLayout(this)
        header.gravity = Gravity.CENTER_VERTICAL
        clock = TextClock(this)
        clock.textSize = 30f
        clock.setTextColor(soft)
        clock.fontFeatureSettings = "tnum"
        clock.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        applyClock()
        header.addView(clock)
        gear = ImageView(this)
        gear.setImageResource(android.R.drawable.ic_menu_preferences)
        gear.setColorFilter(soft)
        gear.setPadding(dp(12), dp(12), dp(12), dp(12))
        gear.background = glass(0f, true)
        focusFx(gear, dpf(26))
        gear.setOnClickListener { openPanel() }
        header.addView(gear, LinearLayout.LayoutParams(dp(52), dp(52)).apply { setMargins(dp(20), 0, 0, 0) })
        root.addView(header, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
            setMargins(0, dp(28), dp(64), 0)
        })

        val tv = query(Intent.CATEGORY_LEANBACK_LAUNCHER).sortedBy { label(it).lowercase() }
        allApps = (tv + query(Intent.CATEGORY_LAUNCHER))
            .distinctBy { key(it) }
            .sortedBy { label(it).lowercase() }

        row = LinearLayout(this)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(18), dp(18), dp(18), dp(18))
        fun lp() = LinearLayout.LayoutParams(dp(144), dp(84)).apply { setMargins(0, 0, dp(16), 0) }
        tv.forEach { row.addView(tile(it), lp()) }
        val more = TextView(this)
        more.text = "Apps"
        more.gravity = Gravity.CENTER
        more.setTextColor(soft)
        more.textSize = 18f
        more.background = glass(dpf(14))
        focusFx(more, dpf(14))
        more.setOnClickListener { showScreen("apps", "Apps", allApps, "No apps found", currentFocus) }
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

        panelList = LinearLayout(this)
        panelList.orientation = LinearLayout.VERTICAL
        panelList.setPadding(dp(28), dp(28), dp(28), dp(28))
        val head = TextView(this)
        head.text = "Settings"
        head.textSize = 28f
        head.setTextColor(soft)
        head.setPadding(dp(4), 0, 0, dp(20))
        panelList.addView(head)
        fun add(v: View) = panelList.addView(v, LinearLayout.LayoutParams(-1, dp(56)).apply {
            setMargins(0, 0, 0, dp(10))
        })
        add(panelRow("System settings") {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
            }
        })
        add(panelRow("Home") {
            closePanel(false)
            row.getChildAt(0)?.requestFocus()
        })
        add(panelRow("Apps") {
            closePanel(false)
            showScreen("apps", "Apps", allApps, "No apps found", panelReturn)
        })
        add(panelRow("My Space") {
            closePanel(false)
            showMySpace(panelReturn)
        })
        add(panelRow("Search") {
            closePanel(true)
            try {
                startActivity(Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                Toast.makeText(this, "Search is not available", Toast.LENGTH_SHORT).show()
            }
        })
        wallRow = panelRow("") {
            val idx = (prefs.getInt("wp", 0) + 1) % WALLS.size
            prefs.edit().putInt("wp", idx).apply()
            scenery.wall = WALLS[idx]
            refreshRows()
        }
        add(wallRow)
        clockRow = panelRow("") {
            h24 = !h24
            prefs.edit().putBoolean("h24", h24).apply()
            applyClock()
            refreshRows()
        }
        add(clockRow)
        refreshRows()

        panel = ScrollView(this)
        panel.addView(panelList)
        panel.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xE6141A2E.toInt(), 0xE60B0F1E.toInt())
        ).apply {
            cornerRadius = dpf(32)
            setStroke(dp(1), 0x66FFFFFF)
        }
        panel.clipToOutline = true
        panel.visibility = View.GONE
        panel.translationX = dpf(460)
        root.addView(panel, FrameLayout.LayoutParams(dp(420), -1, Gravity.END).apply {
            setMargins(0, dp(24), dp(24), dp(24))
        })

        setContentView(root)
        root.post { row.getChildAt(0)?.requestFocus() }
    }

    override fun onResume() {
        super.onResume()
        if (System.currentTimeMillis() - lastWeather > 20 * 60 * 1000) {
            lastWeather = System.currentTimeMillis()
            loadWeather()
        }
    }

    private fun httpGet(u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        return try {
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun describe(c: Int) = when (c) {
        0 -> "Clear"
        1, 2, 3 -> "Partly cloudy"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67, in 80..82 -> "Rain"
        in 71..77, 85, 86 -> "Snow"
        95, 96, 99 -> "Thunderstorm"
        else -> "Cloudy"
    }

    private fun loadWeather() {
        Thread {
            try {
                val loc = JSONObject(httpGet("https://ipwho.is/"))
                val lat = loc.getDouble("latitude")
                val lon = loc.getDouble("longitude")
                val city = loc.optString("city", "")
                val wx = JSONObject(
                    httpGet("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code")
                )
                val cur = wx.getJSONObject("current")
                val t = Math.round(cur.getDouble("temperature_2m")).toInt()
                val code = cur.getInt("weather_code")
                runOnUiThread {
                    weatherTemp.text = "$t°"
                    weatherInfo.text = city + " · " + describe(code)
                }
            } catch (e: Exception) {
            }
        }.start()
    }

    private fun setBackgroundFocus(enabled: Boolean) {
        dock.descendantFocusability =
            if (enabled) ViewGroup.FOCUS_AFTER_DESCENDANTS else ViewGroup.FOCUS_BLOCK_DESCENDANTS
        gear.isFocusable = enabled
    }

    private fun openPanel() {
        if (panelOpen) return
        panelOpen = true
        panelReturn = currentFocus
        setBackgroundFocus(false)
        panel.visibility = View.VISIBLE
        panel.animate().translationX(0f).setDuration(300).start()
        panelList.getChildAt(1)?.requestFocus()
    }

    private fun closePanel(restore: Boolean) {
        if (!panelOpen) return
        panelOpen = false
        panel.animate().translationX(dpf(460)).setDuration(250).withEndAction {
            panel.visibility = View.GONE
        }.start()
        setBackgroundFocus(true)
        if (restore) (panelReturn ?: row.getChildAt(0))?.requestFocus()
    }

    private fun showMySpace(ret: View?) {
        val favs = favSet()
        showScreen(
            "myspace", "My Space", allApps.filter { favs.contains(key(it)) },
            "Nothing saved yet.\nHold OK on any app to save it here.", ret
        )
    }

    private fun showScreen(kind: String, title: String, list: List<ResolveInfo>, empty: String, ret: View?) {
        screenKind = kind
        screenReturn = ret
        setBackgroundFocus(false)
        overlay.removeAllViews()
        overlay.isFocusable = list.isEmpty()
        var first: View? = null
        if (list.isEmpty()) {
            val e = TextView(this)
            e.text = empty
            e.textSize = 20f
            e.gravity = Gravity.CENTER
            e.setTextColor(0x99FFFFFF.toInt())
            overlay.addView(e, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        } else {
            val grid = GridLayout(this)
            grid.columnCount = 5
            list.forEach { ri ->
                val cell = LinearLayout(this)
                cell.orientation = LinearLayout.VERTICAL
                val t = tile(ri)
                if (first == null) first = t
                cell.addView(t, LinearLayout.LayoutParams(dp(144), dp(84)))
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
        }
        val titleTv = TextView(this)
        titleTv.text = title
        titleTv.setTextColor(soft)
        titleTv.textSize = 28f
        overlay.addView(titleTv, FrameLayout.LayoutParams(-2, -2).apply { setMargins(dp(64), dp(36), 0, 0) })
        overlay.alpha = 0f
        overlay.visibility = View.VISIBLE
        overlay.animate().alpha(1f).setDuration(250).start()
        if (list.isEmpty()) overlay.requestFocus() else first?.requestFocus()
    }

    private fun hideScreen() {
        overlay.animate().alpha(0f).setDuration(200).withEndAction {
            overlay.visibility = View.GONE
        }.start()
        screenKind = ""
        setBackgroundFocus(true)
        (screenReturn ?: row.getChildAt(0))?.requestFocus()
    }

    override fun onBackPressed() {
        if (overlay.visibility == View.VISIBLE) hideScreen()
        else if (panelOpen) closePanel(true)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (overlay.visibility == View.VISIBLE) hideScreen()
        if (panelOpen) closePanel(false)
        dock.smoothScrollTo(0, 0)
        row.getChildAt(0)?.requestFocus()
    }
}
