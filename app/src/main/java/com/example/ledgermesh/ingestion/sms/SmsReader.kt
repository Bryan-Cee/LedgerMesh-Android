package com.example.ledgermesh.ingestion.sms

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads SMS messages from the device inbox via the [Telephony.Sms] content
 * provider. Requires the `android.permission.READ_SMS` runtime permission.
 *
 * Results can be filtered by sender address and/or minimum timestamp to
 * support both full and incremental scans.
 */
@Singleton
class SmsReader @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /**
     * Read SMS messages from the inbox.
     *
     * @param senderAddresses If non-empty, only messages whose `address`
     *   field matches (case-insensitive, substring) one of these values are
     *   returned. If empty, all inbox messages are returned.
     * @param afterTimestamp If non-null, only messages received after this
     *   epoch-millis timestamp are returned.
     * @param limit Maximum number of messages to return (most recent first).
     * @return List of [SmsMessage] sorted by date descending.
     */
    fun readMessages(
        senderAddresses: List<String> = emptyList(),
        afterTimestamp: Long? = null,
        limit: Int = 5000
    ): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()
        val contentResolver: ContentResolver = context.contentResolver

        val uri: Uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )

        // Build selection clause
        val selectionParts = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()

        if (afterTimestamp != null) {
            selectionParts.add("${Telephony.Sms.DATE} > ?")
            selectionArgs.add(afterTimestamp.toString())
        }

        val selection = selectionParts.takeIf { it.isNotEmpty() }?.joinToString(" AND ")
        val args = selectionArgs.takeIf { it.isNotEmpty() }?.toTypedArray()

        val sortOrder = "${Telephony.Sms.DATE} DESC LIMIT $limit"

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, selection, args, sortOrder)

            cursor?.let { c ->
                val idIdx = c.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIdx = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = c.getColumnIndexOrThrow(Telephony.Sms.DATE)

                while (c.moveToNext()) {
                    val id = c.getLong(idIdx)
                    val address = c.getString(addressIdx) ?: continue
                    val body = c.getString(bodyIdx) ?: continue
                    val date = c.getLong(dateIdx)

                    // Client-side sender filter (content provider does not
                    // support case-insensitive LIKE across all devices reliably)
                    if (senderAddresses.isNotEmpty()) {
                        val matchesSender = senderAddresses.any { sender ->
                            address.equals(sender, ignoreCase = true) ||
                                address.contains(sender, ignoreCase = true)
                        }
                        if (!matchesSender) continue
                    }

                    messages.add(SmsMessage(id, address, body, date))
                }
            }
        } finally {
            cursor?.close()
        }

        return messages
    }

    /**
     * Returns the deduplicated list of sender addresses from all built-in
     * profiles, useful for pre-filtering the inbox query.
     */
    fun getKnownSenders(): List<String> {
        return DefaultProfiles.getAll()
            .flatMap { it.senderAddresses }
            .distinct()
    }
}
