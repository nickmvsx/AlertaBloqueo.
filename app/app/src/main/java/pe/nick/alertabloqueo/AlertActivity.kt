package pe.nick.alertabloqueo

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
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

    private var vibrador: Vibrator? = null
    private lateinit var web: WebView
    private val handler = Handler(Looper.getMainLooper())

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
        web.settings.javaScriptEnabled = true
        web.settings.allowFileAccess = true
        web.addJavascriptInterface(Puente(), "Android")
        web.loadUrl("file:///android_asset/alerta.html")
        setContentView(web)

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

    private fun vibrar() {
        detener()
        vibrador = getSystemService(Vibrator::class.java)
        val v = vibrador ?: return
        val amplitudes = IntArray(Programador.PATRON.size) { if (it % 2 == 1) 255 else 0 }
        val efecto = if (v.hasAmplitudeControl())
            VibrationEffect.createWaveform(Programador.PATRON, amplitudes, 1)
        else
            VibrationEffect.createWaveform(Programador.PATRON, 1)

        if (Build.VERSION.SDK_INT >= 33) {
            v.vibrate(efecto, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(efecto, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
        handler.postDelayed({ detener() }, 2 * 60 * 1000L) // deja de vibrar a los 2 min
    }

    private fun detener() {
        vibrador?.cancel()
        handler.removeCallbacksAndMessages(null)
    }

    private fun apagar() {
        detener()
        Programador.apagar(this)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* se cierra solo con el botón Apagar */ }

    override fun onDestroy() {
        detener()
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
