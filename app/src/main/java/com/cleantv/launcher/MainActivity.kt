package com.cleantv.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.concurrent.thread

data class AppItem(val label: String, val pkg: String, val intent: Intent, val icon: Drawable)
data class Day(val name: String, val icon: Int, val temp: String)

class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var adapter: AppAdapter

    // Shown on first run if installed. Change anytime in Menu > Manage apps.
    private val essentials = setOf(
        "com.google.android.youtube.tv", "com.netflix.ninja", "com.amazon.amazonvideo.livingroom",
        "com.android.vending", "com.android.tv.settings", "org.videolan.vlc", "org.xbmc.kodi",
        "com.spotify.tv.android", "com.disney.disneyplus", "in.startv.hotstar"
    )

    private val niceNames = mapOf(
        "com.android.vending" to "Play Store", "com.google.android.youtube.tv" to "YouTube",
        "com.google.android.youtube" to "YouTube", "com.amazon.amazonvideo.livingroom" to "Prime Video",
        "com.netflix.ninja" to "Netflix", "org.videolan.vlc" to "VLC", "com.spotify.tv.android" to "Spotify",
        "com.android.tv.settings" to "Settings", "org.xbmc.kodi" to "Kodi", "com.disney.disneyplus" to "Disney+"
    )

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("launcher", MODE_PRIVATE)
        adapter = AppAdapter({ open(it) }, { menu(it) }, { prefs.getBoolean("lock", false) })
        val grid = findViewById<RecyclerView>(R.id.grid)
        grid.layoutManager = GridLayoutManager(this, 6)
        grid.adapter = adapter
        findViewById<View>(R.id.icBt).setOnClickListener { go(Settings.ACTION_BLUETOOTH_SETTINGS) }
        findViewById<View>(R.id.icWifi).setOnClickListener { go(Settings.ACTION_WIFI_SETTINGS) }
        findViewById<View>(R.id.icSettings).setOnClickListener { showSettings() }
    }

    override fun onResume() {
        super.onResume()
        applyPrefs(); refreshApps(); loadWeather()
    }

    override fun onKeyDown(k: Int, e: KeyEvent): Boolean {
        if (k == KeyEvent.KEYCODE_MENU || k == KeyEvent.KEYCODE_SETTINGS) { showSettings(); return true }
        return super.onKeyDown(k, e)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() { /* home screen: Back does nothing */ }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun go(action: String) {
        try { startActivity(Intent(action)) } catch (e: Exception) { toast("Not available on this TV") }
    }
    private fun open(a: AppItem) {
        try { startActivity(a.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { toast("Can't open ${a.label}") }
    }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun dlg() = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)

    private fun clean(pkg: String, raw: String): String =
        niceNames[pkg] ?: raw.replace(" for Android TV", "").replace(" for TV", "").replace("Android TV", "").trim().ifEmpty { raw }

    private fun allApps(): List<AppItem> {
        val pm = packageManager
        val out = LinkedHashMap<String, AppItem>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            for (ri in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == packageName || out.containsKey(pkg)) continue
                val launch = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: continue
                out[pkg] = AppItem(clean(pkg, ri.loadLabel(pm).toString()), pkg, launch, ri.activityInfo.loadIcon(pm))
            }
        }
        return out.values.sortedBy { it.label.lowercase() }
    }

    private fun selected(all: List<AppItem>): Set<String> {
        prefs.getStringSet("apps", null)?.let { return it }
        val d = all.filter { it.pkg in essentials }.map { it.pkg }.toSet()
        return if (d.isEmpty()) all.map { it.pkg }.toSet() else d
    }

    private fun refreshApps() {
        val all = allApps(); val sel = selected(all)
        adapter.submit(all.filter { it.pkg in sel })
        val grid = findViewById<View>(R.id.grid)
        grid.post { if (currentFocus == null) grid.requestFocus() }
    }

    private fun applyPrefs() {
        fun vis(id: Int, on: Boolean) { findViewById<View>(id).visibility = if (on) View.VISIBLE else View.GONE }
        vis(R.id.topBar, prefs.getBoolean("bar", true))
        vis(R.id.clock, prefs.getBoolean("time", true))
        vis(R.id.dateBox, prefs.getBoolean("date", true))
        val net = prefs.getBoolean("net", true)
        vis(R.id.icBt, net); vis(R.id.icWifi, net)
        vis(R.id.weather, prefs.getBoolean("weather", true))
    }

    private fun menu(a: AppItem) {
        dlg().setTitle(a.label).setItems(arrayOf("Open", "App info", "Uninstall", "Remove from home")) { _, i ->
            try {
                when (i) {
                    0 -> open(a)
                    1 -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}")))
                    2 -> startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}")))
                    3 -> {
                        val s = HashSet(selected(allApps())); s.remove(a.pkg)
                        prefs.edit().putStringSet("apps", s).apply(); refreshApps()
                    }
                }
            } catch (e: Exception) { toast("Not available on this TV") }
        }.show()
    }

    private fun showSettings() {
        dlg().setTitle("Launcher settings")
            .setItems(arrayOf("Status bar", "Manage apps", "Weather city", "System settings")) { _, i ->
                when (i) { 0 -> statusBarDialog(); 1 -> manageApps(); 2 -> cityDialog(); 3 -> go(Settings.ACTION_SETTINGS) }
            }.show()
    }

    private fun statusBarDialog() {
        val labels = arrayOf("Show top status bar", "Weather widget", "Lock icons", "Time", "Date", "Network icons")
        val keys = arrayOf("bar", "weather", "lock", "time", "date", "net")
        val defs = booleanArrayOf(true, true, false, true, true, true)
        val checked = BooleanArray(keys.size) { prefs.getBoolean(keys[it], defs[it]) }
        dlg().setTitle("Status bar")
            .setMultiChoiceItems(labels, checked) { _, w, on -> prefs.edit().putBoolean(keys[w], on).apply(); applyPrefs() }
            .setPositiveButton("Done", null).show()
    }

    private fun manageApps() {
        val all = allApps(); val sel = HashSet(selected(all))
        val checked = BooleanArray(all.size) { all[it].pkg in sel }
        dlg().setTitle("Choose apps for home")
            .setMultiChoiceItems(all.map { it.label }.toTypedArray(), checked) { _, w, on -> if (on) sel.add(all[w].pkg) else sel.remove(all[w].pkg) }
            .setPositiveButton("Done") { _, _ -> prefs.edit().putStringSet("apps", sel).apply(); refreshApps() }.show()
    }

    private fun cityDialog() {
        val et = EditText(this); et.setText(prefs.getString("city", "Abu Dhabi")); et.setTextColor(-1); et.setSingleLine()
        dlg().setTitle("Weather city").setView(et)
            .setPositiveButton("Save") { _, _ -> prefs.edit().putString("city", et.text.toString().trim()).apply(); loadWeather() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun http(u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    private fun wx(c: Int): Pair<Int, String> = when (c) {
        0 -> R.drawable.w_sun to "Clear"
        1, 2 -> R.drawable.w_partly to "Partly cloudy"
        3 -> R.drawable.w_cloud to "Overcast"
        45, 48 -> R.drawable.w_fog to "Fog"
        in 51..57 -> R.drawable.w_rain to "Drizzle"
        in 61..67, in 80..82 -> R.drawable.w_rain to "Rain"
        in 71..77, 85, 86 -> R.drawable.w_snow to "Snow"
        in 95..99 -> R.drawable.w_storm to "Thunderstorm"
        else -> R.drawable.w_cloud to "Cloudy"
    }

    private fun loadWeather() {
        val city = prefs.getString("city", "Abu Dhabi") ?: "Abu Dhabi"
        thread {
            try {
                val g = JSONObject(http("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + URLEncoder.encode(city, "UTF-8")))
                    .getJSONArray("results").getJSONObject(0)
                val w = JSONObject(http("https://api.open-meteo.com/v1/forecast?latitude=${g.getDouble("latitude")}&longitude=${g.getDouble("longitude")}" +
                    "&current=temperature_2m,relative_humidity_2m,apparent_temperature,wind_speed_10m,weather_code" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=6"))
                val c = w.getJSONObject("current"); val d = w.getJSONObject("daily")
                val (icon, cond) = wx(c.getInt("weather_code"))
                val inF = SimpleDateFormat("yyyy-MM-dd", Locale.US); val outF = SimpleDateFormat("EEE", Locale.getDefault())
                val days = (1..5).map { i ->
                    Day(
                        outF.format(inF.parse(d.getJSONArray("time").getString(i))!!),
                        wx(d.getJSONArray("weather_code").getInt(i)).first,
                        Math.round(d.getJSONArray("temperature_2m_max").getDouble(i)).toString() + "°/" +
                            Math.round(d.getJSONArray("temperature_2m_min").getDouble(i)) + "°"
                    )
                }
                runOnUiThread {
                    findViewById<ImageView>(R.id.wIcon).setImageResource(icon)
                    tv(R.id.wTemp).text = "${Math.round(c.getDouble("temperature_2m"))}°C"
                    tv(R.id.wCity).text = g.getString("name"); tv(R.id.wCond).text = cond
                    tv(R.id.wHum).text = "Humidity  ${c.getInt("relative_humidity_2m")}%"
                    tv(R.id.wWind).text = "Wind  ${Math.round(c.getDouble("wind_speed_10m"))} km/h"
                    tv(R.id.wFeels).text = "Feels like  ${Math.round(c.getDouble("apparent_temperature"))}°C"
                    val box = findViewById<LinearLayout>(R.id.forecast); box.removeAllViews()
                    for (f in days) {
                        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; minimumWidth = dp(76) }
                        col.addView(TextView(this).apply { text = f.name; textSize = 12f; setTextColor(0xFFAEB8CC.toInt()); gravity = Gravity.CENTER })
                        col.addView(ImageView(this).apply {
                            setImageResource(f.icon)
                            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply { topMargin = dp(4); bottomMargin = dp(4) }
                        })
                        col.addView(TextView(this).apply { text = f.temp; textSize = 12f; setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER })
                        box.addView(col)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { tv(R.id.wCond).text = "Weather unavailable" }
            }
        }
    }

    private fun tv(id: Int) = findViewById<TextView>(id)
}

class AppAdapter(
    private val click: (AppItem) -> Unit,
    private val long: (AppItem) -> Unit,
    private val locked: () -> Boolean
) : RecyclerView.Adapter<AppAdapter.VH>() {
    private var items: List<AppItem> = emptyList()
    fun submit(l: List<AppItem>) { items = l; notifyDataSetChanged() }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val card: View = v.findViewById(R.id.card)
        val img: ImageView = v.findViewById(R.id.img)
        val name: TextView = v.findViewById(R.id.name)
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(LayoutInflater.from(p.context).inflate(R.layout.item_app, p, false))
    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, i: Int) {
        val a = items[i]
        h.img.setImageDrawable(a.icon)
        h.name.text = a.label
        h.name.setTextColor(0xFFAEB8CC.toInt())
        h.card.setOnClickListener { click(a) }
        h.card.setOnLongClickListener { if (locked()) false else { long(a); true } }
        h.card.setOnFocusChangeListener { _, f ->
            val s = if (f) 1.1f else 1f
            h.itemView.animate().scaleX(s).scaleY(s).setDuration(130).start()
            h.itemView.translationZ = if (f) 10f else 0f
            h.name.setTextColor(if (f) 0xFFFFFFFF.toInt() else 0xFFAEB8CC.toInt())
        }
    }
}
