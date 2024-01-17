package com.mahamsoft.expenseviewapp

data class SmsMessage(
    val date: String,
    val text: String,
    val iscredit: Boolean = false,
    val amount: Double = 0.0
)
