package com.report12321.app.util

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

class CopyReportFieldReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()

        if (text.isBlank()) {
            Toast.makeText(context, "没有可复制的内容", Toast.LENGTH_SHORT).show()
            return
        }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "已复制$label", Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val ACTION_COPY_REPORT_FIELD = "com.report12321.app.action.COPY_REPORT_FIELD"
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_TEXT = "extra_text"
    }
}
