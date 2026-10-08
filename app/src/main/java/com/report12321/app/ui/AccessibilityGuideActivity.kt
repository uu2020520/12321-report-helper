package com.report12321.app.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.report12321.app.R
import com.report12321.app.util.SystemBarInsets

class AccessibilityGuideActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 32)
        }

        root.addView(TextView(this).apply {
            text = "开启无障碍服务"
            textSize = 24f
            setTextColor(getColor(R.color.text_primary))
        })

        root.addView(TextView(this).apply {
            text = "请在系统无障碍设置中找到「12321一键举报」，开启后返回本应用。该服务只在你点击一键举报后辅助填写12321页面。"
            textSize = 16f
            setTextColor(getColor(R.color.text_secondary))
            setPadding(0, 24, 0, 24)
        })

        root.addView(Button(this).apply {
            text = "前往无障碍设置"
            gravity = Gravity.CENTER
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })

        setContentView(root)

        // 系统栏适配：本页背景是浅色，状态栏同色 + 深色图标
        SystemBarInsets.applyVerticalPadding(root)
        SystemBarInsets.setStatusBarColor(window, this, R.color.background, lightIcons = true)
    }
}
