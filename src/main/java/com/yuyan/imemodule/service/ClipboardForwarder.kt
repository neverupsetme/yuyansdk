package com.yuyan.imemodule.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * mk patch: hands each copied text to SyncClipboard.
 *
 * Since Android 10 only the focused app and the default IME may read the clipboard in the
 * background, so SyncClipboard relies on this IME instead of Shizuku. Its receiver only accepts
 * senders signed with this app's key, through a knownSigner permission (Android 12+).
 */
object ClipboardForwarder {
    private const val TAG = "ClipboardForwarder"
    private const val TARGET_PACKAGE = "com.jericx.syncclipboardmobile"
    private const val ACTION = "$TARGET_PACKAGE.action.FORWARD_CLIPBOARD"

    // SHA-256 of the certificate SyncClipboard's releases (and the mk builds) are signed with.
    private const val TARGET_CERT_SHA256 =
        "FAC61745DC0903786FB9EDE62A962B399F7348F0BB6F899B8332667591033B9C"

    // Keeps the broadcast well under the binder transaction limit.
    private const val MAX_CHARS = 100_000

    private val targetCert: ByteArray by lazy {
        TARGET_CERT_SHA256.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    fun forward(context: Context, text: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !isGenuineTarget(context)) return
        val intent = Intent(ACTION)
            .setPackage(TARGET_PACKAGE)
            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            .putExtra(Intent.EXTRA_TEXT, text.take(MAX_CHARS))
        try {
            context.sendBroadcast(intent)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Failed to forward clipboard text", e)
        }
    }

    /** Only send clipboard text to the real SyncClipboard, never to another app reusing its name. */
    private fun isGenuineTarget(context: Context): Boolean = try {
        context.packageManager.hasSigningCertificate(
            TARGET_PACKAGE, targetCert, PackageManager.CERT_INPUT_SHA256
        )
    } catch (e: RuntimeException) {
        Log.w(TAG, "Failed to verify $TARGET_PACKAGE", e)
        false
    }
}
