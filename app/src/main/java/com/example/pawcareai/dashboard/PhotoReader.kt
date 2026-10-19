package com.example.pawcareai.dashboard

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.InputStream

//reads the photo the user picked: its bytes for the upload, and a smaller copy for the preview
class PhotoReader(private val contentResolver: ContentResolver)
{
    //the largest photo laravel accepts: 10 MB
    val uploadLimit: Int = 10 * 1024 * 1024

    //2.input

    //read the photo's bytes; stop one byte after the limit so a huge photo is never fully loaded
    //returns null when the photo cannot be opened
    fun readBytes(uri: Uri): ByteArray?
    {
        try
        {
            val stream: InputStream? = contentResolver.openInputStream(uri)
            if (stream == null)
            {
                return null
            }
            val output: ByteArrayOutputStream = ByteArrayOutputStream()
            val buffer: ByteArray = ByteArray(8192)
            while (output.size() <= uploadLimit)
            {
                val bytesWanted: Int = minOf(buffer.size, uploadLimit + 1 - output.size())
                val bytesRead: Int = stream.read(buffer, 0, bytesWanted)
                if (bytesRead == -1)
                {
                    break
                }
                output.write(buffer, 0, bytesRead)
            }
            stream.close()
            return output.toByteArray()
        }
        catch (error: Exception)
        {
            return null
        }
    }

    //the photo type, for example "image/png"; jpeg is assumed when android does not know
    fun mimeType(uri: Uri): String
    {
        val type: String? = contentResolver.getType(uri)
        if (type == null)
        {
            return "image/jpeg"
        }
        return type
    }

    //3.process

    //the file extension laravel expects, or null for a type the ai service cannot read
    fun extensionFor(mimeType: String): String?
    {
        if (mimeType == "image/png")
        {
            return "png"
        }
        if (mimeType == "image/webp")
        {
            return "webp"
        }
        if (mimeType == "image/jpeg")
        {
            return "jpg"
        }
        return null
    }

    //4.output

    //a preview no larger than about 2048 pixels, so a big camera photo does not use too much memory
    fun previewBitmap(uri: Uri): Bitmap?
    {
        try
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            {
                return decodeWithImageDecoder(uri)
            }
            return decodeWithBitmapFactory(uri)
        }
        catch (error: Exception)
        {
            return null
        }
    }

    //android 9 and newer: also keeps the photo the right way up
    private fun decodeWithImageDecoder(uri: Uri): Bitmap
    {
        val source: ImageDecoder.Source = ImageDecoder.createSource(contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, imageSource ->
            val largestSide: Int = maxOf(info.size.width, info.size.height)
            decoder.setTargetSampleSize(maxOf(1, (largestSide + 2047) / 2048))
        }
    }

    //android 8: read the size first, then decode at half size until it is small enough
    private fun decodeWithBitmapFactory(uri: Uri): Bitmap?
    {
        val options: BitmapFactory.Options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        val sizeStream: InputStream? = contentResolver.openInputStream(uri)
        if (sizeStream != null)
        {
            BitmapFactory.decodeStream(sizeStream, null, options)
            sizeStream.close()
        }

        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 2048)
        {
            options.inSampleSize = options.inSampleSize * 2
        }
        options.inJustDecodeBounds = false

        val imageStream: InputStream? = contentResolver.openInputStream(uri)
        if (imageStream == null)
        {
            return null
        }
        val bitmap: Bitmap? = BitmapFactory.decodeStream(imageStream, null, options)
        imageStream.close()
        return bitmap
    }
}
