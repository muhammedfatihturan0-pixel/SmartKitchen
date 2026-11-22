package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

object BitmapUtils {

    fun createCameraImageUri(context: Context): Uri {
        val cacheFolder = File(context.cacheDir, "camera_images").apply { mkdirs() }
        val photoFile = File(cacheFolder, "scan_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
    }

    fun decodeSampledSoftwareBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1600
    ): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            var inSampleSize = 1
            val rawW = boundsOptions.outWidth
            val rawH = boundsOptions.outHeight
            if (rawW > maxDimension || rawH > maxDimension) {
                val halfW = rawW / 2
                val halfH = rawH / 2
                while (halfW / inSampleSize >= maxDimension && halfH / inSampleSize >= maxDimension) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                this.inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            val rotationDegrees = readExifRotation(context, uri)
            val rotated = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val transformed = Bitmap.createBitmap(
                    decoded, 0, 0, decoded.width, decoded.height, matrix, true
                )
                if (transformed !== decoded) decoded.recycle()
                transformed
            } else {
                decoded
            }

            val longest = max(rotated.width, rotated.height)
            if (longest > maxDimension && longest > 0) {
                val scale = maxDimension.toFloat() / longest.toFloat()
                val targetW = (rotated.width * scale).roundToInt().coerceAtLeast(1)
                val targetH = (rotated.height * scale).roundToInt().coerceAtLeast(1)
                val finalBmp = Bitmap.createScaledBitmap(rotated, targetW, targetH, true)
                if (finalBmp !== rotated) rotated.recycle()
                finalBmp
            } else {
                rotated
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readExifRotation(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (_: Exception) {
            0
        }
    }
}
