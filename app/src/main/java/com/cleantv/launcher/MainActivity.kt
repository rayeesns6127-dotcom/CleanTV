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

data class AppItem(val label: String, val pkg: String, val intent: Intent, val image: Drawable, val banner: Boolean)

class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var adapter: AppAdapter

    // Shown on first run if installed. Change anytime in Menu > Manage apps.
    private val essentials = setOf(
        "com.google.android.youtube.tv", "com.netflix.ninja", "com.amazon.amazonvideo.livingroom",
        "com.android.vending", "com.android.tv.settings", "org.videolan.vlc", "org.xbmc.kodi",
        "com.spotify.tv.android", "com.disney.disneyplus", "in.startv.hotstar"
    )

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("launcher", MODE_PRIVATE)
        adapter = AppAdapter({ open(it) }, { menu(it) }, { prefs.getBoolean("lock", false) })
        val grid = findViewById<RecyclerView>(R.id.grid)
        grid.layoutManager = GridLayoutManager(this, 5)
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

    private fun go(action: String) {
        try { startActivity(Intent(action)) } catch (e: Exception) { toast("Not available on this TV") }
    }
    private fun open(a: AppItem) {
        try { startActivity(a.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { toast("Can't open ${a.label}") }
    }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun dlg() = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)

    private fun allApps(): List<AppItem> {
        val pm = packageManager
        val out = LinkedHashMap<String, AppItem>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            for (ri in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == packageName || out.containsKey(pkg)) continue
                val launch = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: continue
                val banner = ri.activityInfo.loadBanner(pm)
                out[pkg] = AppItem(ri.loadLabel(pm).toString(), pkg, launch, banner ?: ri.activityInfo.loadIcon(pm), banner != null)
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

    private fun wx(c: Int): Pair<String, String> = when (c) {
        0 -> "☀️" to "Clear"
        1, 2 -> "⛅" to "Partly cloudy"
        3 -> "☁️" to "Overcast"
        45, 48 -> "🌫️" to "Fog"
        in 51..57 -> "🌦️" to "Drizzle"
        in 61..67, in 80..82 -> "🌧️" to "Rain"
        in 71..77, 85, 86 -> "❄️" to "Snow"
        in 95..99 -> "⛈️" to "Thunderstorm"
        else -> "🌡️" to "Unknown"
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
                    val (ic, _) = wx(d.getJSONArray("weather_code").getInt(i))
                    outF.format(inF.parse(d.getJSONArray("time").getString(i))!!) + "\n" + ic + "\n" +
                        Math.round(d.getJSONArray("temperature_2m_max").getDouble(i)) + "°/" + Math.round(d.getJSONArray("temperature_2m_min").getDouble(i)) + "°"
                }
                runOnUiThread {
                    tv(R.id.wIcon).text = icon
                    tv(R.id.wTemp).text = "${Math.round(c.getDouble("temperature_2m"))}°C"
                    tv(R.id.wCity).text = g.getString("name"); tv(R.id.wCond).text = cond
                    tv(R.id.wHum).text = "Humidity  ${c.getInt("relative_humidity_2m")}%"
                    tv(R.id.wWind).text = "Wind  ${Math.round(c.getDouble("wind_speed_10m"))} km/h"
                    tv(R.id.wFeels).text = "Feels like  ${Math.round(c.getDouble("apparent_temperature"))}°C"
                    val box = findViewById<LinearLayout>(R.id.forecast); box.removeAllViews()
                    for (s in days) box.addView(TextView(this).apply {
                        gravity = Gravity.CENTER; textSize = 12f; setTextColor(0xFFC8CCD4.toInt()); text = s
                        minWidth = (76 * resources.displayMetrics.density).toInt()
                    })
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
        val img: ImageView = v.findViewById(R.id.img)
        val name: TextView = v.findViewById(R.id.name)
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(LayoutInflater.from(p.context).inflate(R.layout.item_app, p, false))
    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, i: Int) {
        val a = items[i]
        val d = h.itemView.resources.displayMetrics.density
        h.img.setImageDrawable(a.image)
        if (a.banner) { h.img.scaleType = ImageView.ScaleType.CENTER_CROP; h.img.setPadding(0, 0, 0, 0) }
        else { h.img.scaleType = ImageView.ScaleType.CENTER_INSIDE; h.img.setPadding((24 * d).toInt(), (14 * d).toInt(), (24 * d).toInt(), (36 * d).toInt()) }
        h.name.visibility = if (a.banner) View.GONE else View.VISIBLE
        h.name.text = a.label
        h.itemView.setOnClickListener { click(a) }
        h.itemView.setOnLongClickListener { if (locked()) false else { long(a); true } }
        h.itemView.setOnFocusChangeListener { v, f ->
            v.animate().scaleX(if (f) 1.08f else 1f).scaleY(if (f) 1.08f else 1f).setDuration(120).start()
            v.translationZ = if (f) 8f else 0f
        }
    }
}
