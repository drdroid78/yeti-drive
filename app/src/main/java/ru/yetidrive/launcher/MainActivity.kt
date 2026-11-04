package ru.yetidrive.launcher

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.mapview.MapView

class MainActivity : Activity() {
    private val bg = Color.rgb(15, 17, 20)
    private val panel = Color.rgb(27, 30, 35)
    private val muted = Color.rgb(155, 162, 172)
    private val white = Color.rgb(242, 244, 247)

    private var mapView: MapView? = null
    private var mapKitInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val apiKey = BuildConfig.MAPKIT_API_KEY
        if (apiKey.isNotBlank()) {
            try {
                MapKitFactory.setApiKey(apiKey)
                MapKitFactory.initialize(this)
                mapKitInitialized = true
                mapView = MapView(this)
            } catch (e: Exception) {
                Toast.makeText(this, "Не удалось запустить Яндекс MapKit", Toast.LENGTH_LONG).show()
            }
        }
        showHome()
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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

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
            isAllCaps = false
            setTextColor(white)
            setBackgroundColor(panel)
            setOnClickListener { action() }
        }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "Приложение не найдено: $packageName", Toast.LENGTH_LONG).show()
        }
    }

    private fun shell(content: View) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(bg)
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }

        val side = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(panel)
            setPadding(dp(6), dp(10), dp(6), dp(10))
        }
        side.layoutParams = LinearLayout.LayoutParams(dp(112), -1)

        side.addView(makeText("YETI", 20f, white, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(18))
        })

        listOf("Главная", "Навигация", "Музыка", "Телефон", "Приложения", "Настройки")
            .forEach { item ->
                side.addView(
                    makeButton(item) {
                        when (item) {
                            "Главная" -> showHome()
                            "Навигация" -> launchApp("ru.yandex.yandexnavi")
                            "Музыка" -> launchApp("ru.yandex.music")
                            else -> Toast.makeText(this, item, Toast.LENGTH_SHORT).show()
                        }
                    },
                    LinearLayout.LayoutParams(-1, dp(52))
                )
            }

        root.addView(side)
        root.addView(content, LinearLayout.LayoutParams(0, -1, 1f).apply {
            leftMargin = dp(10)
        })
        setContentView(root)
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
            dp(230), dp(58), Gravity.BOTTOM or Gravity.END
        ).apply {
            setMargins(0, 0, dp(16), dp(16))
        })
        content.addView(mapPanel, LinearLayout.LayoutParams(0, -1, 1f))

        val player = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(panel)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        player.layoutParams = LinearLayout.LayoutParams(dp(220), -1).apply {
            leftMargin = dp(10)
        }
        player.addView(makeText("СЕЙЧАС ИГРАЕТ", 12f, muted, true))
        player.addView(makeText("Яндекс Музыка", 19f, white, true).apply {
            setPadding(0, dp(12), 0, dp(8))
        })
        player.addView(makeText("Откройте приложение для выбора музыки", 13f, muted))
        player.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        player.addView(
            makeButton("Открыть музыку") { launchApp("ru.yandex.music") },
            LinearLayout.LayoutParams(-1, dp(54))
        )
        content.addView(player)
        shell(content)
    }
}
