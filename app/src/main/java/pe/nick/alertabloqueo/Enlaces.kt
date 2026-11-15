package pe.nick.alertabloqueo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Patterns
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Detecta enlaces en un mensaje y obtiene su vista previa (título, sitio y miniatura).
 * TikTok y YouTube usan su servicio oEmbed; el resto, las etiquetas Open Graph de la página.
 */
object Enlaces {

    data class Vista(val url: String, val titulo: String, val sitio: String, val imagen: String?)

    private val cache = java.util.Collections.synchronizedMap(HashMap<String, Vista>())

    /** Primer enlace del texto (con https:// si no lo trae), o null. */
    fun primero(texto: String): String? {
        val m = Patterns.WEB_URL.matcher(texto)
        while (m.find()) {
            val u = m.group().trimEnd('.', ',', ')', '!', '?')
            // Evita confundir palabras sueltas con dominios: exige esquema, "www." o una ruta.
            if (u.startsWith("http", true) || u.startsWith("www.", true) || u.contains('/')) {
                return if (u.startsWith("http", true)) u else "https://$u"
            }
        }
        return null
    }

    private fun sitioDe(url: String): String = try {
        URL(url).host.removePrefix("www.").removePrefix("vm.").removePrefix("m.")
    } catch (e: Exception) { url }

    private fun abrir(url: String, agente: String): HttpURLConnection {
        val con = URL(url).openConnection() as HttpURLConnection
        con.instanceFollowRedirects = true
        con.connectTimeout = 10_000
        con.readTimeout = 10_000
        con.setRequestProperty("User-Agent", agente)
        con.setRequestProperty("Accept-Language", "es-PE,es;q=0.9")
        return con
    }

    /** Sigue redirecciones (p. ej. vm.tiktok.com/xxx) y devuelve la URL final. */
    private fun resolver(url: String): String {
        var actual = url
        try {
            for (paso in 1..5) {
                val con = abrir(actual, MOVIL)
                con.instanceFollowRedirects = false
                val code = con.responseCode
                val loc = con.getHeaderField("Location")
                con.disconnect()
                if (code in 300..399 && loc != null) actual = URL(URL(actual), loc).toString() else break
            }
        } catch (e: Exception) { }
        return actual
    }

    private fun leer(con: HttpURLConnection, max: Int = 400_000): String =
        con.inputStream.use { ins ->
            val buf = ByteArray(max)
            var n = 0
            while (n < max) {
                val r = ins.read(buf, n, max - n)
                if (r <= 0) break
                n += r
            }
            String(buf, 0, n, Charsets.UTF_8)
        }

    private fun oembed(endpoint: String, url: String, sitio: String): Vista? = try {
        val con = abrir(endpoint + URLEncoder.encode(url, "UTF-8"), MOVIL)
        val o = JSONObject(leer(con))
        con.disconnect()
        val autor = o.optString("author_name")
        Vista(
            url,
            o.optString("title").ifBlank { if (autor.isNotBlank()) "Video de $autor" else sitio },
            if (autor.isNotBlank()) "$sitio · $autor" else sitio,
            o.optString("thumbnail_url").ifBlank { null }
        )
    } catch (e: Exception) { null }

    private fun meta(html: String, prop: String): String? {
        val r1 = Regex("""<meta[^>]+(?:property|name)=["']$prop["'][^>]*content=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        val r2 = Regex("""<meta[^>]+content=["']([^"']*)["'][^>]*(?:property|name)=["']$prop["']""", RegexOption.IGNORE_CASE)
        return (r1.find(html) ?: r2.find(html))?.groupValues?.get(1)?.let { desescapar(it) }?.ifBlank { null }
    }

    private fun desescapar(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&#x27;", "'").replace("&lt;", "<").replace("&gt;", ">")

    private fun openGraph(url: String): Vista? = try {
        val con = abrir(url, BOT)
        val html = leer(con)
        val base = con.url.toString()
        con.disconnect()
        val titulo = meta(html, "og:title") ?: meta(html, "twitter:title")
            ?: Regex("<title[^>]*>([^<]*)</title>", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.trim()
        var img = meta(html, "og:image") ?: meta(html, "twitter:image")
        if (img != null) img = URL(URL(base), img).toString()
        val sitio = meta(html, "og:site_name") ?: sitioDe(base)
        Vista(url, desescapar(titulo ?: sitio), sitio, img)
    } catch (e: Exception) { null }

    /** Obtiene la vista previa (hace conexiones de red: llamar fuera del hilo principal). */
    fun obtener(url: String): Vista {
        cache[url]?.let { return it }
        val final = resolver(url)
        val host = sitioDe(final)
        val v = when {
            host.contains("tiktok.com") ->
                oembed("https://www.tiktok.com/oembed?url=", final, "TikTok")
            host.contains("youtube.com") || host.contains("youtu.be") ->
                oembed("https://www.youtube.com/oembed?format=json&url=", final, "YouTube")
            else -> null
        } ?: openGraph(final) ?: Vista(url, host, host, null)
        cache[url] = v
        return v
    }

    /** Descarga la miniatura reducida. */
    fun miniatura(url: String?, maxLado: Int = 720): Bitmap? = try {
        if (url == null) null else {
            val con = abrir(url, MOVIL)
            val bytes = con.inputStream.use { it.readBytes() }
            con.disconnect()
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            var s = 1
            while (o.outWidth / (s * 2) >= maxLado || o.outHeight / (s * 2) >= maxLado) s *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })
        }
    } catch (e: Exception) { null }

    private const val MOVIL =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
    private const val BOT = "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"
}
