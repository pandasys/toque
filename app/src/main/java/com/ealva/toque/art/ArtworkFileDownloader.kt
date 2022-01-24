/*
 * Copyright 2022 Eric A. Snell
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ealva.toque.art

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.util.Size
import androidx.annotation.WorkerThread
import androidx.exifinterface.media.ExifInterface
import com.ealva.ealvalog.e
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.ealvalog.unaryPlus
import com.ealva.toque.app.Toque
import com.ealva.toque.art.ArtworkDownloader.CompressionQuality
import com.ealva.toque.common.debug
import com.ealva.toque.file.fileExtensionWithDot
import com.ealva.toque.log._i
import com.ealva.toque.net.NetworkDefaults
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import okio.buffer
import okio.sink
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToInt


private val LOG by lazyLogger(ArtworkFileDownloader::class)
private const val CACHE_SIZE = 20L * 1024L * 1024L
private var counter: Long = 1

interface ArtworkDownloader {
  /**
   * CompressionQuality of 100 means compress for maximum visual quality while 0 means compress
   * for smallest size.
   */
  @JvmInline
  value class CompressionQuality(val value: Int) : Comparable<CompressionQuality> {
    fun toFloat(): Float = value.toFloat()

    companion object {
      operator fun invoke(floatVal: Float): CompressionQuality =
        CompressionQuality(floatVal.toInt())
    }

    override fun compareTo(other: CompressionQuality): Int = value.compareTo(other.value)
  }

  @WorkerThread
  fun download(
    source: Uri,
    destination: Uri,
    size: Size,
    quality: CompressionQuality,
    maxSize: Size = Size(2048, 2048)
  )

  companion object {
    operator fun invoke(): ArtworkDownloader = ArtworkFileDownloader()
  }
}

val ORIGINAL_SIZE = Size(-1, -1)

private class ArtworkFileDownloader : ArtworkDownloader {
  private val client: OkHttpClient by lazy { makeClient() }

  private fun makeClient(): OkHttpClient {
    return NetworkDefaults.okHttpClient
      .newBuilder()
      .cache(Cache(File(Toque.appContext.cacheDir, "download"), CACHE_SIZE))
      .interceptLoggingInDebug()
      .build()
  }

