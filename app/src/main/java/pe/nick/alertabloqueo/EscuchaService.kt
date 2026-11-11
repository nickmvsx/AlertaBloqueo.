package pe.nick.alertabloqueo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Servicio en segundo plano que mantiene una conexión con ntfy.sh y,
 * cuando otro celular envía una alerta a este código, la muestra en pantalla.
 */
class EscuchaService : Service() {

    companion object {
        private const val CANAL_ESCUCHA = "escucha"
        private const val ID = 2001

        fun iniciar(c: Context) {
            try {
                ContextCompat.startForegroundService(c, Intent(c, EscuchaService::class.java))
            } catch (e: Exception) { /* el sistema no permitió iniciarlo ahora */ }
        }

        fun detener(c: Context) {
            c.stopService(Intent(c, EscuchaService::class.java))
        }
    }

    @Volatile private var corriendo = false
    @Volatile private var conexion: HttpURLConnection? = null
    private var hilo: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CANAL_ESCUCHA, "Recepción de alertas", NotificationManager.IMPORTANCE_MIN)
        )
        val abrir = PendingIntent.getActivity(
            this, 20, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = NotificationCompat.Builder(this, CANAL_ESCUCHA)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Esperando alertas")
            .setContentText("Código: " + Config.codigoBonito(this))
            .setOngoing(true)
            .setContentIntent(abrir)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID, n)
        }

        if (hilo?.isAlive != true) {
            corriendo = true
            hilo = Thread({ bucle() }, "escucha-ntfy").also { it.start() }
        }
        return START_STICKY
    }

    private fun ahora() = System.currentTimeMillis() / 1000

    private fun bucle() {
        var espera = 2000L
        val inicio = ahora() - 30 * 60   // recupera lo enviado en los últimos 30 min mientras estaba apagado
        while (corriendo) {
            try {
                // Pide lo llegado desde el último mensaje (o desde que arrancó el servicio),
                // así no se pierde nada si se cortó la conexión.
                val desde = maxOf(Config.ultimoTiempo(this), inicio)
                val tema = Config.tema(Config.codigo(this))
                val url = URL("https://ntfy.sh/$tema/json?since=$desde")
                val con = url.openConnection() as HttpURLConnection
                con.connectTimeout = 15_000
                con.readTimeout = 120_000       // ntfy manda un "keepalive" cada ~45 s
                conexion = con
                con.inputStream.bufferedReader(Charsets.UTF_8).use { lector ->
                    espera = 2000L
                    while (corriendo) {
                        val linea = lector.readLine() ?: break
                        if (linea.isBlank()) continue
                        val o = JSONObject(linea)
                        if (o.optString("event") == "message") procesar(o)
                    }
                }
            } catch (e: Exception) {
                if (!corriendo) break
                try { Thread.sleep(espera) } catch (_: InterruptedException) { break }
                espera = (espera * 2).coerceAtMost(60_000L)
            } finally {
                conexion?.disconnect()
                conexion = null
            }
        }
    }

    private fun procesar(o: JSONObject) {
        val pm = getSystemService(PowerManager::class.java)
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "alertabloqueo:recibir")
        wl.acquire(60_000L)
        try {
            val id = o.getString("id")
            val tiempo = o.optLong("time", ahora())
            if (id == Config.ultimoId(this)) return          // ya mostrado
            Config.setUltimo(this, id, tiempo)
            if (tiempo < ahora() - 30 * 60) return           // más de 30 min de antigüedad: se ignora
            val titulo = o.optString("title").ifBlank { "¡Atención!" }
            val mensaje = o.optString("message")
            var imagen = Config.IMAGEN_DEFECTO
            val urlAdjunto = o.optJSONObject("attachment")?.optString("url")
            if (!urlAdjunto.isNullOrBlank()) {
                descargar(urlAdjunto, id)?.let { imagen = "file://" + it.absolutePath }
            }
            Config.setActual(this, titulo, mensaje, imagen)
            Programador.mostrar(this)
        } finally {
            if (wl.isHeld) wl.release()
        }
    }

    /** Descarga la imagen adjunta y borra las anteriores. */
    private fun descargar(url: String, id: String): File? = try {
        val dir = File(filesDir, "recibidas").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val destino = File(dir, "img_$id.jpg")
        val con = URL(url).openConnection() as HttpURLConnection
        con.connectTimeout = 15_000
        con.readTimeout = 30_000
        con.inputStream.use { entrada -> destino.outputStream().use { entrada.copyTo(it) } }
        con.disconnect()
        destino
    } catch (e: Exception) { null }

    override fun onDestroy() {
        corriendo = false
        try { conexion?.disconnect() } catch (_: Exception) {}
        hilo?.interrupt()
        super.onDestroy()
    }
}
