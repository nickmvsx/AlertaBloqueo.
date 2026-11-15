package pe.nick.alertabloqueo

import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Pantalla de alerta: enciende la pantalla (setTurnScreenOn) y se dibuja sobre el
 * bloqueo (setShowWhenLocked) sin desbloquear el teléfono. Vista nativa (VistaAlerta),
 * que se pinta al instante también en Samsung.
 * Con el extra SOLO_VER se usa para revisar el historial, sin vibrar.
 */
class AlertActivity : Activity() {

    companion object {
        const val SOLO_VER = "solo_ver"
        const val INDICE = "indice"
    }

    private var vista: VistaAlerta? = null
    private var soloVer = false

    private val cerrar = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { if (!soloVer) finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        soloVer = intent.getBooleanExtra(SOLO_VER, false)

        if (!soloVer) {
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
        }

        val fondo = Color.parseColor("#121212")
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = fondo
        @Suppress("DEPRECATION")
        window.navigationBarColor = fondo

        val v = VistaAlerta(this, object : VistaAlerta.Acciones {
            override fun apagar() = this@AlertActivity.apagar()
            override fun abrirEnlace(url: String) = this@AlertActivity.abrirEnlace(url)
        }, soloHistorial = soloVer, inicio = intent.getIntExtra(INDICE, 0))
        vista = v

        val contenedor = FrameLayout(this).apply {
            setBackgroundColor(fondo)
            addView(v.raiz, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        ViewCompat.setOnApplyWindowInsetsListener(contenedor) { c, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            c.setPadding(b.left, b.top, b.right, b.bottom)
            WindowInsetsCompat.CONSUMED
        }
        setContentView(contenedor)

        ContextCompat.registerReceiver(
            this, cerrar, IntentFilter(Programador.ACCION_CERRAR), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        if (!soloVer) Vibracion.iniciar(this)
    }

    /** Si llega otra alerta mientras esta está abierta, se muestra la nueva. */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (soloVer || intent?.getBooleanExtra(SOLO_VER, false) == true) return
        vista?.recargar()
        Vibracion.iniciar(this)
    }

    private fun apagar() {
        if (soloVer) { finish(); return }
        Programador.apagar(this)
        finish()
    }

    /** Abre el enlace: si el celular está bloqueado, primero pide el desbloqueo (huella/PIN). */
    private fun abrirEnlace(url: String) {
        Vibracion.detener()
        val abrir = {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {}
            if (!soloVer) Programador.apagar(this)
            finish()
        }
        val km = getSystemService(KeyguardManager::class.java)
        if (km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() { abrir() }
            })
        } else abrir()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (soloVer) finish()   // en modo alerta se cierra solo con el botón Apagar
    }

    override fun onDestroy() {
        try { unregisterReceiver(cerrar) } catch (_: Exception) {}
        super.onDestroy()
    }
}
