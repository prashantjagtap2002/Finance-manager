package com.example.financemanager.domain

object SmsAmountFormatter {
    fun number(value: String): String = Regex("[0-9][0-9,]*(?:\\.[0-9]{1,2})?")
        .find(value)
        ?.value
        ?.replace(",", "")
        ?: ""

    fun display(value: String): String {
        val number = number(value)
        return if (number.isBlank()) value.ifEmpty { "N/A" } else "Rs.$number"
    }
}
