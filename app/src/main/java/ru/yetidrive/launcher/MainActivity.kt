package ru.yetidrive.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.mapview.MapView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val bg = Color.rgb(15, 17, 20)
    private val panel = Color.rgb(27, 30, 35)
    private val raised = Color.rgb(36, 40, 46)
    private val muted = Color.rgb(155, 162, 172)
    private val white = Color.rgb(242, 244, 247)
    private val accent = Color.rgb(153, 174, 195)

    private val uiHandler = Handler(Looper.getMainLooper())
    private val dateFormat = SimpleDateFormat("EEEE, d MMMM · HH:mm", Locale.forLanguageTag("ru-RU"))
    private val prefs by lazy { getSharedPreferences("yeti_drive", Context.MODE_PRIVATE) }

    private var mapView: MapView? = null
    private var mapKitInitialized = false
    private var dateLabel: TextView? = null
    private var weatherIcon: TextView? = null
    private var weatherTemperature: TextView? = null
    private var weatherDescription: TextView? = null
    private var dockApps: MutableList<String> = mutableListOf()
    private var weatherLocation: WeatherLocation? = null
    private var weatherSnapshot: WeatherSnapshot? = null

    private val clockTick = object : Runnable {
        override fun run() {
            dateLabel?.text = dateFormat.format(Date())
            uiHandler.postDelayed(this, 30_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        dockApps = loadDockApps()
        weatherLocation = loadWeatherLocation()

        val apiKey = BuildConfig.MAPKIT_API_KEY
        if (apiKey.isNotBlank()) {
            try {
                MapKitFactory.setApiKey(apiKey)
                MapKitFactory.initialize(this)
                mapKitInitialized = true
                mapView = MapView(this)
            } catch (_: Exception) {
                Toast.makeText(this, "Не удалось запустить Яндекс MapKit", Toast.LENGTH_LONG).show()
            }
        }

        showHome()
        weatherLocation?.let(::refreshWeather)
    }

    override fun onResume() {
        super.onResume()
        dateLabel?.text = dateFormat.format(Date())
        uiHandler.removeCallbacks(clockTick)
        uiHandler.post(clockTick)
    }

    override fun onPause() {
        uiHandler.removeCallbacks(clockTick)
        super.onPause()
    }

    override fun onStart() {
        super.onStart()
        if (mapKitInitialized) {
            MapKitFactory.getInstance().onStart()
            mapView?.onStart()
        }
    }

    override fun onStop() {
        if (mapKitInitialized) {
            mapView?.onStop()
            MapKitFactory.getInstance().onStop()
        }
        super.onStop()
    }

    override fun onDestroy() {
        uiHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun makeText(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun makeButton(label: String, action: () -> Unit) =
        Button(this).apply {
            text = label
            textSize = 14f
            isAllCaps = false
            setTextColor(white)
            setBackgroundColor(raised)
            setOnClickListener { action() }
        }

    private fun showHome() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(bg)
        }

        val mapPanel = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(34, 38, 44))
        }
        val currentMap = mapView
        if (currentMap != null) {
            (currentMap.parent as? ViewGroup)?.removeView(currentMap)
            mapPanel.addView(currentMap, FrameLayout.LayoutParams(-1, -1))
        } else {
            val message = if (BuildConfig.MAPKIT_API_KEY.isBlank()) {
                "ДОБАВЬТЕ MAPKIT_API_KEY В GITHUB SECRETS"
            } else {
                "КАРТА НЕ ЗАГРУЗИЛАСЬ"
            }
            mapPanel.addView(makeText(message, 16f, muted, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(20), dp(20), dp(20))
            }, FrameLayout.LayoutParams(-1, -1))
        }

        val navigationButton = makeButton("Открыть Яндекс Навигатор") {
            launchApp("ru.yandex.yandexnavi")
        }
        mapPanel.addView(navigationButton, FrameLayout.LayoutParams(
            dp(230), dp(54), Gravity.BOTTOM or Gravity.END
        ).apply {
            setMargins(0, 0, dp(16), dp(16))
        })
        content.addView(mapPanel, LinearLayout.LayoutParams(0, -1, 1f))

        val dashboard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        dashboard.layoutParams = LinearLayout.LayoutParams(dp(244), -1)
        dashboard.addView(createWeatherCard(), LinearLayout.LayoutParams(-1, dp(164)))
        dashboard.addView(createPlayerCard(), LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(10)
        })
        content.addView(dashboard)

        shell(content)
        renderWeather()
    }

    private fun shell(content: View) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(bg)
            setPadding(dp(10), dp(8), dp(10), dp(10))
        }

        val side = createSidePanel()
        root.addView(side, LinearLayout.LayoutParams(dp(112), -1))

        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        right.layoutParams = LinearLayout.LayoutParams(0, -1, 1f).apply {
            leftMargin = dp(10)
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(panel)
            setPadding(dp(14), 0, dp(16), 0)
        }
        topBar.addView(makeText("YETI DRIVE", 14f, accent, true))
        topBar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        dateLabel = makeText(dateFormat.format(Date()), 15f, white, true)
        topBar.addView(dateLabel)
        right.addView(topBar, LinearLayout.LayoutParams(-1, dp(42)))
        right.addView(content, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(10)
        })
        right.addView(createDock(), LinearLayout.LayoutParams(-1, dp(92)).apply {
            topMargin = dp(10)
        })

        root.addView(right)
        setContentView(root)
    }

    private fun createSidePanel(): View {
        val side = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(panel)
            setPadding(dp(6), dp(10), dp(6), dp(10))
        }
        side.addView(makeText("YETI", 20f, white, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(18))
        })

        listOf("Главная", "Навигация", "Музыка", "Телефон", "Приложения", "Настройки")
            .forEach { item ->
                side.addView(makeButton(item) {
                    when (item) {
                        "Главная" -> showHome()
                        "Навигация" -> launchApp("ru.yandex.yandexnavi")
                        "Музыка" -> launchApp("ru.yandex.music")
                        "Телефон" -> openDialer()
                        "Приложения" -> showAppPicker("Выберите приложение", ::addDockApp)
                        "Настройки" -> showDockManager()
                    }
                }, LinearLayout.LayoutParams(-1, dp(50)).apply {
                    bottomMargin = dp(5)
                })
            }
        return side
    }

    private fun createPlayerCard(): View {
        val player = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(panel)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        player.addView(makeText("СЕЙЧАС ИГРАЕТ", 12f, muted, true))
        player.addView(makeText("Яндекс Музыка", 19f, white, true).apply {
            setPadding(0, dp(12), 0, dp(8))
        })
        player.addView(makeText("Откройте приложение для выбора музыки", 13f, muted))
        player.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        player.addView(makeButton("Открыть музыку") { launchApp("ru.yandex.music") },
            LinearLayout.LayoutParams(-1, dp(52)))
        return player
    }

    private fun createWeatherCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(panel)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            isClickable = true
            isFocusable = true
            setOnClickListener { showWeatherCityDialog() }
        }
        card.addView(makeText("ПОГОДА", 12f, muted, true))

        val summary = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, 0)
        }
        weatherIcon = makeText("☁", 38f, accent, true).apply { gravity = Gravity.CENTER }
        summary.addView(weatherIcon, LinearLayout.LayoutParams(dp(54), dp(54)))
        weatherTemperature = makeText("—°", 32f, white, true)
        summary.addView(weatherTemperature, LinearLayout.LayoutParams(-2, -1).apply {
            leftMargin = dp(4)
        })
        card.addView(summary)

        weatherDescription = makeText("Нажмите, чтобы выбрать город", 13f, muted)
        card.addView(weatherDescription)
        return card
    }

    private fun createDock(): View {
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(panel)
            setPadding(dp(10), dp(7), dp(10), dp(7))
        }
        dock.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(makeText("БЫСТРЫЙ", 11f, muted, true))
            addView(makeText("ЗАПУСК", 11f, muted, true))
        }, LinearLayout.LayoutParams(dp(82), -1))

        val appStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(appStrip, ViewGroup.LayoutParams(-2, -1))
        }
        dock.addView(scroll, LinearLayout.LayoutParams(0, -1, 1f))
        renderDockApps(appStrip)

        dock.addView(makeButton("＋ Добавить") { showAppPicker("Добавить приложение", ::addDockApp) },
            LinearLayout.LayoutParams(dp(112), -1).apply { leftMargin = dp(8) })
        dock.addView(makeButton("Настроить") { showDockManager() },
            LinearLayout.LayoutParams(dp(102), -1).apply { leftMargin = dp(6) })
        return dock
    }

    private fun renderDockApps(container: LinearLayout) {
        container.removeAllViews()
        if (dockApps.isEmpty()) {
            container.addView(makeText("Добавьте сюда нужные приложения", 13f, muted),
                LinearLayout.LayoutParams(-2, -1).apply { leftMargin = dp(8) })
            return
        }

        dockApps.forEach { packageName ->
            val appButton = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(raised)
                isClickable = true
                isFocusable = true
                contentDescription = appLabel(packageName)
                setOnClickListener { launchApp(packageName) }
                setOnLongClickListener {
                    showDockManager()
                    true
                }
            }
            val icon = ImageView(this).apply {
                try {
                    setImageDrawable(packageManager.getApplicationIcon(packageName))
                } catch (_: Exception) {
                    setImageResource(android.R.drawable.sym_def_app_icon)
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            appButton.addView(icon, LinearLayout.LayoutParams(dp(34), dp(34)))
            appButton.addView(makeText(appLabel(packageName), 10f, white).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(dp(76), dp(18)))
            container.addView(appButton, LinearLayout.LayoutParams(dp(82), -1).apply {
                rightMargin = dp(5)
            })
        }
    }

    private fun showAppPicker(title: String, onPicked: (String) -> Unit) {
        val apps = getLaunchableApps()
        if (apps.isEmpty()) {
            Toast.makeText(this, "Не найдены приложения для запуска", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = apps.map { appLabel(it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(labels) { _, index -> onPicked(apps[index]) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showDockManager() {
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(rows) }
        lateinit var dialog: AlertDialog

        fun redrawRows() {
            rows.removeAllViews()
            if (dockApps.isEmpty()) {
                rows.addView(makeText("Панель пока пустая. Добавьте любое установленное приложение.", 14f, muted).apply {
                    setPadding(dp(10), dp(14), dp(10), dp(14))
                })
            }
            dockApps.forEachIndexed { index, packageName ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(4), dp(3), dp(4), dp(3))
                }
                row.addView(makeText(appLabel(packageName), 14f, white, true).apply {
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(0, dp(48), 1f))

                row.addView(makeButton("↑") {
                    if (index > 0) {
                        Collections.swap(dockApps, index, index - 1)
                        saveDockApps()
                        redrawRows()
                        showHome()
                    }
                }, LinearLayout.LayoutParams(dp(44), dp(42)).apply { leftMargin = dp(3) })
                row.addView(makeButton("↓") {
                    if (index < dockApps.lastIndex) {
                        Collections.swap(dockApps, index, index + 1)
                        saveDockApps()
                        redrawRows()
                        showHome()
                    }
                }, LinearLayout.LayoutParams(dp(44), dp(42)).apply { leftMargin = dp(3) })
                row.addView(makeButton("Замена") {
                    dialog.dismiss()
                    showAppPicker("Выберите приложение на замену") { replacement ->
                        dockApps[index] = replacement
                        saveDockApps()
                        showHome()
                        showDockManager()
                    }
                }, LinearLayout.LayoutParams(dp(78), dp(42)).apply { leftMargin = dp(3) })
                row.addView(makeButton("×") {
                    dockApps.removeAt(index)
                    saveDockApps()
                    redrawRows()
                    showHome()
                }, LinearLayout.LayoutParams(dp(44), dp(42)).apply { leftMargin = dp(3) })
                rows.addView(row, LinearLayout.LayoutParams(-1, dp(50)))
            }

            rows.addView(makeButton("＋ Добавить приложение") {
                dialog.dismiss()
                showAppPicker("Добавить приложение") { addDockApp(it); showDockManager() }
            }, LinearLayout.LayoutParams(-1, dp(48)).apply {
                topMargin = dp(8)
                bottomMargin = dp(8)
            })
        }

        dialog = AlertDialog.Builder(this)
            .setTitle("Настройка быстрого запуска")
            .setView(scroll)
            .setNegativeButton("Готово", null)
            .create()
        dialog.setOnShowListener { redrawRows() }
        dialog.show()
    }

    private fun getLaunchableApps(): List<String> {
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val collator = Collator.getInstance(Locale.forLanguageTag("ru-RU"))
        return packageManager.queryIntentActivities(launchIntent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .distinct()
            .filter { it != packageName }
            .sortedWith { first, second -> collator.compare(appLabel(first), appLabel(second)) }
    }

    private fun appLabel(packageName: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    } catch (_: Exception) {
        packageName
    }

    private fun loadDockApps(): MutableList<String> {
        val saved = prefs.getString("quick_launch_apps", null)
        val launchable = getLaunchableApps().toSet()
        if (saved == null) {
            return mutableListOf("ru.yandex.yandexnavi", "ru.yandex.music")
                .filter { it in launchable }.toMutableList()
        }
        return saved.split('\n').filter { it.isNotBlank() && it in launchable }.toMutableList()
    }

    private fun saveDockApps() {
        prefs.edit().putString("quick_launch_apps", dockApps.joinToString("\n")).apply()
    }

    private fun addDockApp(packageName: String) {
        if (packageName in dockApps) {
            Toast.makeText(this, "Приложение уже добавлено", Toast.LENGTH_SHORT).show()
            return
        }
        dockApps.add(packageName)
        saveDockApps()
        showHome()
    }

    private fun launchApp(packageName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) startActivity(intent)
            else Toast.makeText(this, "Приложение не найдено: ${appLabel(packageName)}", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Не удалось открыть ${appLabel(packageName)}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openDialer() {
        try {
            startActivity(Intent(Intent.ACTION_DIAL))
        } catch (_: Exception) {
            Toast.makeText(this, "Телефонное приложение недоступно", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showWeatherCityDialog() {
        val input = EditText(this).apply {
            hint = "Например, Казань"
            setSingleLine(true)
            setText(weatherLocation?.city.orEmpty())
        }
        AlertDialog.Builder(this)
            .setTitle("Город для погоды")
            .setMessage("Укажите город, для которого показывать текущую погоду.")
            .setView(input)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Найти", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val city = input.text.toString().trim()
                        if (city.isNotEmpty()) {
                            weatherDescription?.text = "Ищем город…"
                            searchWeatherCity(city)
                            dialog.dismiss()
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun searchWeatherCity(query: String) {
        Thread {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val json = readJson("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=ru&format=json")
                val result = json.optJSONArray("results")?.optJSONObject(0)
                    ?: throw IllegalArgumentException("Город не найден")
                val city = result.optString("name", query)
                val admin = result.optString("admin1", "")
                val location = WeatherLocation(
                    city = if (admin.isNotBlank() && admin != city) "$city, $admin" else city,
                    latitude = result.getDouble("latitude"),
                    longitude = result.getDouble("longitude")
                )
                val snapshot = fetchWeather(location)
                weatherLocation = location
                weatherSnapshot = snapshot
                prefs.edit()
                    .putString("weather_city", location.city)
                    .putFloat("weather_latitude", location.latitude.toFloat())
                    .putFloat("weather_longitude", location.longitude.toFloat())
                    .apply()
                runOnUiThread { renderWeather() }
            } catch (error: Exception) {
                runOnUiThread {
                    weatherDescription?.text = "Не удалось загрузить · нажмите, чтобы повторить"
                    Toast.makeText(this, error.message ?: "Не удалось найти город", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun loadWeatherLocation(): WeatherLocation? {
        val city = prefs.getString("weather_city", null) ?: return null
        if (!prefs.contains("weather_latitude") || !prefs.contains("weather_longitude")) return null
        return WeatherLocation(
            city,
            prefs.getFloat("weather_latitude", 0f).toDouble(),
            prefs.getFloat("weather_longitude", 0f).toDouble()
        )
    }

    private fun refreshWeather(location: WeatherLocation) {
        weatherDescription?.text = "Обновляем погоду…"
        Thread {
            try {
                val snapshot = fetchWeather(location)
                weatherSnapshot = snapshot
                runOnUiThread { renderWeather() }
            } catch (_: Exception) {
                runOnUiThread {
                    if (weatherSnapshot == null) {
                        weatherDescription?.text = "Нет данных · нажмите, чтобы повторить"
                    }
                }
            }
        }.start()
    }

    private fun fetchWeather(location: WeatherLocation): WeatherSnapshot {
        val json = readJson(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${location.latitude}&longitude=${location.longitude}" +
                "&current=temperature_2m,weather_code&timezone=auto"
        )
        val current = json.getJSONObject("current")
        val temperature = current.getDouble("temperature_2m").toInt()
        val code = current.getInt("weather_code")
        val condition = when (code) {
            0 -> "Ясно"
            1 -> "Преимущественно ясно"
            2 -> "Переменная облачность"
            3 -> "Облачно"
            45, 48 -> "Туман"
            in 51..57 -> "Морось"
            in 61..67 -> "Дождь"
            in 71..77 -> "Снег"
            in 80..82 -> "Ливень"
            85, 86 -> "Снегопад"
            in 95..99 -> "Гроза"
            else -> "Погода обновлена"
        }
        val icon = when (code) {
            0, 1 -> "☀"
            2 -> "⛅"
            3, 45, 48 -> "☁"
            in 51..67, in 80..82 -> "☂"
            in 71..77, 85, 86 -> "❄"
            in 95..99 -> "⚡"
            else -> "☁"
        }
        return WeatherSnapshot(location.city, temperature, condition, icon)
    }

    private fun readJson(address: String): JSONObject {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.requestMethod = "GET"
        return try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Сервис погоды вернул ошибку ${connection.responseCode}")
            }
            JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun renderWeather() {
        val snapshot = weatherSnapshot
        val location = weatherLocation
        if (snapshot != null) {
            weatherIcon?.text = snapshot.icon
            weatherTemperature?.text = "${snapshot.temperature}°"
            weatherDescription?.text = "${snapshot.condition} · ${snapshot.city}"
        } else if (location != null) {
            weatherIcon?.text = "☁"
            weatherTemperature?.text = "—°"
            weatherDescription?.text = "${location.city} · нажмите для обновления"
        } else {
            weatherIcon?.text = "☁"
            weatherTemperature?.text = "—°"
            weatherDescription?.text = "Нажмите, чтобы выбрать город"
        }
    }

    private data class WeatherLocation(val city: String, val latitude: Double, val longitude: Double)
    private data class WeatherSnapshot(val city: String, val temperature: Int, val condition: String, val icon: String)
}
