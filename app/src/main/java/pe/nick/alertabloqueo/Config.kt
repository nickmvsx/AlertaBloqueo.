package pe.nick.alertabloqueo

import android.content.Context
import java.security.SecureRandom

/** Configuración guardada en el teléfono. */
object Config {
    private fun p(c: Context) = c.getSharedPreferences("config_alerta", Context.MODE_PRIVATE)

    const val IMAGEN_DEFECTO = "file:///android_asset/imagen.png"

    // --- Alerta programada (local) ---
    fun titulo(c: Context) = p(c).getString("titulo", "¡Atención!")!!
    fun mensaje(c: Context) = p(c).getString("mensaje", "Este es tu mensaje de alerta.")!!
    fun hora(c: Context) = p(c).getString("hora", "08:00")!!
    fun diario(c: Context) = p(c).getBoolean("diario", true)
    fun activo(c: Context) = p(c).getBoolean("activo", false)

    fun guardar(c: Context, titulo: String, mensaje: String, hora: String, diario: Boolean, activo: Boolean) {
        p(c).edit()
            .putString("titulo", titulo)
            .putString("mensaje", mensaje)
            .putString("hora", hora)
            .putBoolean("diario", diario)
            .putBoolean("activo", activo)
            .apply()
    }

    // --- Código de este teléfono para recibir alertas ---
    fun codigo(c: Context): String {
        var s = p(c).getString("codigo", null)
        if (s == null) {
            val letras = "abcdefghjkmnpqrstuvwxyz23456789"
            val r = SecureRandom()
            s = (1..12).map { letras[r.nextInt(letras.length)] }.joinToString("")
            p(c).edit().putString("codigo", s).apply()
        }
        return s
    }

    /** Muestra el código en grupos de 4: abcd-efgh-jkmn */
    fun codigoBonito(c: Context) = codigo(c).chunked(4).joinToString("-")

    /** Limpia un código escrito por el usuario (quita guiones y espacios). */
    fun normalizar(codigo: String) = codigo.lowercase().filter { it.isLetterOrDigit() }

    fun tema(codigo: String) = "alertabloqueo-" + normalizar(codigo)

    fun recibir(c: Context) = p(c).getBoolean("recibir", true)
    fun setRecibir(c: Context, v: Boolean) = p(c).edit().putBoolean("recibir", v).apply()

    /** Último mensaje recibido (id y hora unix en segundos), para no repetirlo al reconectar. */
    fun ultimoId(c: Context): String? = p(c).getString("ultimo_id", null)
    fun ultimoTiempo(c: Context): Long = p(c).getLong("ultimo_tiempo", 0L)
    fun setUltimo(c: Context, id: String, tiempo: Long) =
        p(c).edit().putString("ultimo_id", id).putLong("ultimo_tiempo", tiempo).apply()

    fun ultimoDestino(c: Context) = p(c).getString("ultimo_destino", "")!!
    fun setUltimoDestino(c: Context, v: String) = p(c).edit().putString("ultimo_destino", v).apply()

    // --- Diagnóstico: qué pasó con la última alerta ---
    fun diag(c: Context) = p(c).getString("diag", "Aún no llegó ninguna alerta.")!!
    fun setDiag(c: Context, texto: String) {
        val hora = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        p(c).edit().putString("diag", "$hora · $texto").apply()
    }

    // --- Lo que muestra la alerta en este momento ---
    fun actualTitulo(c: Context) = p(c).getString("act_titulo", titulo(c))!!
    fun actualMensaje(c: Context) = p(c).getString("act_mensaje", mensaje(c))!!
    fun actualImagen(c: Context) = p(c).getString("act_imagen", IMAGEN_DEFECTO)!!

    fun setActual(c: Context, titulo: String, mensaje: String, imagen: String) {
        p(c).edit()
            .putString("act_titulo", titulo)
            .putString("act_mensaje", mensaje)
            .putString("act_imagen", imagen)
            .commit()
    }
}
