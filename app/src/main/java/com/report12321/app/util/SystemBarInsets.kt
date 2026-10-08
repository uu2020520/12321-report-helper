package com.report12321.app.util

import android.content.Context
import android.view.View
import android.view.Window
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 系统栏（状态栏 / 导航栏）适配
 *
 * 背景：本工程 targetSdk = 35。在 Android 15（API 35）及以上，系统对 targetSdk 35
 * 的应用强制启用全面屏（edge-to-edge）：状态栏与导航栏变透明，内容直接绘制到系统栏下方，
 * 于是 Toolbar 会与状态栏重叠、底部按钮会被手势导航条压住。
 *
 * 处理方式：用 WindowInsets 读出系统栏高度，补成对应方向的内边距。
 * 在 Android 15 以下这些 inset 会返回 0，因此不会重复加padding。
 */
object SystemBarInsets {

    /**
     * 给顶部视图补上状态栏高度的内边距
     *
     * 一般用在 Toolbar 上：Toolbar 高度改为 wrap_content + minHeight，
     * 这样加上内边距后它的背景会一直铺到屏幕顶端，盖住透明状态栏区域。
     */
    fun applyTopPadding(view: View) {
        val initialTop = view.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, initialTop + top, v.paddingRight, v.paddingBottom)
            insets
        }
        view.requestApplyInsets()
    }

    /**
     * 给底部容器补上导航栏高度的内边距，避免按钮/列表被手势导航条遮住
     */
    fun applyBottomPadding(view: View) {
        val initialBottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, initialBottom + bottom)
            insets
        }
        view.requestApplyInsets()
    }

    /**
     * 同时给顶部和底部补内边距
     *
     * 注意：同一个 View 上只能有一个 OnApplyWindowInsetsListener，
     * 分别调用 applyTopPadding() 和 applyBottomPadding() 会互相覆盖，所以这里提供合并版本。
     */
    fun applyVerticalPadding(view: View) {
        val initialTop = view.paddingTop
        val initialBottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                v.paddingLeft,
                initialTop + bars.top,
                v.paddingRight,
                initialBottom + bars.bottom
            )
            insets
        }
        view.requestApplyInsets()
    }

    /**
     * 设置状态栏底色与图标颜色
     *
     * 双层保险：
     * 1. 平台支持时直接把状态栏涂成指定颜色（和顶部 Toolbar 同色，视觉上连成一片）；
     * 2. Android 15 全面屏强制生效、状态栏被强制透明时，由 applyTopPadding() 让 Toolbar
     *    背景延伸到状态栏下方兜底。两条路结果一致。
     *
     * @param lightIcons true = 深色图标（适合浅色底），false = 浅色图标（适合深色底）
     */
    fun setStatusBarColor(
        window: Window,
        context: Context,
        @ColorRes colorRes: Int,
        lightIcons: Boolean
    ) {
        @Suppress("DEPRECATION")
        window.statusBarColor = ContextCompat.getColor(context, colorRes)
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = lightIcons
    }
}
