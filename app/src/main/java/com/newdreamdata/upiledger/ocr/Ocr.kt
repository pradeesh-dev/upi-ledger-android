package com.newdreamdata.upiledger.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** On-device text recognition (ML Kit, Latin model bundled in the APK). */
object Ocr {
    suspend fun recognize(context: Context, uri: Uri): String {
        val bitmap = withContext(Dispatchers.IO) { load(context, uri) }
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        // ML Kit groups into blocks; emit lines top-to-bottom for the parser.
                        val lines = result.textBlocks.flatMap { b -> b.lines.map { l -> (l.boundingBox?.top ?: 0) to l.text } }
                            .sortedBy { it.first }
                            .map { it.second }
                        cont.resume(lines.joinToString("\n"))
                    }
                    .addOnFailureListener { e -> cont.resumeWithException(e) }
            }
        } finally {
            recognizer.close()
        }
    }

    private fun load(context: Context, uri: Uri): Bitmap {
        val bmp = if (Build.VERSION.SDK_INT >= 28) {
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        } else {
            context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
                ?: throw IllegalArgumentException("Could not decode image")
        }
        // Cap the long edge: OCR quality plateaus and memory stays sane.
        val max = 2200
        val longest = maxOf(bmp.width, bmp.height)
        if (longest <= max) return bmp
        val scale = max.toFloat() / longest
        return Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
    }
}
