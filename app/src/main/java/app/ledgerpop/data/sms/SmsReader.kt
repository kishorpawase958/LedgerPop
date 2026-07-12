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
     * Reads SMS from inbox.
     * Filtering is handled by SmsFilter inside SmsImporter.
     */
    open fun readTransactionSms(since: Long = 0): List<SmsMessage> {
        // Hard check for permission before any query
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }

        val resolver = context.contentResolver ?: return emptyList()

        val messages = mutableListOf<SmsMessage>()
        val uri = Uri.parse("content://sms/inbox")
        // Use date_sent if available as it matches the SC timestamp used by SmsReceiver
        val projection = arrayOf("address", "body", "date", "date_sent")
        
        val selection = if (since > 0) "date > ?" else null
        val selectionArgs = if (since > 0) arrayOf(since.toString()) else null

        var cursor: Cursor? = null
        try {
            cursor = resolver.query(uri, projection, selection, selectionArgs, "date DESC")
            cursor?.use {
                val addressIdx = it.getColumnIndexOrThrow("address")
                val bodyIdx = it.getColumnIndexOrThrow("body")
                val dateIdx = it.getColumnIndexOrThrow("date")
                val dateSentIdx = it.getColumnIndex("date_sent")

                while (it.moveToNext()) {
                    val sender = it.getString(addressIdx) ?: continue
                    val body = it.getString(bodyIdx) ?: continue
                    
                    var timestamp = it.getLong(dateIdx)
                    if (dateSentIdx != -1) {
                        val sent = it.getLong(dateSentIdx)
                        if (sent > 0) timestamp = sent
                    }

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