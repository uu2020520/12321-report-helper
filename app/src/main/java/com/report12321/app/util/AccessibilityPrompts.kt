package com.report12321.app.util

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import com.report12321.app.ui.AccessibilityGuideActivity

/**
 * 无障碍服务的引导弹窗
 *
 * 之前 MainActivity 里有一个同名弹窗方法，但从未被调用，
 * 导致「未开启无障碍时点举报毫无提示」的静默失败。这里抽成公共对象，供各处复用。
 */
object AccessibilityPrompts {

    fun showEnableDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("需要开启无障碍服务")
            .setMessage(
                "自动填表需要开启无障碍服务。\n\n" +
                    "开启步骤：\n" +
                    "1. 点击「前往设置」\n" +
                    "2. 找到「12321一键举报」\n" +
                    "3. 开启无障碍服务\n" +
                    "4. 返回本应用即可使用\n\n" +
                    "也可以不开：在设置里把填表通道切换为「内置网页」即可。"
            )
            .setPositiveButton("前往设置") { _, _ ->
                context.startActivity(Intent(context, AccessibilityGuideActivity::class.java))
            }
            .setNegativeButton("稍后", null)
            .show()
    }
}
