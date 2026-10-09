package com.report12321.app.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.report12321.app.R
import com.report12321.app.model.ReportTypeOption
import com.report12321.app.model.SpamItem
import com.report12321.app.service.AutoFillAccessibilityService
import com.report12321.app.util.AccessibilityPrompts
import com.report12321.app.util.ReportFloatingHelperService
import com.report12321.app.util.ReportHelperNotifier
import com.report12321.app.util.ReportDataHolder
import com.report12321.app.util.ReportPrefs
import com.report12321.app.util.SystemBarInsets

/**
 * 举报详情页
 *
 * 展示骚扰信息详情，允许用户编辑举报内容，
 * 确认后一键跳转12321并自动填表
 */
class ReportActivity : AppCompatActivity() {

    private lateinit var spamItem: SpamItem

    private lateinit var textPhone: TextView
    private lateinit var textTime: TextView
    private lateinit var textContent: TextView
    private lateinit var textSpamType: TextView
    private lateinit var textSpamLevel: TextView
    private lateinit var spinnerReportType: Spinner
    private lateinit var editRemark: EditText
    private lateinit var editContent: EditText
    private lateinit var btnReport: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        // 设置 Toolbar（此前没有 Toolbar，supportActionBar 为 null，
        // 导致 setDisplayHomeAsUpEnabled 静默失效、返回按钮永远不出现）
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "举报详情"

        // 系统栏适配
        SystemBarInsets.applyTopPadding(toolbar)
        SystemBarInsets.applyBottomPadding(findViewById(R.id.scroll_view))
        SystemBarInsets.setStatusBarColor(window, this, R.color.primary, lightIcons = false)

        // 获取传入的骚扰信息
        @Suppress("DEPRECATION")
        spamItem = intent.getSerializableExtra("spam_item") as? SpamItem
            ?: run {
                Toast.makeText(this, "数据异常", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

        initViews()
        fillData()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun initViews() {
        textPhone = findViewById(R.id.text_phone)
        textTime = findViewById(R.id.text_time)
        textContent = findViewById(R.id.text_content)
        textSpamType = findViewById(R.id.text_spam_type)
        textSpamLevel = findViewById(R.id.text_spam_level)
        spinnerReportType = findViewById(R.id.spinner_report_type)
        editRemark = findViewById(R.id.edit_remark)
        editContent = findViewById(R.id.edit_content)
        btnReport = findViewById(R.id.btn_report)

        // 举报类型下拉框
        val reportTypes = ReportTypeOption.getAll()
        val typeNames = reportTypes.map { "${it.name} - ${it.description}" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, typeNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerReportType.adapter = adapter

        // 默认选中匹配的举报类型
        val defaultTypeIndex = reportTypes.indexOfFirst { it.code == spamItem.getReportTypeCode() }
        if (defaultTypeIndex >= 0) {
            spinnerReportType.setSelection(defaultTypeIndex)
        }

        // 一键举报按钮
        btnReport.setOnClickListener {
            startReport()
        }
    }

    private fun fillData() {
        textPhone.text = spamItem.phoneNumber
        textTime.text = spamItem.time
        textContent.text = spamItem.content
        textSpamType.text = spamItem.getSpamTypeName()
        textSpamLevel.text = spamItem.getSpamLevelText()

        // 编辑框预填内容
        editContent.setText(spamItem.content)
    }

    /**
     * 开始举报
     */
    private fun startReport() {
        // 获取选中的举报类型
        val reportTypes = ReportTypeOption.getAll()
        val selectedType = reportTypes[spinnerReportType.selectedItemPosition]

        // 构建举报数据
        val reportData = ReportDataHolder.ReportData(
            phoneNumber = spamItem.phoneNumber,
            reportType = selectedType.code,
            reportTypeName = selectedType.name,
            smsContent = editContent.text.toString(),
            callDuration = spamItem.callDuration,
            receiveTime = spamItem.time,
            remark = editRemark.text.toString(),
            source = if (spamItem.type == com.report12321.app.model.SpamType.SMS) "sms" else "call"
        )

        // 存入数据持有者
        ReportDataHolder.reportData = reportData

        // 半自动举报助手：通过通知栏提供复制按钮，用户在12321网页中手动粘贴。
        ReportHelperNotifier.show(this, reportData)
        if (!startFloatingHelperOrRequestPermission(reportData)) {
            return
        }

        // 两条通道互斥：同一时刻只会走一条
        if (ReportPrefs.useAccessibility(this)) {
            startAccessibilityFlow()
        } else {
            startWebViewFlow(reportData)
        }
    }

    /**
     * 通道一：无障碍服务自动填表 + 外部浏览器
     *
     * 走外部浏览器而不是内置 WebView，是因为无障碍服务能否捕获「自己 App 窗口」的
     * 事件在各厂商 ROM 上表现不一致，外部浏览器更可靠。
     * 举报数据已存入 ReportDataHolder，服务会自行读取。
     */
    private fun startAccessibilityFlow() {
        if (!AutoFillAccessibilityService.isRunning()) {
            AccessibilityPrompts.showEnableDialog(this)
            return
        }

        AutoFillAccessibilityService.getInstance()?.startAutoFill()
        startActivity(
            Intent(Intent.ACTION_VIEW, AutoFillAccessibilityService.URL_12321_REPORT.toUri())
        )
        Toast.makeText(this, "已打开12321，无障碍服务将尝试自动填写", Toast.LENGTH_LONG).show()
    }

    /**
     * 通道二：内置 WebView + 注入 JS 填表（默认）
     *
     * 同时停掉可能正在进行的无障碍填表，避免两条通道同时写同一批字段。
     */
    private fun startWebViewFlow(data: ReportDataHolder.ReportData) {
        AutoFillAccessibilityService.getInstance()?.stopAutoFill()

        val intent = Intent(this, WebReportActivity::class.java).apply {
            putExtra(WebReportActivity.EXTRA_PHONE, data.phoneNumber)
            putExtra(WebReportActivity.EXTRA_CONTENT, data.smsContent)
            putExtra(WebReportActivity.EXTRA_TIME, data.receiveTime)
        }
        startActivity(intent)

        Toast.makeText(this, "已打开12321，可用侧边助手或通知栏复制内容", Toast.LENGTH_LONG).show()
    }

    private fun startFloatingHelperOrRequestPermission(data: ReportDataHolder.ReportData): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
            val serviceIntent = Intent(this, ReportFloatingHelperService::class.java).apply {
                putExtra(ReportFloatingHelperService.EXTRA_PHONE, data.phoneNumber)
                putExtra(ReportFloatingHelperService.EXTRA_CONTENT, data.smsContent)
                putExtra(ReportFloatingHelperService.EXTRA_TIME, data.receiveTime)
            }
            startService(serviceIntent)
            Toast.makeText(this, "已开启侧边复制助手", Toast.LENGTH_SHORT).show()
            return true
        }

        Toast.makeText(this, "请允许悬浮窗权限，授权后再次点击一键举报可显示侧边助手", Toast.LENGTH_LONG).show()
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:$packageName".toUri()
        )
        startActivity(intent)
        return false
    }
}
