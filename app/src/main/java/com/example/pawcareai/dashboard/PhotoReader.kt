package com.example.pawcareai.dashboard

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream

//read a chosen photo for upload and for the on-screen preview
class PhotoReader(private val contentResolver: ContentResolver)
{
    //largest photo laravel accepts
    val uploadLimit: Int = 10 * 1024 * 1024

    //2.input

    //read at most the upload limit plus one byte, or null when the photo cannot be read
    fun readBytes(uri: Uri): ByteArray?
    {
        val imageReadResult = runCatching {
            contentResolver.openInputStream(uri)?.use { imageStream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= uploadLimit)
                {
                    val count = imageStream.read(buffer, 0, minOf(buffer.size, uploadLimit + 1 - output.size()))
                    if (count == -1) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        }
        return imageReadResult.getOrNull()
    }

    //read the photo type, defaulting to jpeg like the original upload
    fun mimeType(uri: Uri): String
    {
        return contentResolver.getType(uri) ?: "image/jpeg"
    }

    //3.process

    //pick the upload file extension, or null for an unsupported type
    fun extensionFor(mime: String): String?
    {
        return when (mime)
        {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/jpeg" -> "jpg"
            else -> null
        }
    }

    //4.output

    //decode the photo without its full camera resolution
    fun previewBitmap(uri: Uri): Bitmap?
    {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            {
                //keep camera orientation while limiting preview memory
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val largestSide = maxOf(info.size.width, info.size.height)
                    decoder.setTargetSampleSize(maxOf(1, (largestSide + 2047) / 2048))
                }
            }
            else
            {
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                options.inSampleSize = 1
                while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 2048)
                {
                    options.inSampleSize *= 2
                }
                options.inJustDecodeBounds = false
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            }
        }.getOrNull()
    }
}
