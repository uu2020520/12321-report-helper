package com.report12321.app.model

import java.io.Serializable

data class SpamItem(
    val id: Long,
    val phoneNumber: String,
    val content: String,
    val time: String,
    val timestamp: Long,
    val type: SpamType,
    val spamType: String,
    val spamLevel: Int,
    val callDuration: String = "",
    val callType: String = ""
) : Serializable {

    fun getSpamTypeName(): String {
        return when (spamType) {
            "fraud" -> "疑似诈骗"
            "ad" -> "广告推销"
            "loan" -> "贷款营销"
            "gambling" -> "博彩赌博"
            "fake_cert" -> "办证发票"
            "harass_call" -> "骚扰电话"
            else -> if (type == SpamType.SMS) "垃圾短信" else "可疑电话"
        }
    }

    fun getSpamLevelText(): String {
        return when {
            spamLevel >= 80 -> "高风险 $spamLevel"
            spamLevel >= 50 -> "中风险 $spamLevel"
            else -> "低风险 $spamLevel"
        }
    }

    fun getReportTypeCode(): String {
        return when (spamType) {
            "fraud" -> "fraud"
            "loan" -> "loan"
            "gambling" -> "gambling"
            "fake_cert" -> "illegal"
            "ad" -> "ad"
            "harass_call" -> "harass_call"
            else -> if (type == SpamType.SMS) "spam_sms" else "harass_call"
        }
    }
}
