package com.danna.drobeus

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

// Lectura de archivos del celular (fotos, PDF, Word...) para subirlos al servidor
object Archivos {

    // Tipos que acepta la carga automática de horario
    val TIPOS_HORARIO = arrayOf(
        "image/*",
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "text/plain"
    )

    class Leido(val nombre: String, val mime: String, val bytes: ByteArray)

    // Lee el archivo; si es una foto, la reduce y comprime para que pese poco
    fun leer(ctx: Context, uri: Uri): Leido? {
        val resolver = ctx.contentResolver
        var nombre = "archivo"
        resolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) nombre = c.getString(i) ?: nombre
        }
        var mime = resolver.getType(uri) ?: "application/octet-stream"
        var bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null

        if (mime.startsWith("image/") && mime != "image/gif") {
            val comprimida = comprimir(bytes)
            if (comprimida != null) {
                bytes = comprimida
                mime = "image/jpeg"
                nombre = nombre.substringBeforeLast('.', nombre) + ".jpg"
            }
        }
        return Leido(nombre, mime, bytes)
    }

    private fun comprimir(original: ByteArray): ByteArray? {
        return try {
            val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(original, 0, original.size, limites)

            var muestra = 1
            while (maxOf(limites.outWidth, limites.outHeight) / muestra > 2200) muestra *= 2

            val opciones = BitmapFactory.Options().apply { inSampleSize = muestra }
            var bmp = BitmapFactory.decodeByteArray(original, 0, original.size, opciones)
                ?: return null

            // Respeta la rotación con la que se tomó la foto
            val rotacion = when (
                ExifInterface(ByteArrayInputStream(original))
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (rotacion != 0f) {
                val m = Matrix().apply { postRotate(rotacion) }
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }

            val salida = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, salida)
            salida.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    fun parte(campo: String, a: Leido): MultipartBody.Part =
        MultipartBody.Part.createFormData(campo, a.nombre, a.bytes.toRequestBody(a.mime.toMediaType()))

    fun texto(valor: String): RequestBody =
        valor.toRequestBody("text/plain; charset=utf-8".toMediaType())
}
