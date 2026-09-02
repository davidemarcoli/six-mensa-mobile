package dev.davidemarcoli.sixmensa.ui.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Renders a PDF to bitmaps using the platform renderer — no third-party dependency, works
 * fully offline once the file is on disk, and gives us complete control over the Compose UI.
 *
 * [PdfRenderer] is **not thread-safe and permits only one open page at a time**, so all
 * access is serialised through a mutex. It also requires a seekable file descriptor, which
 * is why [dev.davidemarcoli.sixmensa.data.PdfRepository] downloads the whole file first
 * rather than streaming it.
 */
class PdfPageRenderer {

    private val mutex = Mutex()

    suspend fun render(file: File, targetWidth: Int): List<ImageBitmap> = mutex.withLock {
        withContext(Dispatchers.IO) {
            ParcelFileDescriptor
                .open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                .use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        (0 until renderer.pageCount).map { index ->
                            renderer.openPage(index).use { page ->
                                val scale = targetWidth.toFloat() / page.width
                                val height = (page.height * scale).toInt().coerceAtLeast(1)
                                val bitmap = Bitmap.createBitmap(
                                    targetWidth,
                                    height,
                                    Bitmap.Config.ARGB_8888,
                                )
                                // Pages render onto transparency; without a white base the
                                // text would sit on whatever is behind it.
                                Canvas(bitmap).drawColor(Color.WHITE)
                                page.render(
                                    bitmap,
                                    null,
                                    null,
                                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                                )
                                bitmap.asImageBitmap()
                            }
                        }
                    }
                }
        }
    }
}
