package com.report12321.app.util

import android.content.Context
import android.database.Cursor
import android.provider.CallLog
import android.provider.Telephony
import android.util.Log
import com.report12321.app.model.SpamItem
import com.report12321.app.model.SpamType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 骚扰信息检测器
 *
 * 负责从系统短信和通话记录中提取骚扰信息，
 * 并根据常见骚扰特征进行初步识别和分类。
 */
class SpamDetector(private val context: Context) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /**
     * 获取所有可疑短信
     * @param limit 最大返回条数
     */
    fun getSuspiciousSms(limit: Int = 50): List<SpamItem> {
        val items = mutableListOf<SpamItem>()

        try {
            val cursor: Cursor? = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE
                ),
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )

            cursor?.use {
                val idIndex = it.getColumnIndex(Telephony.Sms._ID)
                val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)

                var count = 0
                while (it.moveToNext() && count < limit) {
                    val id = it.getLong(idIndex)
                    val address = it.getString(addressIndex) ?: ""
                    val body = it.getString(bodyIndex) ?: ""
                    val date = it.getLong(dateIndex)

                    // 分析骚扰类型和等级
                    val (spamType, spamLevel) = analyzeSms(address, body)

                    // 只返回有骚扰嫌疑的短信（等级 > 0）
                    if (spamLevel > 0) {
                        items.add(
                            SpamItem(
                                id = id,
                                phoneNumber = address,
                                content = body,
                                time = dateFormat.format(Date(date)),
                                timestamp = date,
                                type = SpamType.SMS,
                                spamType = spamType,
                                spamLevel = spamLevel
                            )
                        )
                    }
                    count++
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "没有短信读取权限", e)
        } catch (e: Exception) {
            Log.e(TAG, "读取短信失败", e)
        }

        return items
    }

    /**
     * 获取所有可疑通话记录
     * @param limit 最大返回条数
     */
    fun getSuspiciousCalls(limit: Int = 50): List<SpamItem> {
        val items = mutableListOf<SpamItem>()

        try {
            val cursor: Cursor? = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls._ID,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.CACHED_NAME
                ),
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )

            cursor?.use {
                val idIndex = it.getColumnIndex(CallLog.Calls._ID)
                val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
                val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
                val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
                val nameIndex = it.getColumnIndex(CallLog.Calls.CACHED_NAME)

                var count = 0
                while (it.moveToNext() && count < limit) {
                    val id = it.getLong(idIndex)
                    val number = it.getString(numberIndex) ?: ""
                    val date = it.getLong(dateIndex)
                    val duration = it.getLong(durationIndex)
                    val callType = it.getInt(typeIndex)
                    val name = it.getString(nameIndex)

                    // 分析骚扰类型和等级
                    val (spamType, spamLevel) = analyzeCall(number, callType, duration, name)

                    // 只返回有骚扰嫌疑的通话（等级 > 0）
                    if (spamLevel > 0) {
                        val callTypeName = when (callType) {
                            CallLog.Calls.INCOMING_TYPE -> "来电"
                            CallLog.Calls.OUTGOING_TYPE -> "去电"
                            CallLog.Calls.MISSED_TYPE -> "未接"
                            CallLog.Calls.REJECTED_TYPE -> "已拒接"
                            else -> "未知"
                        }

                        items.add(
                            SpamItem(
                                id = id,
                                phoneNumber = number,
                                content = "骚扰电话 - $callTypeName",
                                time = dateFormat.format(Date(date)),
                                timestamp = date,
                                type = SpamType.CALL,
                                spamType = spamType,
                                spamLevel = spamLevel,
                                callDuration = formatDuration(duration),
                                callType = callTypeName
                            )
                        )
                    }
                    count++
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "没有通话记录读取权限", e)
        } catch (e: Exception) {
            Log.e(TAG, "读取通话记录失败", e)
        }

        return items
    }

    /**
     * 分析短信的骚扰类型和等级
     * @return Pair(骚扰类型代码, 骚扰等级0-100)
     */
    private fun analyzeSms(address: String, body: String): Pair<String, Int> {
        var level = 0
        var spamType = "spam_sms"

        // 诈骗关键词检测
        if (containsAny(body, FRAUD_KEYWORDS)) {
            level += 60
            spamType = "fraud"
        }

        // 广告推销关键词检测
        if (containsAny(body, AD_KEYWORDS)) {
            level += 40
            if (spamType == "spam_sms") spamType = "ad"
        }

        // 贷款关键词检测
        if (containsAny(body, LOAN_KEYWORDS)) {
            level += 50
            spamType = "loan"
        }

        // 赌博关键词检测
        if (containsAny(body, GAMBLING_KEYWORDS)) {
            level += 70
            spamType = "gambling"
        }

        // 办证/发票关键词检测
        if (containsAny(body, FAKE_CERT_KEYWORDS)) {
            level += 55
            spamType = "fake_cert"
        }

        // 含有"退订"字样（商业短信标志）
        if (body.contains("退订") || body.contains("回复TD") || body.contains("回TD")) {
            level += 20
            if (spamType == "spam_sms") spamType = "ad"
        }

        // 含有链接（可疑）
        if (body.contains("http://") || body.contains("https://") || body.contains("t.cn")) {
            level += 15
        }

        // 号码特征分析
        if (address.length > 11) {
            // 长号码（如1069开头的短信端口）
            level += 10
        }

        return Pair(spamType, level.coerceIn(0, 100))
    }

    /**
     * 分析通话的骚扰类型和等级
     * @return Pair(骚扰类型代码, 骚扰等级0-100)
     */
    private fun analyzeCall(number: String, callType: Int, duration: Long, name: String?): Pair<String, Int> {
        var level = 0
        var spamType = "harass_call"

        // 未接来电
        if (callType == CallLog.Calls.MISSED_TYPE) {
            level += 20
        }

        // 极短通话（可能是骚扰/诈骗）
        if (duration in 1..5) {
            level += 25
        }

        // 陌生号码（无通讯录名称）
        if (name.isNullOrBlank()) {
            level += 15
        }

        // 号码特征分析
        if (number.startsWith("400") || number.startsWith("800")) {
            // 400/800号码多为推销
            level += 35
            spamType = "ad"
        }

        if (number.startsWith("00")) {
            // 境外号码
            level += 40
            spamType = "fraud"
        }

        if (number.startsWith("95") && number.length <= 5) {
            // 银行/金融短号
            level += 10
        }

        // 虚拟运营商号段
        val virtualPrefixes = listOf("170", "171", "165", "167", "168", "162")
        if (virtualPrefixes.any { number.startsWith(it) }) {
            level += 25
            spamType = "fraud"
        }

        return Pair(spamType, level.coerceIn(0, 100))
    }

    /**
     * 格式化通话时长
     */
    private fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return "未接"
        val min = seconds / 60
        val sec = seconds % 60
        return if (min > 0) "${min}分${sec}秒" else "${sec}秒"
    }

    /**
     * 检查文本是否包含列表中的任一关键词
     */
    private fun containsAny(text: String, keywords: List<String>): Boolean {
        return keywords.any { text.contains(it) }
    }

    companion object {
        private const val TAG = "SpamDetector"

        // 诈骗关键词
        private val FRAUD_KEYWORDS = listOf(
            "中奖", "领取", "冻结", "解冻", "异常", "验证身份",
            "安全账户", "转账", "汇款", "公检法", "公安局",
            "法院传票", "涉嫌", "洗钱", "退税", "补贴",
            "冒充", "客服", "退款", "刷单", "兼职",
            "日赚", "月入", "投资回报", "高收益"
        )

        // 广告推销关键词
        private val AD_KEYWORDS = listOf(
            "优惠", "折扣", "促销", "特价", "限时",
            "免费", "赠送", "体验", "办理", "升级",
            "贷款利率", "低息", "免息", "分期",
            "退订", "回复td", "回td", "回n"
        )

        // 贷款关键词
        private val LOAN_KEYWORDS = listOf(
            "贷款", "借款", "放款", "额度", "信用贷",
            "网贷", "小额", "极速到账", "无需抵押",
            "黑户", "花户", "秒批", "秒下"
        )

        // 赌博关键词
        private val GAMBLING_KEYWORDS = listOf(
            "棋牌", "百家乐", "老虎机", "投注",
            "下注", "博彩", "赌场", "彩金",
            "提现", "充值", "代理"
        )

        // 办证/代开发票关键词
        private val FAKE_CERT_KEYWORDS = listOf(
            "代开", "发票", "办证", "刻章",
            "假证", "文凭", "学历", "资格证"
        )
    }
}
