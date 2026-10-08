package com.report12321.app.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.report12321.app.R
import com.report12321.app.ui.MainActivity

object ReportHelperNotifier {
    private const val CHANNEL_ID = "report_helper_channel"
    private const val NOTIFICATION_ID = 2100

    /** 超时自动收起，避免用户忘记关闭时长驻通知栏 */
    private const val AUTO_CANCEL_DELAY_MS = 15 * 60 * 1000L

    fun show(context: Context, data: ReportDataHolder.ReportData) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        ensureChannel(notificationManager)

        val openAppIntent = Intent(context, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 关闭方式说明：
        // 1. 不再 setOngoing(true) —— 用户可以直接滑动清掉（这是最可靠的关闭入口）；
        // 2. setTimeoutAfter —— 超时后系统自动收起，避免忘关时长期驻留；
        // 3. 退出内置举报页时由 WebReportActivity 主动 dismiss。
        //
        // 注意：不能靠再加一个「完成」按钮。标准通知模板最多只渲染 3 个 action，
        // 第 4 个加了也不会显示。
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("12321举报助手")
            .setContentText("下拉通知，复制号码、短信内容和接收时间；左右滑动可关闭")
            .setContentIntent(openAppPendingIntent)
            .addAction(copyAction(context, 1, "复制号码", "发送方号码", data.phoneNumber))
            .addAction(copyAction(context, 2, "复制内容", "短信内容", data.smsContent))
            .addAction(copyAction(context, 3, "复制时间", "接收时间", data.receiveTime))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setTimeoutAfter(AUTO_CANCEL_DELAY_MS)
        }

        val notification = builder.build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun dismiss(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "举报复制助手",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "在12321网页填写时快速复制举报信息"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun copyAction(
        context: Context,
        requestCode: Int,
        title: String,
        label: String,
        text: String
    ): Notification.Action {
        val intent = Intent(context, CopyReportFieldReceiver::class.java).apply {
            action = CopyReportFieldReceiver.ACTION_COPY_REPORT_FIELD
            putExtra(CopyReportFieldReceiver.EXTRA_LABEL, label)
            putExtra(CopyReportFieldReceiver.EXTRA_TEXT, text)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Action.Builder(
            R.drawable.ic_report,
            title,
            pendingIntent
        ).build()
    }
}
