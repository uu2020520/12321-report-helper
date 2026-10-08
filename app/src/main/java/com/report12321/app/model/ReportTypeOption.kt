package com.report12321.app.model

data class ReportTypeOption(
    val code: String,
    val name: String,
    val description: String
) {
    companion object {
        fun getAll(): List<ReportTypeOption> {
            return listOf(
                ReportTypeOption("spam_sms", "垃圾短信", "营销、诱导、骚扰类短信"),
                ReportTypeOption("harass_call", "骚扰电话", "频繁来电、响一声、推销电话"),
                ReportTypeOption("fraud", "诈骗信息", "冒充客服、公检法、中奖等诈骗内容"),
                ReportTypeOption("ad", "广告推销", "未经同意的广告营销"),
                ReportTypeOption("loan", "贷款营销", "网贷、放款、额度营销"),
                ReportTypeOption("gambling", "博彩赌博", "赌博、博彩、投注相关信息"),
                ReportTypeOption("illegal", "违法违规", "办证、发票、灰产等信息")
            )
        }
    }
}
