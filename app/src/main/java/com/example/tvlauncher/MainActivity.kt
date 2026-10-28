package com.example.tvlauncher

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

class Choice(val label: String, val run: () -> Unit)

class MainActivity : Activity() {
    private lateinit var scenery: Scenery
    private lateinit var glassDock: Glass
    private lateinit var weatherTemp: TextView
    private lateinit var weatherInfo: TextView
    private lateinit var clock: TextClock
    private lateinit var gear: ImageView
    private lateinit var dock: HorizontalScrollView
    private lateinit var row: LinearLayout
    private lateinit var shelfRow: LinearLayout
    private lateinit var shelfBox: LinearLayout
    private lateinit var pills: LinearLayout
    private lateinit var appsBtn: TextView
    private lateinit var overlay: FrameLayout
    private lateinit var panel: ScrollView
    private lateinit var panelList: LinearLayout
    private lateinit var wallRow: TextView
    private lateinit var clockRow: TextView
    private val soft = 0xE6FFFFFF.toInt()
    private var allApps = listOf<ResolveInfo>()
    private var byKey = mapOf<String, ResolveInfo>()
    private var leanKeys = listOf<String>()
    private var panelOpen = false
    private var panelReturn: View? = null
    private var screenReturn: View? = null
    private var screenKind = ""
    private var lastWeather = 0L
    private var h24 = true
    private val prefs by lazy { getSharedPreferences("glass", MODE_PRIVATE) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun dpf(v: Int) = v * resources.displayMetrics.density
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun glass(radius: Float, oval: Boolean = false) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x4DFFFFFF, 0x1AFFFFFF)
    ).apply {
        if (oval) shape = GradientDrawable.OVAL else cornerRadius = radius
        setStroke(dp(1), 0x66FFFFFF)
    }

    private fun focusFx(v: View, radius: Float, ring: Boolean = true, grow: Float = 1.05f) {
        v.isFocusable = true
        v.setOnFocusChangeListener { _, f ->
            val s = if (f) grow else 1f
            v.animate().scaleX(s).scaleY(s).translationZ(if (f) dpf(10) else 0f).setDuration(200).start()
            if (ring) {
                v.foreground = if (f) GradientDrawable().apply {
                    cornerRadius = radius
                    setStroke(dp(2), soft)
                } else null
            }
        }
    }

    private fun query(cat: String): List<ResolveInfo> =
        packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)
            .filter { it.activityInfo.packageName != packageName }

    private fun label(ri: ResolveInfo) = ri.loadLabel(packageManager).toString()
    private fun key(ri: ResolveInfo) = ri.activityInfo.packageName + "/" + ri.activityInfo.name

    private fun getList(name: String, def: List<String>): MutableList<String> {
        val s = prefs.getString(name, null)
        val base = if (s == null) def else s.split("\n").filter { it.isNotEmpty() }
        return base.filter { byKey.containsKey(it) }.toMutableList()
    }

    private fun saveList(name: String, l: List<String>) {
        prefs.edit().putString(name, l.joinToString("\n")).apply()
    }

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

    private fun tile(ri: ResolveInfo, ctx: String): FrameLayout {
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
        fl.tag = key(ri)
        focusFx(fl, dpf(14), false, 1.1f)
        fl.setOnClickListener { launch(ri) }
        fl.setOnLongClickListener {
            showMenu(ri, ctx)
            true
        }
        return fl
    }

    private fun tileLp(w: Int, h: Int) =
        LinearLayout.LayoutParams(dp(w), dp(h)).apply { setMargins(0, 0, dp(16), 0) }

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

    private fun showChoices(title: String, items: List<Choice>) {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(24), dp(24), dp(24), dp(16))
        box.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xF2141A2E.toInt(), 0xF20B0F1E.toInt())
        ).apply {
            cornerRadius = dpf(28)
            setStroke(dp(1), 0x66FFFFFF)
        }
        val t = TextView(this)
        t.text = title
        t.textSize = 20f
        t.setTextColor(soft)
        t.maxLines = 1
        t.ellipsize = TextUtils.TruncateAt.END
        t.setPadding(dp(4), 0, 0, dp(14))
        box.addView(t, LinearLayout.LayoutParams(dp(340), -2))
        items.forEach { c ->
            box.addView(panelRow(c.label) {
                d.dismiss()
                c.run()
            }, LinearLayout.LayoutParams(dp(340), dp(52)).apply { setMargins(0, 0, 0, dp(8)) })
        }
        d.setContentView(box)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        d.show()
        box.getChildAt(1)?.requestFocus()
    }

    private fun showMenu(ri: ResolveInfo, ctx: String) {
        val k = key(ri)
        val dockL = getList("dock2", leanKeys)
        val favL = getList("fav2", emptyList<String>())
        val idx = dockL.indexOf(k)
        val items = ArrayList<Choice>()
        if (ctx == "dock" && idx > 0) items.add(Choice("Move left") { moveDock(k, -1) })
        if (ctx == "dock" && idx >= 0 && idx < dockL.size - 1) items.add(Choice("Move right") { moveDock(k, 1) })
        if (idx >= 0) {
            items.add(Choice("Remove from Home") {
                dockL.remove(k)
                saveList("dock2", dockL)
                rebuildHome(dockL.getOrNull(idx) ?: dockL.lastOrNull())
                toast("Removed from Home")
            })
        } else {
            items.add(Choice("Add to Home") {
                dockL.add(k)
                saveList("dock2", dockL)
                rebuildHome(null)
                toast("Added to Home")
            })
        }
        if (favL.contains(k)) {
            items.add(Choice("Remove from My Space") {
                favL.remove(k)
                saveList("fav2", favL)
                favChanged()
                toast("Removed from My Space")
            })
        } else {
            items.add(Choice("Save to My Space") {
                favL.add(k)
                saveList("fav2", favL)
                favChanged()
                toast("Saved to My Space")
            })
        }
        showChoices(label(ri), items)
    }

    private fun moveDock(k: String, dir: Int) {
        val l = getList("dock2", leanKeys)
        val i = l.indexOf(k)
        val j = i + dir
        if (i < 0 || j < 0 || j >= l.size) return
        Collections.swap(l, i, j)
        saveList("dock2", l)
        rebuildHome(k)
    }

    private fun favChanged() {
        rebuildHome(null)
        if (overlay.visibility == View.VISIBLE && screenKind == "myspace") showMySpace(screenReturn)
    }

    private fun rebuildHome(focusKey: String?, inShelf: Boolean = false) {
        row.removeAllViews()
        getList("dock2", leanKeys).mapNotNull { byKey[it] }
            .forEach { row.addView(tile(it, "dock"), tileLp(144, 84)) }
        row.addView(appsBtn, tileLp(144, 84))
        shelfRow.removeAllViews()
        val favs = getList("fav2", emptyList<String>()).mapNotNull { byKey[it] }
        if (favs.isEmpty()) {
            val hint = TextView(this)
            hint.text = "Nothing here yet. Hold OK on any app to save it to My Space."
            hint.textSize = 15f
            hint.setTextColor(0x99FFFFFF.toInt())
            hint.gravity = Gravity.CENTER_VERTICAL
            shelfRow.addView(hint, LinearLayout.LayoutParams(-2, dp(68)))
        } else {
            favs.forEach { shelfRow.addView(tile(it, "shelf"), tileLp(120, 68)) }
        }
        if (focusKey != null) {
            row.postDelayed({
                val parent = if (inShelf) shelfRow else row
                for (i in 0 until parent.childCount) {
                    val ch = parent.getChildAt(i)
                    if (ch.tag == focusKey) {
                        ch.requestFocus()
                        break
                    }
                }
            }, 150)
        }
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

    private fun liveTv() {
        val c = allApps.filter { it.activityInfo.packageName == "com.tcl.tv" }
        val ri = c.firstOrNull { leanKeys.contains(key(it)) } ?: c.firstOrNull()
        if (ri != null) launch(ri) else toast("Live TV app not found")
    }

    private fun search() {
        try {
            startActivity(Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            toast("Search is not available")
        }
    }

    private fun showInputs() {
        try {
            val tim = getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager
            val ins = tim.tvInputList.filter { it.isPassthroughInput }
            if (ins.isEmpty()) {
                toast("No inputs found")
                return
            }
            val items = ins.map { info ->
                Choice(info.loadLabel(this).toString()) {
                    try {
                        startActivity(
                            Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(info.id))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (e: Exception) {
                        toast("Could not open input")
                    }
                }
            }
            showChoices("Inputs", items)
        } catch (e: Exception) {
            toast("Inputs are not available")
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        h24 = prefs.getBoolean("h24", true)

        val tvApps = query(Intent.CATEGORY_LEANBACK_LAUNCHER).sortedBy { label(it).lowercase() }
        allApps = (tvApps + query(Intent.CATEGORY_LAUNCHER))
            .distinctBy { key(it) }
            .sortedBy { label(it).lowercase() }
        byKey = allApps.associateBy { key(it) }
        leanKeys = tvApps.map { key(it) }

        val root = FrameLayout(this)
        scenery = Scenery(this)
        scenery.wall = WALLS[prefs.getInt("wp", 0) % WALLS.size]
        root.addView(scenery, FrameLayout.LayoutParams(-1, -1))

        val scrim = View(this)
        scrim.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x00000000, 0x66000000)
        )
        root.addView(scrim, FrameLayout.LayoutParams(-1, dp(300), Gravity.BOTTOM))

        // upper left: weather + date
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

        // upper right: clock + settings button
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

        // quick action pills
        pills = LinearLayout(this)
        pills.orientation = LinearLayout.HORIZONTAL
        fun pill(text: String, action: () -> Unit) {
            val t = TextView(this)
            t.text = text
            t.textSize = 17f
            t.setTextColor(soft)
            t.gravity = Gravity.CENTER
            t.setPadding(dp(26), 0, dp(26), 0)
            t.background = glass(dpf(22))
            focusFx(t, dpf(22))
            t.setOnClickListener { action() }
            pills.addView(t, LinearLayout.LayoutParams(-2, dp(44)).apply { setMargins(0, 0, dp(12), 0) })
        }
        pill("Live TV") { liveTv() }
        pill("Inputs") { showInputs() }
        pill("Search") { search() }
        root.addView(pills, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            setMargins(dp(64), dp(160), 0, 0)
        })

        // My Space shelf
        shelfRow = LinearLayout(this)
        shelfRow.gravity = Gravity.CENTER_VERTICAL
        val shelfScroll = HorizontalScrollView(this)
        shelfScroll.isHorizontalScrollBarEnabled = false
        shelfScroll.clipToPadding = false
        shelfScroll.setPadding(dp(10), dp(10), dp(10), dp(10))
        shelfScroll.addView(shelfRow)
        val shelfLabel = TextView(this)
        shelfLabel.text = "My Space"
        shelfLabel.textSize = 18f
        shelfLabel.setTextColor(soft)
        shelfLabel.setPadding(dp(6), 0, 0, dp(2))
        shelfBox = LinearLayout(this)
        shelfBox.orientation = LinearLayout.VERTICAL
        shelfBox.addView(shelfLabel)
        shelfBox.addView(shelfScroll)
        root.addView(shelfBox, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(64), 0, dp(64), dp(190))
        })

        // glass dock
        appsBtn = TextView(this)
        appsBtn.text = "Apps"
        appsBtn.gravity = Gravity.CENTER
        appsBtn.setTextColor(soft)
        appsBtn.textSize = 18f
        appsBtn.background = glass(dpf(14))
        focusFx(appsBtn, dpf(14))
        appsBtn.setOnClickListener { showScreen("apps", "Apps", allApps, "No apps found", currentFocus) }

        row = LinearLayout(this)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(18), dp(18), dp(18), dp(18))
        dock = HorizontalScrollView(this)
        dock.isHorizontalScrollBarEnabled = false
        dock.clipToPadding = false
        dock.addView(row)
        glassDock = Glass(this, scenery, dpf(28))
        glassDock.addView(dock, FrameLayout.LayoutParams(-1, -2))
        root.addView(glassDock, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(64), 0, dp(64), dp(56))
        })

        // overlay for Apps / My Space
        overlay = FrameLayout(this)
        overlay.setBackgroundColor(0xE60B0F1E.toInt())
        overlay.visibility = View.GONE
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))

        // settings side panel
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
            search()
        })
        wallRow = panelRow("") {
            val idx = (prefs.getInt("wp", 0) + 1) % WALLS.size
            prefs.edit().putInt("wp", idx).apply()
            scenery.wall = WALLS[idx]
            glassDock.refresh()
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
        add(panelRow("Reset lay }
