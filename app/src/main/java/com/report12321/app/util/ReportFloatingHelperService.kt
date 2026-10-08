package com.report12321.app.util

import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import com.report12321.app.R

class ReportFloatingHelperService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    private val handler = Handler(Looper.getMainLooper())
    private val autoStopRunnable = Runnable { stopSelf() }

    private var phoneNumber = ""
    private var smsContent = ""
    private var receiveTime = ""

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 系统可能在服务被杀后用 null intent 重建它。此时没有任何举报数据，
        // 继续跑只会显示一个点了没反应的空悬浮窗，所以直接结束。
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        phoneNumber = intent.getStringExtra(EXTRA_PHONE).orEmpty()
        smsContent = intent.getStringExtra(EXTRA_CONTENT).orEmpty()
        receiveTime = intent.getStringExtra(EXTRA_TIME).orEmpty()

        showFloatingHelper()
        scheduleAutoStop()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(autoStopRunnable)
        removeFloatingHelper()
        super.onDestroy()
    }

    /**
     * 自动关闭：避免用户忘了点「关闭」时悬浮窗一直挂在屏幕上
     */
    private fun scheduleAutoStop() {
        handler.removeCallbacks(autoStopRunnable)
        handler.postDelayed(autoStopRunnable, AUTO_STOP_DELAY_MS)
    }

    private fun showFloatingHelper() {
        if (floatingView != null) {
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = buildFloatingView()

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = 12
            y = 0
        }

        try {
            windowManager?.addView(floatingView, layoutParams)
        } catch (e: Exception) {
            Toast.makeText(this, "悬浮助手显示失败，请检查悬浮窗权限", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun buildFloatingView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(10, 10, 10, 10)
            setBackgroundResource(R.drawable.card_background)
        }

        root.addView(createButton("号码") {
            copy("发送方号码", phoneNumber)
        })
        root.addView(createButton("内容") {
            copy("短信内容", smsContent)
        })
        root.addView(createButton("时间") {
            copy("接收时间", receiveTime)
        })
        root.addView(createButton("关闭") {
            stopSelf()
        })

        attachDrag(root)
        return root
    }

    private fun createButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            textSize = 13f
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(18, 8, 18, 8)
            setOnClickListener { onClick() }
        }
    }

    private fun attachDrag(view: View) {
        var startX = 0
        var startY = 0
        var touchStartX = 0f
        var touchStartY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = layoutParams.x
                    startY = layoutParams.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = startX - (event.rawX - touchStartX).toInt()
                    layoutParams.y = startY + (event.rawY - touchStartY).toInt()
                    windowManager?.updateViewLayout(floatingView, layoutParams)
                    true
                }
                else -> false
            }
        }
    }

    private fun copy(label: String, text: String) {
        if (text.isBlank()) {
            Toast.makeText(this, "没有可复制的$label", Toast.LENGTH_SHORT).show()
            return
        }

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "已复制$label", Toast.LENGTH_SHORT).show()
    }

    private fun removeFloatingHelper() {
        val view = floatingView ?: return
        try {
            windowManager?.removeView(view)
        } catch (_: Exception) {
        } finally {
            floatingView = null
        }
    }

    companion object {
        const val EXTRA_PHONE = "extra_phone"
        const val EXTRA_CONTENT = "extra_content"
        const val EXTRA_TIME = "extra_time"

        private const val AUTO_STOP_DELAY_MS = 10 * 60 * 1000L
    }
}
