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

    data class ReportRecord(
        val data: ReportData,
        val createdAt: Long = System.currentTimeMillis()
    )

    var reportData: ReportData? = null
    private val history = mutableListOf<ReportRecord>()

    fun addHistory(record: ReportRecord) {
        history.add(0, record)
        if (history.size > 100) {
            history.removeLast()
        }
    }

    fun getHistory(): List<ReportRecord> = history.toList()

    fun clearReportData() {
        reportData = null
    }
}
