package com.bitdroid.dialer.data

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog

object CallLogRepo {
    private fun canRead(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    private fun kindOf(type: Int) = when (type) {
        CallLog.Calls.OUTGOING_TYPE -> CallKind.OUTGOING
        CallLog.Calls.MISSED_TYPE -> CallKind.MISSED
        CallLog.Calls.REJECTED_TYPE -> CallKind.REJECTED
        CallLog.Calls.BLOCKED_TYPE -> CallKind.BLOCKED
        CallLog.Calls.VOICEMAIL_TYPE -> CallKind.VOICEMAIL
        else -> CallKind.INCOMING
    }

    /** Recent calls, with back-to-back calls from the same number and kind folded together. */
    fun recents(context: Context, limit: Int = 300): List<CallEntry> {
        if (!canRead(context)) return emptyList()
        val out = ArrayList<CallEntry>()
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.NEW),
                null, null, "${CallLog.Calls.DATE} DESC",
            )?.use { c ->
                var n = 0
                while (c.moveToNext() && n++ < limit) {
                    val number = c.getString(1) ?: ""
                    val kind = kindOf(c.getInt(2))
                    val prev = out.lastOrNull()
                    if (prev != null && prev.kind == kind && Phone.same(prev.number.ifBlank { "?" }, number.ifBlank { "?" })) {
                        out[out.lastIndex] = prev.copy(count = prev.count + 1, isNew = prev.isNew || c.getInt(5) == 1)
                        continue
                    }
                    out += CallEntry(
                        id = c.getLong(0), number = number, contact = Contacts.lookup(context, number),
                        kind = kind, date = c.getLong(3), durationSec = c.getLong(4), count = 1, isNew = c.getInt(5) == 1,
                    )
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun unseenMissed(context: Context): Int {
        if (!canRead(context)) return 0
        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls._ID),
                "${CallLog.Calls.TYPE} = ${CallLog.Calls.MISSED_TYPE} AND ${CallLog.Calls.NEW} = 1", null, null,
            )?.use { it.count } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    fun markMissedSeen(context: Context) {
        try {
            context.contentResolver.update(
                CallLog.Calls.CONTENT_URI,
                ContentValues().apply {
                    put(CallLog.Calls.NEW, 0)
                    put(CallLog.Calls.IS_READ, 1)
                },
                "${CallLog.Calls.TYPE} = ${CallLog.Calls.MISSED_TYPE} AND ${CallLog.Calls.NEW} = 1", null,
            )
        } catch (_: Exception) {
        }
    }

    fun delete(context: Context, number: String) {
        try {
            context.contentResolver.delete(CallLog.Calls.CONTENT_URI, "${CallLog.Calls.NUMBER} = ?", arrayOf(number))
        } catch (_: Exception) {
        }
    }
}
