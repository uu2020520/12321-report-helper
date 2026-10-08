package com.report12321.app.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.report12321.app.R
import com.report12321.app.service.AutoFillAccessibilityService
import com.report12321.app.util.ReportPrefs
import com.report12321.app.util.SystemBarInsets

/**
 * 设置页
 *
 * 目前的核心设置项：填表通道选择（内置网页 / 无障碍服务），两条通道互斥。
 *
 * 注意：这里必须用 SwitchCompat，不能用 MaterialSwitch。
 * MaterialSwitch 是 Material3 控件，其默认样式引用了 M3 专有属性
 * （?attr/colorSurfaceContainerHighest、?attr/colorOutline），
 * 而本应用主题是 Theme.MaterialComponents（Material2），未定义这些属性，
 * 会导致配色解析失败、打开设置页直接崩溃。
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var channelSwitch: SwitchCompat
    private lateinit var hintText: TextView
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 32)
        }

        root.addView(TextView(this).apply {
            text = "设置"
            textSize = 24f
            setTextColor(getColor(R.color.text_primary))
        })

        channelSwitch = SwitchCompat(this).apply {
            text = "使用无障碍服务自动填表"
            isChecked = ReportPrefs.useAccessibility(this@SettingsActivity)
            setPadding(0, 32, 0, 0)
            setOnCheckedChangeListener { _, isChecked ->
                ReportPrefs.setChannel(
                    this@SettingsActivity,
                    if (isChecked) ReportPrefs.CHANNEL_ACCESSIBILITY else ReportPrefs.CHANNEL_JS
                )
                refreshHint()
            }
        }
        root.addView(channelSwitch)

        hintText = TextView(this).apply {
            textSize = 14f
            setTextColor(getColor(R.color.text_secondary))
            setPadding(0, 8, 0, 0)
        }
        root.addView(hintText)

        statusText = TextView(this).apply {
            textSize = 14f
            setTextColor(getColor(R.color.text_secondary))
            setPadding(0, 24, 0, 0)
        }
        root.addView(statusText)

        root.addView(Button(this).apply {
            text = "前往无障碍设置"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })

        setContentView(root)

        // 系统栏适配：本页背景是浅色，状态栏同色 + 深色图标
        SystemBarInsets.applyVerticalPadding(root)
        SystemBarInsets.setStatusBarColor(window, this, R.color.background, lightIcons = true)
    }

    override fun onResume() {
        super.onResume()
        // 从系统无障碍设置返回后立即刷新状态，不用重进页面
        refreshHint()
        refreshStatus()
    }

    private fun refreshHint() {
        hintText.text = if (ReportPrefs.useAccessibility(this)) {
            "填表时会打开外部浏览器，由无障碍服务自动填写号码和短信内容。\n" +
                "需要先在下方开启无障碍服务，未开启时会提示你前往设置。"
        } else {
            "填表时在内置网页里自动尝试填写，不依赖无障碍服务。\n" +
                "若没填上，可用侧边助手或通知栏复制内容手动填写。"
        }
    }

    private fun refreshStatus() {
        val running = AutoFillAccessibilityService.isRunning()
        statusText.text = "无障碍服务状态：${if (running) "已开启" else "未开启"}"
    }
}
