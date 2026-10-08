package com.report12321.app.util

import android.content.Context

/**
 * 举报填表的用户偏好设置
 *
 * 目前只有一项：填表通道选择。两条通道互斥，同一时刻只会走一条。
 */
object ReportPrefs {

    private const val FILE_NAME = "report_prefs"
    private const val KEY_CHANNEL = "auto_fill_channel"

    /** 内置 WebView + 注入 JS 填表（默认，最稳） */
    const val CHANNEL_JS = "js"

    /** 无障碍服务 + 外部浏览器填表（需先开启无障碍服务） */
    const val CHANNEL_ACCESSIBILITY = "accessibility"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun getChannel(context: Context): String =
        prefs(context).getString(KEY_CHANNEL, CHANNEL_JS) ?: CHANNEL_JS

    fun setChannel(context: Context, channel: String) {
        prefs(context).edit().putString(KEY_CHANNEL, channel).apply()
    }

    fun useAccessibility(context: Context): Boolean =
        getChannel(context) == CHANNEL_ACCESSIBILITY
}
