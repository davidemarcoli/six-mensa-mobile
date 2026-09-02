package dev.davidemarcoli.sixmensa.share

import android.content.Context
import android.content.Intent
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import java.io.File

object ShareActions {

    private fun uriFor(context: Context, file: File) = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )

    fun sharePdf(context: Context, file: File, title: String) {
        val intent = ShareCompat.IntentBuilder(context)
            .setType("application/pdf")
            .setStream(uriFor(context, file))
            .setChooserTitle(title)
            .createChooserIntent()
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.launch(intent)
    }

    fun shareText(context: Context, text: String, title: String) {
        val intent = ShareCompat.IntentBuilder(context)
            .setType("text/plain")
            .setText(text)
            .setChooserTitle(title)
            .createChooserIntent()
        context.launch(intent)
    }

    /** Null when no installed app can display a PDF, so the caller can hide the action. */
    fun openPdfExternallyIntent(context: Context, file: File): Intent? {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriFor(context, file), "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return intent.takeIf { it.resolveActivity(context.packageManager) != null }
    }

    /**
     * Starts from the hosting Activity when there is one, so the chooser appears in the
     * app's own task. Only falls back to NEW_TASK when genuinely context-less.
     */
    fun Context.launch(intent: Intent) {
        val activity = findActivity()
        if (activity != null) {
            activity.startActivity(intent)
        } else {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
