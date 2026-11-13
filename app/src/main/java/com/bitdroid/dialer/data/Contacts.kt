package com.bitdroid.dialer.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.UserManager
import android.provider.ContactsContract
import java.util.concurrent.ConcurrentHashMap

object Contacts {
    private val cache = ConcurrentHashMap<String, Contact>()

    fun clearCache() = cache.clear()

    fun canRead(context: Context): Boolean {
        val um = context.getSystemService(UserManager::class.java)
        if (um != null && !um.isUserUnlocked) return false
        return context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    fun lookup(context: Context, rawNumber: String): Contact {
        val number = rawNumber.trim()
        cache[number]?.let { return it }
        var contact = Contact(number, null, null, null)
        if (canRead(context) && number.isNotBlank()) {
            try {
                val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
                context.contentResolver.query(
                    uri,
                    arrayOf(
                        ContactsContract.PhoneLookup._ID,
                        ContactsContract.PhoneLookup.DISPLAY_NAME,
                        ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI,
                        ContactsContract.PhoneLookup.LOOKUP_KEY,
                    ),
                    null, null, null,
                )?.use { c ->
                    if (c.moveToFirst()) {
                        contact = Contact(
                            number, c.getString(1), c.getString(2)?.let(Uri::parse),
                            ContactsContract.Contacts.getLookupUri(c.getLong(0), c.getString(3)),
                        )
                    }
                }
            } catch (_: Exception) {
            }
            cache[number] = contact
        }
        return contact
    }

    /** Unstars a contact so it leaves favorites. Needs WRITE_CONTACTS. */
    fun unstar(context: Context, lookupUri: Uri): Boolean = try {
        val values = android.content.ContentValues().apply { put(ContactsContract.Contacts.STARRED, 0) }
        context.contentResolver.update(lookupUri, values, null, null) > 0
    } catch (_: Exception) {
        false
    }

    /** Starred contacts with their first phone number, max [limit]. */
    fun favorites(context: Context, limit: Int = 6): List<Pair<Contact, String>> {
        if (!canRead(context)) return emptyList()
        val out = ArrayList<Pair<Contact, String>>()
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                    ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                    ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY,
                ),
                "${ContactsContract.CommonDataKinds.Phone.STARRED} = 1", null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE LOCALIZED ASC, ${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC",
            )?.use { c ->
                val seen = HashSet<Long>()
                while (c.moveToNext() && out.size < limit) {
                    val id = c.getLong(0)
                    if (!seen.add(id)) continue
                    val number = c.getString(2) ?: continue
                    out += Contact(
                        number, c.getString(1), c.getString(3)?.let(Uri::parse),
                        ContactsContract.Contacts.getLookupUri(id, c.getString(4)),
                    ) to number
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    data class PhoneEntry(val name: String, val number: String, val label: String, val photo: Uri?)

    fun search(context: Context, query: String, limit: Int = 60): List<PhoneEntry> {
        if (!canRead(context)) return emptyList()
        val uri = if (query.isBlank()) ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        else Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(query))
        val out = ArrayList<PhoneEntry>()
        val seen = HashSet<String>()
        try {
            context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.LABEL,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                ),
                null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC",
            )?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val number = c.getString(1) ?: continue
                    if (!seen.add((c.getString(0) ?: "") + Phone.normalize(number))) continue
                    val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(context.resources, c.getInt(2), c.getString(3)).toString()
                    out += PhoneEntry(c.getString(0) ?: number, number, label, c.getString(4)?.let(Uri::parse))
                }
            }
        } catch (_: Exception) {
        }
        return out
    }
}
