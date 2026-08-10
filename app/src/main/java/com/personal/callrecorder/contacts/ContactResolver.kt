package com.personal.callrecorder.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a phone number to a saved contact name via ContactsContract.
 * Every path is null-safe and permission-guarded: with no READ_CONTACTS grant,
 * or no match, callers simply fall back to the raw number.
 */
@Singleton
class ContactResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /** @return the contact display name, or null if unknown / not permitted. */
    fun resolveName(number: String?): String? {
        if (number.isNullOrBlank() || !hasPermission()) return null
        return try {
            val uri: Uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (t: Throwable) {
            // SecurityException (permission revoked mid-flight), provider errors, etc.
            null
        }
    }
}
