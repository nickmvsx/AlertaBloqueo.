package pe.nick.alertabloqueo

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla de alerta dibujada con vistas nativas de Android (sin WebView):
 * aparece al instante, también en Samsung con la pantalla apagada.
 * Permite retroceder por los últimos mensajes y muestra la vista previa de los enlaces.
 */
class VistaAlerta(
    private val ctx: Context,
    private val acciones: Acciones,
    private val soloHistorial: Boolean = false,
    inicio: Int = 0
) {
    interface Acciones {
        fun apagar()
        fun abrirEnlace(url: String)
    }

    private val main = Handler(Looper.getMainLooper())
    private var paginas: List<Config.Mensaje> = cargarPaginas()
    private var indice = inicio.coerceIn(0, (paginas.size - 1).coerceAtLeast(0))
    private var turno = 0   // evita que una carga lenta pinte sobre otra página

    // Colores del diseño
    private val FONDO = Color.parseColor("#121212")
    private val SUPERFICIE = Color.parseColor("#232323")
    private val ROSA = Color.parseColor("#F5A9DE")
    private val ROSA_CLARO = Color.parseColor("#FBE3F2")
    private val TINTA = Color.parseColor("#161616")
    private val SUAVE = Color.parseColor("#9B9B9B")

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics).toInt()
    private fun redondo(color: Int, radio: Float) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radio).toFloat() }
    private fun texto(sp: Float, color: Int, negrita: Boolean = false) = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        if (negrita) typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER_HORIZONTAL
    }

    // --- Vistas ---
    private val hora = texto(56f, Color.WHITE).apply { typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) }
    private val fecha = texto(15f, SUAVE)
    private val imagen = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val tarjeta = FrameLayout(ctx).apply {
        background = redondo(ROSA_CLARO, 30f)
        clipToOutline = true
        addView(imagen, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }
    private val chip = texto(14f, TINTA, true).apply {
        background = redondo(ROSA, 99f)
        setPadding(dp(18f), dp(8f), dp(18f), dp(8f))
    }
    private val titulo = texto(30f, Color.WHITE, true)
    private val mensaje = texto(18f, Color.parseColor("#CFCFCF")).apply { setLineSpacing(0f, 1.2f) }

    // Tarjeta de enlace
    private val linkImg = ImageView(ctx).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = redondo(ROSA, 14f)
        clipToOutline = true
    }
    private val linkTitulo = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
        maxLines = 2; ellipsize = TextUtils.TruncateAt.END
    }
    private val linkSitio = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(SUAVE); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
    }
    private val tarjetaLink = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = redondo(SUPERFICIE, 20f)
        setPadding(dp(10f), dp(10f), dp(14f), dp(10f))
        addView(linkImg, LinearLayout.LayoutParams(dp(72f), dp(96f)))
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), 0, dp(8f), 0)
            addView(linkTitulo); addView(linkSitio)
            addView(TextView(ctx).apply {
                text = "Toca para abrir ›"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(ROSA); typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(6f), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        visibility = View.GONE
    }

    // Navegación por el historial
    private fun botonTexto(t: String) = TextView(ctx).apply {
        text = t; setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); setTextColor(Color.WHITE)
        gravity = Gravity.CENTER; setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
        background = redondo(SUPERFICIE, 99f)
    }
    private val anterior = botonTexto("‹ Anterior")
    private val siguiente = botonTexto("Reciente ›")
    private val contador = texto(14f, SUAVE)
    private val navegacion = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(anterior)
        addView(contador, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        addView(siguiente)
    }

    private val apagar = TextView(ctx).apply {
        text = if (soloHistorial) "Cerrar" else "Apagar"
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f); setTextColor(TINTA); typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        background = redondo(Color.WHITE, 99f)
    }

    val raiz: View

    init {
        val contenido = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(tarjeta)
            addView(chip, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(18f) })
            addView(titulo, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12f) })
            addView(mensaje, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8f) })
            addView(tarjetaLink, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(16f) })
        }
        val scroll = ScrollView(ctx).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(contenido, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        raiz = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(FONDO)
            setPadding(dp(22f), dp(20f), dp(22f), dp(18f))
            addView(hora, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(fecha, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = dp(18f) })
            addView(navegacion, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12f) })
            addView(apagar, LinearLayout.LayoutParams(MATCH_PARENT, dp(58f)).apply { topMargin = dp(12f) })
        }

        // Tarjeta cuadrada, como máximo el 40 % del alto de la pantalla
        val m = ctx.resources.displayMetrics
        val lado = minOf(m.widthPixels - dp(44f), (m.heightPixels * 0.40f).toInt())
        tarjeta.layoutParams = LinearLayout.LayoutParams(lado, lado)

        apagar.setOnClickListener { acciones.apagar() }
        anterior.setOnClickListener { if (indice < paginas.size - 1) { indice++; pintar() } }
        siguiente.setOnClickListener { if (indice > 0) { indice--; pintar() } }

        // Reloj y latido suave de la tarjeta, activos solo mientras se ve la pantalla
        val latido = ObjectAnimator.ofFloat(tarjeta, View.SCALE_X, 1f, 1.025f, 1f).apply {
            duration = 1300; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { tarjeta.scaleY = tarjeta.scaleX }
        }
        val reloj = object : Runnable {
            override fun run() { actualizarHora(); main.postDelayed(this, 15_000) }
        }
        raiz.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { reloj.run(); if (!soloHistorial) latido.start() }
            override fun onViewDetachedFromWindow(v: View) { main.removeCallbacks(reloj); latido.cancel() }
        })
        actualizarHora()
        pintar()
    }

    private fun cargarPaginas(): List<Config.Mensaje> =
        if (soloHistorial) Config.historial(ctx) else Config.paginas(ctx)

    /** Llamar cuando llega una alerta nueva mientras la pantalla ya está abierta. */
    fun recargar() {
        paginas = cargarPaginas()
        indice = 0
        pintar()
    }

    private fun actualizarHora() {
        val ahora = Date()
        hora.text = SimpleDateFormat("HH:mm", Locale("es", "PE")).format(ahora)
        fecha.text = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "PE")).format(ahora)
            .replaceFirstChar { it.uppercase() }
    }

    private fun hace(tiempo: Long): String {
        val min = ((System.currentTimeMillis() / 1000 - tiempo) / 60).coerceAtLeast(0)
        return when {
            min < 1 -> "hace un momento"
            min < 60 -> "hace $min min"
            min < 60 * 24 -> "hace ${min / 60} h"
            else -> SimpleDateFormat("dd/MM HH:mm", Locale("es", "PE")).format(Date(tiempo * 1000))
        }
    }

    private fun pintar() {
        val t = ++turno
        if (paginas.isEmpty()) {
            titulo.text = "Sin mensajes"
            mensaje.text = "Aún no has recibido alertas."
            chip.visibility = View.GONE
            tarjetaLink.visibility = View.GONE
            navegacion.visibility = View.GONE
            imagen.setImageBitmap(null)
            return
        }
        val p = paginas[indice]
        titulo.text = p.titulo
        val enlace = Enlaces.primero(p.mensaje)
        // Si el mensaje es solo el enlace, no se repite como texto
        val txt = p.mensaje.trim()
        mensaje.text = if (enlace != null && (txt == enlace || "https://$txt" == enlace)) "" else txt
        mensaje.visibility = if (mensaje.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        chip.visibility = View.VISIBLE
        chip.text = if (indice == 0 && !soloHistorial) "Nueva alerta" else "Mensaje anterior · ${hace(p.tiempo)}"

        // Navegación (solo si hay más de un mensaje)
        navegacion.visibility = if (paginas.size > 1) View.VISIBLE else View.GONE
        contador.text = "${indice + 1} de ${paginas.size}"
        anterior.alpha = if (indice < paginas.size - 1) 1f else 0.3f
        siguiente.alpha = if (indice > 0) 1f else 0.3f

        // Imagen (se decodifica fuera del hilo principal)
        imagen.setImageBitmap(null)
        Thread {
            val bmp = cargarImagen(p.imagen)
            main.post { if (t == turno) imagen.setImageBitmap(bmp) }
        }.start()

        // Enlace con vista previa
        if (enlace == null) {
            tarjetaLink.visibility = View.GONE
        } else {
            tarjetaLink.visibility = View.VISIBLE
            linkTitulo.text = "Cargando vista previa…"
            linkSitio.text = Uri.parse(enlace).host?.removePrefix("www.") ?: enlace
            linkImg.setImageBitmap(null)
            tarjetaLink.setOnClickListener { acciones.abrirEnlace(enlace) }
            Thread {
                val v = Enlaces.obtener(enlace)
                main.post {
                    if (t != turno) return@post
                    linkTitulo.text = v.titulo
                    linkSitio.text = v.sitio
                }
                val mini = Enlaces.miniatura(v.imagen, 480)
                main.post { if (t == turno && mini != null) linkImg.setImageBitmap(mini) }
            }.start()
        }
    }

    private fun cargarImagen(img: String): Bitmap? = try {
        val maxLado = 1080
        if (img.startsWith("file:///android_asset/")) {
            ctx.assets.open(img.removePrefix("file:///android_asset/")).use { BitmapFactory.decodeStream(it) }
        } else {
            val ruta = Uri.parse(img).path ?: img.removePrefix("file://")
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(ruta, o)
            var s = 1
            while (o.outWidth / (s * 2) >= maxLado || o.outHeight / (s * 2) >= maxLado) s *= 2
            BitmapFactory.decodeFile(ruta, BitmapFactory.Options().apply { inSampleSize = s })
                ?: ctx.assets.open("imagen.png").use { BitmapFactory.decodeStream(it) }
        }
    } catch (e: Exception) { null }
}
