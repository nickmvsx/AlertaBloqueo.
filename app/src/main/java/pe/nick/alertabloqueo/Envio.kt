package pe.nick.alertabloqueo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Envía una alerta (título, mensaje e imagen opcional) al código de otro celular vía ntfy.sh. */
object Envio {

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /**
     * Prepara la imagen para enviarla: la endereza según la orientación de la cámara (EXIF),
     * la reduce a máx. 1280 px y la convierte a JPEG para que viaje rápido.
     */
    fun prepararImagen(c: Context, uri: Uri): ByteArray? = try {
        val cr = c.contentResolver
        val orientacion = try {
            cr.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) { ExifInterface.ORIENTATION_NORMAL }

        val opciones = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opciones) }
        var escala = 1
        while (opciones.outWidth / (escala * 2) >= 1280 || opciones.outHeight / (escala * 2) >= 1280) escala *= 2
        var bmp = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = escala })
        } ?: throw IllegalStateException("sin imagen")

        // Ajuste fino a 1280 px como máximo
        val lado = maxOf(bmp.width, bmp.height)
        if (lado > 1280) {
            val f = 1280f / lado
            bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt(), (bmp.height * f).toInt(), true)
        }

        // Enderezar según EXIF
        val m = Matrix()
        when (orientacion) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
        }
        if (!m.isIdentity) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)

        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
        out.toByteArray()
    } catch (e: Exception) { null }

    /** Devuelve un texto para mostrar al usuario. */
    fun enviar(codigoDestino: String, titulo: String, mensaje: String, imagen: ByteArray?): String {
        val limpio = Config.normalizar(codigoDestino)
        if (limpio.length < 6) return "Escribe el código del otro celular."
        val tema = Config.tema(limpio)
        val texto = mensaje.ifBlank { " " }
        return try {
            val con: HttpURLConnection
            if (imagen != null) {
                val url = "https://ntfy.sh/$tema?title=${enc(titulo)}&message=${enc(texto)}&filename=imagen.jpg"
                con = URL(url).openConnection() as HttpURLConnection
                con.requestMethod = "PUT"
                con.doOutput = true
                con.setRequestProperty("Content-Type", "image/jpeg")
                con.setFixedLengthStreamingMode(imagen.size)
                con.connectTimeout = 15_000
                con.readTimeout = 60_000
                con.outputStream.use { it.write(imagen) }
            } else {
                val cuerpo = JSONObject()
                    .put("topic", tema)
                    .put("title", titulo)
                    .put("message", texto)
                    .toString().toByteArray(Charsets.UTF_8)
                con = URL("https://ntfy.sh/").openConnection() as HttpURLConnection
                con.requestMethod = "POST"
                con.doOutput = true
                con.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                con.setFixedLengthStreamingMode(cuerpo.size)
                con.connectTimeout = 15_000
                con.readTimeout = 30_000
                con.outputStream.use { it.write(cuerpo) }
            }
            val codigo = con.responseCode
            val resultado = if (codigo in 200..299) "Alerta enviada ✓"
            else "No se pudo enviar (error $codigo). " +
                (con.errorStream?.bufferedReader()?.use { it.readText() }?.take(150) ?: "")
            con.disconnect()
            resultado
        } catch (e: Exception) {
            "Sin conexión a internet o el servidor no respondió."
        }
    }
}
