package ru.yetidrive.launcher

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val bg = Color.rgb(15, 17, 20)
    private val panel = Color.rgb(27, 30, 35)
    private val muted = Color.rgb(155, 162, 172)
    private val white = Color.rgb(242, 244, 247)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        showHome()
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
            Toast.makeText(
                this,
                "Приложение не найдено: $packageName",
                Toast.LENGTH_LONG
            ).show()
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

        val map = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(34, 38, 44))
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setOnClickListener { launchApp("ru.yandex.yandexnavi") }
        }
        map.addView(makeText("ЯНДЕКС НАВИГАТОР", 23f, white, true).apply {
            gravity = Gravity.CENTER
        })
        map.addView(makeText("Нажмите, чтобы открыть навигацию", 14f, muted).apply {
            gravity = Gravity.CENTER
        })
        content.addView(map, LinearLayout.LayoutParams(0, -1, 1f))

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
