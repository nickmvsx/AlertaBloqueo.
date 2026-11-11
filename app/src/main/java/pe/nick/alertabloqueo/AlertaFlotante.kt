package pe.nick.alertabloqueo

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject

/**
 * Capa flotante a pantalla completa que se dibuja ENCIMA de cualquier app
 * (permiso "Mostrar sobre otras apps"). Se usa cuando el celular está desbloqueado.
 * Muestra el mismo assets/alerta.html que la pantalla de bloqueo.
 */
object AlertaFlotante {
    private val main = Handler(Looper.getMainLooper())
    private var vista: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun mostrar(c: Context) = main.post {
        val app = c.applicationContext
        val wm = app.getSystemService(WindowManager::class.java)

        // Si ya hay una alerta flotante, solo se recarga con el nuevo contenido.
        vista?.let { it.reload(); Vibracion.iniciar(app); return@post }

        val web = WebView(app)
        web.settings.javaScriptEnabled = true
        web.settings.allowFileAccess = true
        web.addJavascriptInterface(Puente(app), "Android")
        web.loadUrl("file:///android_asset/alerta.html")

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.OPAQUE
        ).apply { gravity = Gravity.TOP or Gravity.START }

        try {
            wm.addView(web, params)
            vista = web
            Vibracion.iniciar(app)
        } catch (e: Exception) {
            web.destroy()
        }
    }

    fun ocultar(c: Context) = main.post {
        val v = vista ?: return@post
        try { c.applicationContext.getSystemService(WindowManager::class.java).removeView(v) } catch (_: Exception) {}
        v.destroy()
        vista = null
    }

    fun visible() = vista != null

    private class Puente(private val app: Context) {
        @JavascriptInterface
        fun apagar() { main.post { Programador.apagar(app) } }

        @JavascriptInterface
        fun datos(): String = JSONObject()
            .put("titulo", Config.actualTitulo(app))
            .put("mensaje", Config.actualMensaje(app))
            .put("imagen", Config.actualImagen(app))
            .toString()
    }
}
