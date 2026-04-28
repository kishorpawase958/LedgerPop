package app.ledgerpop.data.sms

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import androidx.core.content.ContextCompat
import android.Manifest

data class SmsMessage(
    val sender: String,
    val body: String,
    val timestamp: Long
)

open class SmsReader(private val context: Context) {

    /**
     * Reads ALL SMS from inbox.
     * Filtering is handled by SmsFilter inside SmsImporter.
     */
    open fun readTransactionSms(): List<SmsMessage> {
        // Hard check for permission before any query
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }

        val resolver = context.contentResolver ?: return emptyList()

        val messages = mutableListOf<SmsMessage>()
        val uri = Uri.parse("content://sms/inbox")
        val projection = arrayOf("address", "body", "date")

        var cursor: Cursor? = null
        try {
            cursor = resolver.query(uri, projection, null, null, "date DESC")
            cursor?.use {
                val addressIdx = it.getColumnIndexOrThrow("address")
                val bodyIdx = it.getColumnIndexOrThrow("body")
                val dateIdx = it.getColumnIndexOrThrow("date")

                while (it.moveToNext()) {
                    val sender = it.getString(addressIdx) ?: continue
                    val body = it.getString(bodyIdx) ?: continue
                    val timestamp = it.getLong(dateIdx)
                    if (body.isBlank()) continue
                    messages.add(SmsMessage(sender, body, timestamp))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }

        return messages
    }
}