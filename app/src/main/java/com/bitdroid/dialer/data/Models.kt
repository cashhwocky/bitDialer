package com.bitdroid.dialer.data

import android.net.Uri

data class Contact(
    val address: String,
    val name: String?,
    val photoUri: Uri?,
    val lookupUri: Uri?,
) {
    val displayName: String get() = name ?: address
}

enum class CallKind { INCOMING, OUTGOING, MISSED, REJECTED, BLOCKED, VOICEMAIL }

data class CallEntry(
    val id: Long,
    val number: String,
    val contact: Contact,
    val kind: CallKind,
    val date: Long,
    val durationSec: Long,
    val count: Int,
    val isNew: Boolean,
)

data class Favorite(val contact: Contact, val number: String, val missed: Int)
