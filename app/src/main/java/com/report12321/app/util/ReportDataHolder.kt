package com.report12321.app.util

object ReportDataHolder {
    data class ReportData(
        val phoneNumber: String,
        val reportType: String,
        val reportTypeName: String,
        val smsContent: String,
        val callDuration: String,
        val receiveTime: String,
        val remark: String,
        val source: String
    )

    var reportData: ReportData? = null

    fun clearReportData() {
        reportData = null
    }
}
