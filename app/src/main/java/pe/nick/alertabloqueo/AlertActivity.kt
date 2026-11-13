package pe.nick.alertabloqueo

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.content.ContextCompat
import org.json.JSONObject

/**
 * Pantalla de alerta: enciende la pantalla (setTurnScreenOn) y se dibuja sobre el
 * bloqueo (setShowWhenLocked) sin desbloquear el teléfono. Muestra assets/alerta.html.
 * Sin sonido: solo vibración fuerte.
 */
class AlertActivity : Activity() {

    private lateinit var web: WebView

    private val cerrar = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { detener(); finish() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        web = WebView(this)
        web.setBackgroundColor(android.graphics.Color.parseColor("#121212"))
        web.settings.javaScriptEnabled = true
        web.settings.allowFileAccess = true
        web.settings.textZoom = 100
        web.addJavascriptInterface(Puente(), "Android")
        web.loadUrl("file:///android_asset/alerta.html")
        val contenedor = android.widget.FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#121212"))
            addView(web)
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(contenedor) { v, insets ->
            val b = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()
                or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            androidx.core.view.WindowInsetsCompat.CONSUMED
        }
        setContentView(contenedor)

        ContextCompat.registerReceiver(
            this, cerrar, IntentFilter(Programador.ACCION_CERRAR), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        vibrar()
    }

    /** Si llega otra alerta mientras esta está abierta, se actualiza el contenido. */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        web.reload()
        vibrar()
    }

    private fun vibrar() = Vibracion.iniciar(this)

    private fun detener() = Vibracion.detener()

    private fun apagar() {
        detener()
        Programador.apagar(this)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* se cierra solo con el botón Apagar */ }

    override fun onDestroy() {
        try { unregisterReceiver(cerrar) } catch (_: Exception) {}
        super.onDestroy()
    }

    inner class Puente {
        @JavascriptInterface
        fun apagar() = runOnUiThread { this@AlertActivity.apagar() }

        @JavascriptInterface
        fun datos(): String = JSONObject()
            .put("titulo", Config.actualTitulo(this@AlertActivity))
            .put("mensaje", Config.actualMensaje(this@AlertActivity))
            .put("imagen", Config.actualImagen(this@AlertActivity))
            .toString()
    }
}
