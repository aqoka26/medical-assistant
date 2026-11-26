package app.medassist

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import app.medassist.core.Attachment
import app.medassist.core.SupportedFiles
import java.io.File
import java.util.UUID

/** Copies a picked file into the app. Photos are shrunk and saved as JPEG to keep costs down. */
class FileImporter(private val context: Context, private val store: Store) {

    fun import(uri: Uri): Attachment {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: guessMime(uri)
        val name = displayName(uri) ?: "file"

        if (mime.startsWith("image/")) {
            val bitmap = decodeImage(uri)
            val localName = "${UUID.randomUUID()}.jpg"
            File(store.attachmentsDir, localName).outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it)
            }
            bitmap.recycle()
            val shownName = if (name.contains('.')) name.substringBeforeLast('.') + ".jpg" else "$name.jpg"
            return Attachment(localName, shownName, "image/jpeg")
        }

        if (!SupportedFiles.isSupported(mime)) {
            throw IllegalArgumentException("Формат файла «$name» не поддерживается. Нужны PDF, фото или текст.")
        }
        val ext = name.substringAfterLast('.', "bin")
        val localName = "${UUID.randomUUID()}.$ext"
        resolver.openInputStream(uri)!!.use { input ->
            File(store.attachmentsDir, localName).outputStream().use { input.copyTo(it) }
        }
        return Attachment(localName, name, mime)
    }

    private fun decodeImage(uri: Uri): Bitmap {
        val maxSide = 2000
        return if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = maxSide.toFloat() / maxOf(w, h)
                if (scale < 1f) decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: throw IllegalArgumentException("Не удалось открыть изображение.")
        }
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment

    private fun guessMime(uri: Uri): String = when (uri.toString().substringAfterLast('.').lowercase()) {
        "pdf" -> SupportedFiles.PDF
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }
}
