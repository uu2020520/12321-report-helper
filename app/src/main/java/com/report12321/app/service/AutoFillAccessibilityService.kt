package com.report12321.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.report12321.app.R
import com.report12321.app.ui.MainActivity
import com.report12321.app.util.ReportDataHolder

/**
 * 12321一键举报核心无障碍服务
 *
 * 工作流程：
 * 1. 用户在App中选择骚扰号码 → 点击"一键举报"
 * 2. App将举报数据存入 ReportDataHolder，并跳转到12321网页
 * 3. 本服务监听页面加载完成事件
 * 4. 自动识别页面中的输入框（号码、内容、类型等）
 * 5. 逐个填入数据，最后点击提交按钮
 * 6. 如果遇到验证码，通知用户手动完成最后一步
 */
class AutoFillAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoFillService"

        // 12321 官方举报网址
        const val URL_12321_REPORT = "https://m.12321.cn/"

        private val SMS_REPORT_ENTRY_KEYWORDS = listOf(
            "投诉非应邀商业短信", "垃圾短信", "非应邀商业短信"
        )
        private val AGREE_KEYWORDS = listOf(
            "我同意", "同意"
        )

        // 12321 网页中“发送方号码”字段的常见文案。
        // 不使用泛化的“手机号/号码”，避免把垃圾短信发送方误填到“您的手机号”。
        private val SENDER_PHONE_KEYWORDS = listOf(
            "发送方号码", "发送垃圾短信的号码", "垃圾短信的号码", "骚扰号码", "举报号码",
            "发送方", "sender", "source number", "from number"
        )
        private val CONTENT_KEYWORDS = listOf(
            "长按粘贴短信内容", "短信内容", "垃圾短信内容", "投诉内容", "举报内容",
            "内容至少15个字以上", "内容", "描述", "详细", "说明",
            "content", "description", "detail", "message"
        )
        private val TYPE_KEYWORDS = listOf(
            "类型", "举报类型", "骚扰类型", "分类",
            "type", "category"
        )
        // 服务实例（用于检查服务是否运行）
        private var instance: AutoFillAccessibilityService? = null

        /**
         * 检查无障碍服务是否正在运行
         */
        fun isRunning(): Boolean = instance != null

        /**
         * 获取服务实例
         */
        fun getInstance(): AutoFillAccessibilityService? = instance
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var notificationManager: NotificationManager

    private var isAutoFilling = false
    private var fillRetryCount = 0
    private val MAX_RETRY = 3
    private var autoFillStartedAt = 0L
    private val MAX_AUTOFILL_WINDOW_MS = 120_000L

    // 通知相关常量
    private val SERVICE_CHANNEL_ID = "autofill_service_channel"
    private val RESULT_CHANNEL_ID = "report_result_channel"
    private val RESULT_NOTIFICATION_ID = 1002

    // ==================== 生命周期方法 ====================

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        notificationManager = getSystemService(NotificationManager::class.java)

        Log.d(TAG, "无障碍服务已连接")

        // 配置服务信息
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.DEFAULT or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }

        // 创建通知渠道（供填表结果通知使用）
        // 注意：这里不再发"服务运行中"的常驻通知。
        // 它此前用 notify() 而非 startForeground()，既不是真正的前台服务，又无法被用户划掉，
        // 而服务运行状态现在已经显示在 MainActivity 的 Toolbar 副标题上。
        createNotificationChannel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !isAutoFilling) return

        // 只处理窗口状态变化和内容变化事件
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // 只要处于自动填表流程，就在窗口变化后尝试。
                // 不限定浏览器包名，兼容系统浏览器、内置 WebView、微信/其他承载页。
                scheduleAutoFillAttempts()
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isAutoFilling = false
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "无障碍服务已销毁")
    }

    // ==================== 自动填表核心逻辑 ====================

    /**
     * 启动自动填表流程
     * 由 Activity 调用
     */
    fun startAutoFill() {
        // 先清掉上一轮遗留的延时任务，避免重复调用时任务堆积
        handler.removeCallbacksAndMessages(null)
        isAutoFilling = true
        fillRetryCount = 0
        autoFillStartedAt = System.currentTimeMillis()
        Log.d(TAG, "启动自动填表流程")
        scheduleAutoFillAttempts()
    }

    /**
     * 停止自动填表
     *
     * 切换到内置网页通道时调用，保证两条填表通道互斥，不会同时写同一批字段。
     */
    fun stopAutoFill() {
        if (!isAutoFilling) return
        isAutoFilling = false
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "停止自动填表流程")
    }

    /**
     * 结束自动填表流程并清理所有未执行的延时任务
     */
    private fun finishAutoFill() {
        isAutoFilling = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun scheduleAutoFillAttempts() {
        listOf(1200L, 3000L, 5500L, 8500L, 12000L, 18000L, 30000L, 45000L, 60000L, 90000L).forEach { delay ->
            handler.postDelayed({
                if (isAutoFilling) {
                    tryAutoFill()
                }
            }, delay)
        }
    }

    private fun handlePreReportPages(rootNode: AccessibilityNodeInfo): Boolean {
        if (clickFirstMatchingNode(rootNode, SMS_REPORT_ENTRY_KEYWORDS, exactPreferred = true)) {
            Log.d(TAG, "已点击垃圾短信投诉入口")
            handler.postDelayed({
                if (isAutoFilling) {
                    tryAutoFill()
                }
            }, 1800)
            return true
        }

        if (containsAnyText(rootNode, listOf("投诉须知")) &&
            clickFirstMatchingNode(rootNode, AGREE_KEYWORDS, exactPreferred = true)
        ) {
            Log.d(TAG, "已点击投诉须知同意按钮")
            handler.postDelayed({
                if (isAutoFilling) {
                    tryAutoFill()
                }
            }, 1800)
            return true
        }

        return false
    }

    /**
     * 尝试自动填表
     * 在检测到12321页面加载后调用
     */
    private fun tryAutoFill() {
        if (!isAutoFilling) return

        val reportData = ReportDataHolder.reportData
        if (reportData == null) {
            Log.w(TAG, "没有举报数据，跳过自动填表")
            finishAutoFill()
            return
        }

        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            Log.w(TAG, "无法获取页面根节点")
            waitForReportPage("无法获取页面根节点")
            return
        }

        try {
            Log.d(TAG, "开始自动填表，号码: ${reportData.phoneNumber}")

            if (handlePreReportPages(rootNode)) {
                return
            }

            // 第1步：查找并填写“发送方号码”输入框
            val phoneFilled = fillFieldByKeywords(rootNode, SENDER_PHONE_KEYWORDS, reportData.phoneNumber)
            Log.d(TAG, "号码填入${if (phoneFilled) "成功" else "失败"}")

            // 第2步：查找并填写短信内容输入框
            val contentText = reportData.smsContent.ifBlank { buildContentText(reportData) }
            val contentFilled = fillFieldByKeywords(rootNode, CONTENT_KEYWORDS, contentText)
            Log.d(TAG, "内容填入${if (contentFilled) "成功" else "失败"}")

            // 第3步：尝试选择举报类型
            selectReportType(rootNode, reportData.reportTypeName)

            if (phoneFilled && contentFilled) {
                val message = "已尝试填入发送方号码和短信内容，请补充手机号、验证码和接收时间后提交"
                finishAutoFill()
                showResultNotification(NotificationType.NEED_MANUAL_ACTION, message)
                return
            }

            if (phoneFilled || contentFilled) {
                retryPartialOrFinish(phoneFilled, contentFilled)
                return
            }

            waitForReportPage("未找到12321页面中的发送方号码或短信内容输入框")

        } catch (e: Exception) {
            Log.e(TAG, "自动填表异常", e)
            retryOrFail("自动填表异常: ${e.message}")
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * 根据关键词查找并填写输入框
     *
     * 查找策略：
     * 1. 先查找 hint/placeholder/contentDescription 包含关键词的 EditText
     * 2. 再查找关键词附近的 EditText（通过兄弟节点和父节点）
     * 3. 最后查找所有 EditText 并按位置匹配
     *
     * @return 是否成功填入
     */
    private fun fillFieldByKeywords(
        rootNode: AccessibilityNodeInfo,
        keywords: List<String>,
        text: String
    ): Boolean {
        val inputNodes = findAllInputNodes(rootNode)

        // 策略1：通过 hint/placeholder/contentDescription/text 查找输入节点。
        for (inputNode in inputNodes) {
            val combinedText = getNodeSearchText(inputNode)

            if (matchesAny(combinedText, keywords)) {
                val success = fillInputNode(inputNode, text)
                if (success) return true
            }
        }

        // 策略2：通过标签文本查找附近的 EditText
        val labelNodes = findNodesByText(rootNode, keywords)
        for (labelNode in labelNodes) {
            if (isInputNode(labelNode)) {
                val success = fillInputNode(labelNode, text)
                if (success) return true
            }

            val nearbyInput = findNearbyInputNode(labelNode)
            if (nearbyInput != null) {
                val success = fillInputNode(nearbyInput, text)
                if (success) return true
            }
        }

        // 策略3：点击匹配到的文字节点或其可点击父节点后尝试粘贴。
        // 部分移动网页会把输入框暴露成普通 View，点击后才进入可编辑状态。
        for (labelNode in labelNodes) {
            val clickable = findClickableSelfOrParent(labelNode)
            if (clickable != null && fillInputNode(clickable, text)) {
                return true
            }
        }

        return false
    }

    /**
     * 查找所有可能的输入节点
     */
    private fun findAllInputNodes(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()

        if (isInputNode(node)) {
            result.add(node)
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                result.addAll(findAllInputNodes(child))
            }
        }

        return result
    }

    private fun isInputNode(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString().orEmpty()
        return node.isEditable ||
                className == "android.widget.EditText" ||
                className == "android.widget.AutoCompleteTextView" ||
                className.contains("EditText", ignoreCase = true)
    }

    private fun getNodeSearchText(node: AccessibilityNodeInfo): String {
        val hint = node.hintText?.toString().orEmpty()
        val contentDesc = node.contentDescription?.toString().orEmpty()
        val textAttr = node.text?.toString().orEmpty()
        val viewId = node.viewIdResourceName.orEmpty()
        return "$hint $contentDesc $textAttr $viewId".lowercase()
    }

    private fun matchesAny(text: String, keywords: List<String>): Boolean {
        return keywords.any { text.contains(it.lowercase()) }
    }

    /**
     * 查找包含关键词的文本节点
     */
    private fun findNodesByText(
        node: AccessibilityNodeInfo,
        keywords: List<String>
    ): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()

        val nodeText = node.text?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        val hint = node.hintText?.toString()?.lowercase() ?: ""

        if (keywords.any {
                val keyword = it.lowercase()
                nodeText.contains(keyword) || contentDesc.contains(keyword) || hint.contains(keyword)
            }) {
            result.add(node)
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                result.addAll(findNodesByText(child, keywords))
            }
        }

        return result
    }

    private fun clickFirstMatchingNode(
        rootNode: AccessibilityNodeInfo,
        keywords: List<String>,
        exactPreferred: Boolean = false
    ): Boolean {
        val nodes = findNodesByText(rootNode, keywords)
        val orderedNodes = if (exactPreferred) {
            nodes.sortedByDescending { node ->
                val text = node.text?.toString().orEmpty()
                val desc = node.contentDescription?.toString().orEmpty()
                keywords.any { text == it || desc == it } ||
                        text.contains("投诉非应邀商业短信") ||
                        desc.contains("投诉非应邀商业短信")
            }
        } else {
            nodes
        }

        for (node in orderedNodes) {
            val text = node.text?.toString().orEmpty()
            if (text.contains("不同意")) {
                continue
            }

            val clickable = findClickableSelfOrParent(node)
            if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
        }

        return false
    }

    private fun containsAnyText(rootNode: AccessibilityNodeInfo, keywords: List<String>): Boolean {
        return findNodesByText(rootNode, keywords).isNotEmpty()
    }

    /**
     * 查找标签节点附近的输入节点
     * 查找策略：先找兄弟节点，再找父节点的其他子节点
     */
    private fun findNearbyInputNode(labelNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 尝试在兄弟节点中查找
        val parent = labelNode.parent ?: return null

        for (i in 0 until parent.childCount) {
            val sibling = parent.getChild(i) ?: continue
            if (isInputNode(sibling)) {
                return sibling
            }
        }

        val inputInParent = findFirstInputNode(parent)
        if (inputInParent != null && inputInParent != labelNode) {
            return inputInParent
        }

        // 尝试在父节点的下一个兄弟中查找
        val grandParent = parent.parent
        if (grandParent != null) {
            val parentIndex = findChildIndex(grandParent, parent)
            if (parentIndex >= 0 && parentIndex + 1 < grandParent.childCount) {
                val nextSibling = grandParent.getChild(parentIndex + 1)
                if (nextSibling != null) {
                    val input = findFirstInputNode(nextSibling)
                    if (input != null) return input
                }
            }
        }

        return null
    }

    /**
     * 查找节点在父节点中的索引
     */
    private fun findChildIndex(parent: AccessibilityNodeInfo, child: AccessibilityNodeInfo): Int {
        for (i in 0 until parent.childCount) {
            if (parent.getChild(i) == child) return i
        }
        return -1
    }

    /**
     * 在节点树中查找第一个输入节点
     */
    private fun findFirstInputNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (isInputNode(node)) {
            return node
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                val result = findFirstInputNode(child)
                if (result != null) return result
            }
        }
        return null
    }

    private fun findClickableSelfOrParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 4) {
            if (current.isClickable || current.isFocusable || current.isEditable) {
                return current
            }
            current = current.parent
            depth++
        }
        return null
    }

    /**
     * 向 EditText 填入文本
     *
     * 使用多种方式尝试填入：
     * 1. ACTION_SET_TEXT（Android 21+）
     * 2. 通过剪贴板 + ACTION_PASTE
     * 3. 通过 Bundle + ACTION_SET_TEXT
     */
    private fun fillInputNode(node: AccessibilityNodeInfo, text: String): Boolean {
        if (!node.isEnabled) {
            Log.w(TAG, "输入节点不可用")
            return false
        }

        // 先聚焦
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        // 方式1：ACTION_SET_TEXT（最可靠）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            val result = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (result) {
                Log.d(TAG, "通过 ACTION_SET_TEXT 填入成功: $text")
                return true
            }
        }

        // 方式2：通过剪贴板粘贴
        try {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("text", text))

            val pasted = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            if (pasted) {
                Log.d(TAG, "通过剪贴板粘贴填入: $text")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "剪贴板粘贴失败", e)
        }

        return false
    }

    /**
     * 选择举报类型
     * 尝试在 Spinner / RadioGroup / CheckBox 中选择对应类型
     */
    private fun selectReportType(rootNode: AccessibilityNodeInfo, typeName: String): Boolean {
        // 查找包含类型关键词的节点
        val typeNodes = findNodesByText(rootNode, TYPE_KEYWORDS)

        for (typeNode in typeNodes) {
            // 查找附近的可点击节点
            val parent = typeNode.parent ?: continue

            for (i in 0 until parent.childCount) {
                val child = parent.getChild(i) ?: continue
                val childText = child.text?.toString() ?: ""

                if (childText.contains(typeName) && child.isClickable) {
                    child.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d(TAG, "选择举报类型: $typeName")
                    return true
                }
            }
        }

        // 尝试查找 Spinner 并点击
        val spinners = findNodesByClassName(rootNode, "android.widget.Spinner")
        if (spinners.isNotEmpty()) {
            spinners[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "点击了Spinner，等待弹出选项")

            // 延迟后选择对应选项
            handler.postDelayed({
                val newRoot = rootInActiveWindow
                if (newRoot != null) {
                    val options = findNodesByText(newRoot, listOf(typeName))
                    if (options.isNotEmpty()) {
                        options[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        Log.d(TAG, "从Spinner中选择了: $typeName")
                    }
                    newRoot.recycle()
                }
            }, 500)
            return true
        }

        Log.d(TAG, "未找到举报类型选择器")
        return false
    }

    /**
     * 根据类名查找节点
     */
    private fun findNodesByClassName(
        node: AccessibilityNodeInfo,
        className: String
    ): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()

        if (node.className?.toString() == className) {
            result.add(node)
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                result.addAll(findNodesByClassName(child, className))
            }
        }

        return result
    }

    /**
     * 构建举报内容文本
     */
    private fun buildContentText(data: ReportDataHolder.ReportData): String {
        val sb = StringBuilder()
        sb.append("举报类型：${data.reportTypeName}\n")
        sb.append("骚扰号码：${data.phoneNumber}\n")
        sb.append("收到时间：${data.receiveTime}\n")

        if (data.smsContent.isNotEmpty()) {
            sb.append("短信内容：${data.smsContent}\n")
        }

        if (data.callDuration.isNotEmpty()) {
            sb.append("通话时长：${data.callDuration}\n")
        }

        if (data.remark.isNotEmpty()) {
            sb.append("备注：${data.remark}\n")
        }

        return sb.toString().trim()
    }

    /**
     * 重试或失败
     */
    private fun retryOrFail(reason: String) {
        fillRetryCount++
        if (fillRetryCount < MAX_RETRY) {
            Log.d(TAG, "重试自动填表 ($fillRetryCount/$MAX_RETRY)")
            handler.postDelayed({
                tryAutoFill()
            }, 3000)
        } else {
            Log.e(TAG, "自动填表失败: $reason")
            finishAutoFill()
            showResultNotification(NotificationType.FAILED, reason)
        }
    }

    private fun waitForReportPage(reason: String) {
        if (!isWithinAutoFillWindow()) {
            Log.e(TAG, "等待填写页超时: $reason")
            finishAutoFill()
            showResultNotification(
                NotificationType.NEED_MANUAL_ACTION,
                "未能自动识别12321填写页，请手动填写发送方号码和短信内容"
            )
            return
        }

        Log.d(TAG, "等待12321填写页: $reason")
        handler.postDelayed({
            if (isAutoFilling) {
                tryAutoFill()
            }
        }, 2500)
    }

    private fun isWithinAutoFillWindow(): Boolean {
        return System.currentTimeMillis() - autoFillStartedAt <= MAX_AUTOFILL_WINDOW_MS
    }

    private fun retryPartialOrFinish(phoneFilled: Boolean, contentFilled: Boolean) {
        fillRetryCount++
        if (fillRetryCount < MAX_RETRY && isWithinAutoFillWindow()) {
            Log.d(TAG, "部分字段填入成功，继续重试 ($fillRetryCount/$MAX_RETRY)")
            handler.postDelayed({
                tryAutoFill()
            }, 1800)
            return
        }

        val message = when {
            phoneFilled -> "已尝试填入发送方号码，请手动粘贴短信内容并完成验证码"
            contentFilled -> "已尝试填入短信内容，请手动填写发送方号码并完成验证码"
            else -> "请手动填写发送方号码、短信内容和验证码"
        }
        finishAutoFill()
        showResultNotification(NotificationType.NEED_MANUAL_ACTION, message)
    }

    // ==================== 通知相关 ====================

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                "举报辅助服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持举报辅助服务在后台运行"
                setShowBadge(false)
            }

            val resultChannel = NotificationChannel(
                RESULT_CHANNEL_ID,
                "举报结果",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "举报提交结果通知"
            }

            notificationManager.createNotificationChannels(listOf(serviceChannel, resultChannel))
        }
    }

    /**
     * 显示举报结果通知
     */
    private fun showResultNotification(type: NotificationType, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val (title, icon) = when (type) {
                NotificationType.SUCCESS -> "举报提交成功" to R.drawable.ic_check_circle
                NotificationType.NEED_MANUAL_ACTION -> "需要手动操作" to R.drawable.ic_warning
                NotificationType.FAILED -> "自动填表失败" to R.drawable.ic_error
            }

            val notification = Notification.Builder(this, RESULT_CHANNEL_ID)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(RESULT_NOTIFICATION_ID, notification)
        }

        // 清除举报数据
        ReportDataHolder.clearReportData()
    }

    /**
     * 通知类型枚举
     */
    enum class NotificationType {
        SUCCESS,           // 自动填表并提交成功
        NEED_MANUAL_ACTION, // 需要用户手动操作（如输入验证码）
        FAILED             // 自动填表失败
    }
}
