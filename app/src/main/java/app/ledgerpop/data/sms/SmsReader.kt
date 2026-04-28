package app.ledgerpop.data.sms

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri

data class SmsMessage(
    val sender: String,
    val body: String,
    val timestamp: Long
)

open class SmsReader(private val contentResolver: ContentResolver?) {

    /**
     * Reads ALL SMS from inbox.
     * Filtering is handled by SmsFilter inside SmsImporter.
     * Returns empty list if contentResolver is null (NoOpSmsReader case).
     */
    open fun readTransactionSms(): List<SmsMessage> {
        val resolver = contentResolver ?: return emptyList()

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