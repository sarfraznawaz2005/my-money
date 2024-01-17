package com.mahamsoft.expenseviewapp

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsReader(private val context: Context) {
    fun readAllSms(): List<SmsMessage> {
        val smsList = mutableListOf<SmsMessage>()
        val cursor: Cursor? = context.contentResolver.query(
            Uri.parse("content://sms/inbox"),
            null,
            null,
            null,
            null
        )

        cursor?.use {
            val indexBody = it.getColumnIndex(Telephony.Sms.BODY)
            val indexDate = it.getColumnIndex(Telephony.Sms.DATE)
            
            while (it.moveToNext()) {
                val smsBody = it.getString(indexBody)
                val smsDate = it.getLong(indexDate)
                val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(smsDate))
                val sms = SmsMessage(date, smsBody)

                //Log.d("SmsReader", "SMS Body: $smsBody")

                smsList.add(sms)
            }
        }

        return smsList
    }
}