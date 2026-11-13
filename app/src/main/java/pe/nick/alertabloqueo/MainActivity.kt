package pe.nick.alertabloqueo

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.webkit.WebSettings
import android.widget.FrameLayout
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Panel: muestra assets/panel.html y le da funciones nativas vía "Android". */
class MainActivity : Activity() {

    private lateinit var web: WebView
    @Volatile private var imagenBytes: ByteArray? = null
    private val ELEGIR_IMAGEN = 7
    private val TOMAR_FOTO = 8
    private val FONDO = Color.parseColor("#121212")

    /** Archivo fijo donde la cámara guarda la foto (sobrevive si Android cierra la app mientras tanto). */
    private fun archivoFoto() = File(cacheDir, "camara/foto.jpg")
    private fun uriFoto() = FileProvider.getUriForFile(this, "$packageName.archivos", archivoFoto())

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Programador.crearCanal(this)

        // La app dibuja de borde a borde y deja margen exacto para la barra de estado,
        // la barra de navegación y el teclado: así la barra inferior nunca se corta.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = FONDO
        @Suppress("DEPRECATION")
        window.navigationBarColor = FONDO
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        web = WebView(this)
        web.setBackgroundColor(FONDO)
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        web.isVerticalScrollBarEnabled = false
        web.isHorizontalScrollBarEnabled = false
        web.overScrollMode = View.OVER_SCROLL_NEVER
        with(web.settings) {
            javaScriptEnabled = true
            allowFileAccess = true
            textZoom = 100                       // ignora el tamaño de letra del sistema (evita que se desarme)
            setSupportZoom(false)
            cacheMode = WebSettings.LOAD_NO_CACHE
            mediaPlaybackRequiresUserGesture = true
        }
        web.addJavascriptInterface(Puente(), "Android")
        web.loadUrl("file:///android_asset/panel.html")

