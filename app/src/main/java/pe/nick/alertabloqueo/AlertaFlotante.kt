package pe.nick.alertabloqueo

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

/**
 * Capa flotante a pantalla completa que se dibuja ENCIMA de cualquier app
 * (permiso "Mostrar sobre otras apps"). Se usa cuando el celular está desbloqueado.
 * Usa la misma vista nativa que la pantalla de bloqueo (VistaAlerta).
 */
object AlertaFlotante {
    private val main = Handler(Looper.getMainLooper())
    private var marco: View? = null
    private var vistaActual: VistaAlerta? = null

    fun mostrar(c: Context) = main.post {
        val app = c.applicationContext
        val wm = app.getSystemService(WindowManager::class.java)

        // Si ya hay una alerta flotante, se muestra la nueva en la misma capa.
        if (marco != null) { vistaActual?.recargar(); Vibracion.iniciar(app); return@post }

        val vista = VistaAlerta(app, object : VistaAlerta.Acciones {
            override fun apagar() = Programador.apagar(app)
            override fun abrirEnlace(url: String) {
                Programador.apagar(app)
                try {
                    app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {}
            }
        })

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
            // Margen para la barra de estado y la de navegación (el botón Apagar no se corta).
            val m = FrameLayout(app).apply {
                setBackgroundColor(Color.parseColor("#121212"))
                setPadding(0, alto(app, "status_bar_height"), 0, alto(app, "navigation_bar_height"))
                addView(vista.raiz, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            }
            wm.addView(m, params)
            marco = m
            vistaActual = vista
            Vibracion.iniciar(app)
            Config.setDiag(app, "Alerta flotante mostrada ✓")
        } catch (e: Exception) {
            Config.setDiag(app, "Alerta flotante FALLÓ: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun ocultar(c: Context) = main.post {
        val v = marco ?: return@post
        try { c.applicationContext.getSystemService(WindowManager::class.java).removeView(v) } catch (_: Exception) {}
        marco = null
        vistaActual = null
    }

    fun visible() = marco != null

    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    private fun alto(c: Context, nombre: String): Int {
        val id = c.resources.getIdentifier(nombre, "dimen", "android")
        return if (id > 0) c.resources.getDimensionPixelSize(id) else 0
    }
}
