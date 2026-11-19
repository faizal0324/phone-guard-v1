package id.penjaga.panggilan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService

/**
 * Menyaring panggilan masuk.
 * mode 0 = mati, 1 = tolak nomor di luar kontak (termasuk nomor tersembunyi), 2 = tolak semua.
 */
class SpamScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val prefs = getSharedPreferences("pengalih", Context.MODE_PRIVATE)
        val mode = prefs.getInt("mode", 0)

        var block = false
        if (callDetails.callDirection == Call.Details.DIRECTION_INCOMING && mode != 0) {
            block = if (mode == 2) {
                true
            } else {
                val number = callDetails.handle?.schemeSpecificPart
                number.isNullOrBlank() || !isKnown(this, number)
            }
        }

        val response = CallResponse.Builder()
        if (block) {
            response.setDisallowCall(true)
                .setRejectCall(true)
                .setSkipNotification(true)
            prefs.edit().putInt("blocked", prefs.getInt("blocked", 0) + 1).apply()
        }
        respondToCall(callDetails, response.build())
    }

    private fun isKnown(ctx: Context, number: String): Boolean {
        // Tanpa izin kontak, jangan memblokir apa pun supaya tidak salah tolak.
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return true
        }
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
                ?.use { it.count > 0 } ?: false
        } catch (e: Exception) {
            true
        }
    }
}