        val contenedor = FrameLayout(this).apply {
            setBackgroundColor(FONDO)
            addView(web, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        ViewCompat.setOnApplyWindowInsetsListener(contenedor) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(b.left, b.top, b.right, maxOf(b.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }
        setContentView(contenedor)

        if (Build.VERSION.SDK_INT >= 33 && !notifOk()) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        } else if (Config.recibir(this)) {
            EscuchaService.iniciar(this)
        }
    }

    override fun onResume() {
        super.onResume()
        js("window.refrescar && refrescar()")
    }

    private fun js(codigo: String) = runOnUiThread { web.evaluateJavascript(codigo, null) }

    private fun notifOk() = Build.VERSION.SDK_INT < 33 ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun bateriaOk() =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun fmt(t: Long) = SimpleDateFormat("EEE dd/MM HH:mm", Locale("es", "PE")).format(Date(t))

    inner class Puente {
        private val c get() = this@MainActivity

        @JavascriptInterface
        fun estado(): String = JSONObject()
            .put("titulo", Config.titulo(c))
            .put("mensaje", Config.mensaje(c))
            .put("hora", Config.hora(c))
            .put("diario", Config.diario(c))
            .put("activo", Config.activo(c))
            .put("codigo", Config.codigoBonito(c))
            .put("recibir", Config.recibir(c))
            .put("destino", Config.ultimoDestino(c))
            .put("permNotif", notifOk())
            .put("permPantalla", Programador.puedePantallaCompleta(c))
            .put("permExacta", Programador.puedeAlarmaExacta(c))
            .put("permBateria", bateriaOk())
            .put("permSobre", Programador.puedeSobreponer(c))
            .put("version", packageManager.getPackageInfo(packageName, 0).versionName ?: "?")
            .put("diag", Config.diag(c))
            .put("marca", Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE)
            .put("proxima", if (Config.activo(c)) fmt(Programador.proxima(Config.hora(c))) else "")
            .toString()

        // --- Alerta programada ---
        @JavascriptInterface
        fun guardar(titulo: String, mensaje: String, hora: String, diario: Boolean, activo: Boolean): String {
            Config.guardar(c, titulo, mensaje, hora, diario, activo)
            val t = Programador.programarSegunConfig(c)
            return if (t == null) "Alerta programada desactivada." else "Alerta programada para ${fmt(t)}."
        }

        @JavascriptInterface
        fun probar(segundos: Int): String {
            Programador.programar(c, System.currentTimeMillis() + segundos * 1000L, prueba = true)
            return "Prueba en $segundos s. Bloquea el teléfono ahora."
        }

        // --- Recibir ---
        @JavascriptInterface
        fun setRecibir(v: Boolean) {
            Config.setRecibir(c, v)
            if (v) EscuchaService.iniciar(c) else EscuchaService.detener(c)
        }

        @JavascriptInterface
        fun compartirCodigo() = runOnUiThread {
            val texto = "Mi código de Alerta Bloqueo es: ${Config.codigoBonito(c)}"
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto),
                "Compartir código"
            ))
        }

        // --- Enviar ---
        @JavascriptInterface
        fun elegirImagen() = runOnUiThread {
            @Suppress("DEPRECATION")
            startActivityForResult(
                Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),
                ELEGIR_IMAGEN
            )
        }

        @JavascriptInterface
        fun tomarFoto() = runOnUiThread {
            val f = archivoFoto()
            f.parentFile?.mkdirs()
            f.delete()
            val uri = uriFoto()
            val i = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                .putExtra(MediaStore.EXTRA_OUTPUT, uri)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            i.clipData = ClipData.newRawUri("foto", uri)
            try {
                @Suppress("DEPRECATION")
                startActivityForResult(i, TOMAR_FOTO)
            } catch (e: Exception) {
                js("avisoImagen('No se encontró una app de cámara.')")
            }
        }

        @JavascriptInterface
        fun quitarImagen() { imagenBytes = null }

        @JavascriptInterface
        fun miCodigo(): String = Config.codigoBonito(c)

        @JavascriptInterface
        fun enviar(codigo: String, titulo: String, mensaje: String) {
            Config.setUltimoDestino(c, codigo)
            val bytes = imagenBytes
            Thread {
                val res = Envio.enviar(codigo, titulo.ifBlank { "¡Atención!" }, mensaje, bytes)
                val ok = res.startsWith("Alerta enviada")
                js("enviado(" + JSONObject.quote(res) + "," + ok + ")")
            }.start()
        }

        // --- Permisos ---
        @JavascriptInterface
        fun pedirNotificaciones() = runOnUiThread {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        @JavascriptInterface
        fun pedirPantallaCompleta() = runOnUiThread {
            if (Build.VERSION.SDK_INT >= 34) startActivity(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
            )
        }

        @JavascriptInterface
        fun pedirAlarmaExacta() = runOnUiThread {
            if (Build.VERSION.SDK_INT >= 31) startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
            )
        }

        @SuppressLint("BatteryLife")
        @JavascriptInterface
        fun pedirBateria() = runOnUiThread {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        }

        @JavascriptInterface
        fun pedirSobreApps() = runOnUiThread {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }

        @JavascriptInterface
        fun abrirAjustesApp() = runOnUiThread {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = when (requestCode) {
            ELEGIR_IMAGEN -> data?.data
            TOMAR_FOTO -> if (archivoFoto().length() > 0) uriFoto() else null
            else -> null
        } ?: return
        procesarImagen(uri)
    }

    /** Endereza (EXIF), reduce y guarda la imagen; el HTML la previsualiza desde un archivo (más rápido que base64). */
    private fun procesarImagen(uri: Uri) {
        js("imagenCargando()")
        Thread {
            val bytes = Envio.prepararImagen(this, uri)
            imagenBytes = bytes
            if (bytes != null) {
                val dir = File(cacheDir, "previa").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val f = File(dir, "previa_${System.currentTimeMillis()}.jpg")
                f.writeBytes(bytes)
                js("imagenElegida(" + JSONObject.quote("file://" + f.absolutePath) + ")")
            } else {
                js("imagenElegida(null)")
            }
        }.start()
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        if (Config.recibir(this) && notifOk()) EscuchaService.iniciar(this)
        js("window.refrescar && refrescar()")
    }
}