  override fun download(
    source: Uri,
    destination: Uri,
    size: Size,
    quality: CompressionQuality,
    maxSize: Size
  ) {
    LOG._i {
      it(
        "download src=%s dest=%s size=%s",
        source,
        destination,
        if (size == ORIGINAL_SIZE) "original" else size
      )
    }
    try {
      if (ContentResolver.SCHEME_FILE == destination.scheme) {
        val request = Request.Builder().url(source.toString()).build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
          LOG.e { +it("Error response %s", response) }
          throw IOException("Unexpected response $response")
        }
        response.body.use { responseBody ->
          if (responseBody == null) throw IOException("No response requesting file")
          val tempFile = File.createTempFile(
            "${counter++}${source.lastPathSegment}",
            source.fileExtensionWithDot,
            Toque.appContext.cacheDir
          )
          try {
            val destPath =
              destination.path ?: throw IllegalStateException("Destination path must be specified")
            tempFile.sink().buffer().use { sink ->
              sink.writeAll(responseBody.source())
              sink.close()
              val destSize = getDestinationSize(tempFile, size, maxSize)
              FileOutputStream(destPath).use { fos ->
                compressImage(
                  tempFile,
                  destSize.width.toFloat(),
                  destSize.height.toFloat(),
                  Bitmap.CompressFormat.JPEG,
                  Bitmap.Config.ARGB_8888,
                  quality,
                  fos
                )
                fos.flush()
              }
            }
          } finally {
            if (!tempFile.delete()) {
              LOG.e { +it("Temp %s not deleted", tempFile) }
            }
          }
        }
      } else {
        LOG.e { +it("Destination scheme not file. %s", destination) }
        // should never happen, we'll let it get caught below and rethrown.
        // If code changes, this is still OK
        throw IllegalArgumentException("Must be file scheme. $destination")
      }
    } catch (e: RuntimeException) {
      LOG.e(e) { +it("Unexpected") }
      throw IOException(e)
    }
  }

  private fun getImageSize(file: File): Size {
    FileInputStream(file).use {
      return decodeSizeFromStream(it)
    }
  }

  private fun decodeSizeFromStream(inputStream: InputStream): Size {
    val options = BitmapFactory.Options()
    options.inJustDecodeBounds = true

    BitmapFactory.decodeStream(inputStream, null, options)
    return Size(options.outWidth, options.outHeight)
  }

  /**
   * If we know the original size, use it. Otherwise calculate it from the file
   *
   * @param file temp file containing the image
   * @param size either the known size or [ORIGINAL_SIZE] if it's not yet been calculated
   *
   * @return the desired image size
   *
   * @throws IOException if we can't read the file or it's not an image
   */
  private fun getDestinationSize(file: File, size: Size, maxSize: Size): Size {
    var originalSize = size
    if (size == ORIGINAL_SIZE) {
      originalSize = getImageSize(file)
    }
    return originalSize.maybeScaleDown(maxSize)
  }

  private fun getScaledBitmap(
    file: File,
    maxWidth: Float,
    maxHeight: Float,
    bitmapConfig: Bitmap.Config
  ): Bitmap {
    val filePath = file.absolutePath
    val options = BitmapFactory.Options()

    // By setting this field as true, the actual bitmap pixels are not loaded in the memory.
    // Just the bounds are loaded. If you try the use the bitmap here, you will get null.
    options.inJustDecodeBounds = true
    BitmapFactory.decodeFile(filePath, options)

    var actualHeight = options.outHeight
    var actualWidth = options.outWidth

    if (actualWidth < 0 || actualHeight < 0) {
      val bitmap2 = BitmapFactory.decodeFile(filePath)
      actualWidth = bitmap2.width
      actualHeight = bitmap2.height
    }

    var imgRatio = actualWidth.toFloat() / actualHeight
    val maxRatio = maxWidth / maxHeight

    //width and height values are set maintaining the aspect ratio of the image
    if (actualHeight > maxHeight || actualWidth > maxWidth) {
      when {
        imgRatio < maxRatio -> {
          imgRatio = maxHeight / actualHeight
          actualWidth = (imgRatio * actualWidth).toInt()
          actualHeight = maxHeight.toInt()
        }
        imgRatio > maxRatio -> {
          imgRatio = maxWidth / actualWidth
          actualHeight = (imgRatio * actualHeight).toInt()
          actualWidth = maxWidth.toInt()
        }
        else -> {
          actualHeight = maxHeight.toInt()
          actualWidth = maxWidth.toInt()
        }
      }
    }

    //setting inSampleSize value allows to load a scaled down version of the original image
    options.inSampleSize = calculateInSampleSize(options, actualWidth, actualHeight)

    //inJustDecodeBounds set to false to load the actual bitmap
    options.inJustDecodeBounds = false
    options.inTempStorage = ByteArray(16 * 1024)

    //load the bitmap from its path
    val bmp = BitmapFactory.decodeFile(filePath, options)
    var scaledBitmap = Bitmap.createBitmap(actualWidth, actualHeight, bitmapConfig)

    val ratioX = actualWidth / options.outWidth.toFloat()
    val ratioY = actualHeight / options.outHeight.toFloat()

    val scaleMatrix = Matrix()
    scaleMatrix.setScale(ratioX, ratioY, 0f, 0f)

    val canvas = Canvas(scaledBitmap)
    canvas.setMatrix(scaleMatrix)
    canvas.drawBitmap(bmp, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))

    //check the rotation of the image and display it properly
    val exif = ExifInterface(filePath)
    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0)
    val matrix = Matrix()
    when (orientation) {
      ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
      ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
      ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
    }
    scaledBitmap = Bitmap.createBitmap(
      scaledBitmap,
      0,
      0,
      scaledBitmap.width,
      scaledBitmap.height,
      matrix,
      true
    )
    return scaledBitmap
  }

  @Suppress("SameParameterValue")
  private fun compressImage(
    file: File,
    maxWidth: Float,
    maxHeight: Float,
    compressFormat: Bitmap.CompressFormat,
    bitmapConfig: Bitmap.Config,
    quality: CompressionQuality,
    outputStream: OutputStream
  ) {
    getScaledBitmap(file, maxWidth, maxHeight, bitmapConfig).compress(
      compressFormat,
      quality.value,
      outputStream
    )
  }

  private fun calculateInSampleSize(
    options: BitmapFactory.Options,
    reqWidth: Int,
    reqHeight: Int
  ): Int {
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
      val heightRatio = (height.toFloat() / reqHeight.toFloat()).roundToInt()
      val widthRatio = (width.toFloat() / reqWidth.toFloat()).roundToInt()
      inSampleSize = if (heightRatio < widthRatio) heightRatio else widthRatio
    }

    val totalPixels = (width * height).toFloat()
    val totalReqPixelsCap = (reqWidth * reqHeight * 2).toFloat()

    while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
      inSampleSize++
    }

    return inSampleSize
  }
}

/** Returns true if both this.width < boundary.width and this.height < boundary.height*/
fun Size.needsToBeScaledDown(boundary: Size): Boolean =
  width > boundary.width || height > boundary.height

/**
 * If [needsToBeScaledDown], returns a Size less than boundary while maintaining aspect ratio.
 * Otherwise returns self.
 */
fun Size.maybeScaleDown(boundary: Size): Size {
  return if (!needsToBeScaledDown(boundary)) this else {
    val originalWidth = width
    val originalHeight = height
    val boundWidth = boundary.width
    val boundHeight = boundary.height
    var newWidth = originalWidth
    var newHeight = originalHeight

    // first check if we need to scale width
    if (originalWidth > boundWidth) {
      //scale width to fit
      newWidth = boundWidth
      //scale height to maintain aspect ratio
      newHeight = newWidth * originalHeight / originalWidth
    }

    // then check if we need to scale even with the new height
    if (newHeight > boundHeight) {
      //scale height to fit
      newHeight = boundHeight
      //scale width to maintain aspect ratio
      newWidth = newHeight * originalWidth / originalHeight
    }

    Size(newWidth, newHeight)
  }
}

fun OkHttpClient.Builder.interceptLoggingInDebug(): OkHttpClient.Builder {
  debug { interceptLogging() }
  return this
}

private val interceptLogger by lazyLogger("LoggingInterceptor")
fun OkHttpClient.Builder.interceptLogging(): OkHttpClient.Builder = apply {
  addInterceptor(
    HttpLoggingInterceptor { message ->
      interceptLogger.i { it(message) }
    }.apply { level = HttpLoggingInterceptor.Level.BASIC }
  )
}
