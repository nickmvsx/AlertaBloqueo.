package pe.nick.alertabloqueo

import android.app.AlarmManager
import android.app.KeyguardManager
import android.os.PowerManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import java.util.Calendar

/** Programa la alarma local, muestra la notificación full-screen y la apaga. */
object Programador {
    const val CANAL = "alertas_vibracion"
    const val NOTIF_ID = 1001
    const val ACCION_CERRAR = "pe.nick.alertabloqueo.CERRAR_ALERTA"
    const val EXTRA_PRUEBA = "prueba"

    /** Patrón de vibración fuerte: 1,2 s vibra, 0,25 s pausa. */
    val PATRON = longArrayOf(0, 1200, 250, 1200, 250, 1200, 250, 1200)

    fun crearCanal(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        val canal = NotificationChannel(CANAL, "Alertas en pantalla", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alertas que encienden la pantalla y se muestran sobre el bloqueo"
            setSound(null, null)          // sin sonido
            enableVibration(true)
            vibrationPattern = PATRON
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        nm.createNotificationChannel(canal)
    }

    private fun piAlarma(c: Context, prueba: Boolean): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java).putExtra(EXTRA_PRUEBA, prueba)
        return PendingIntent.getBroadcast(
            c, if (prueba) 2 else 1, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun puedeAlarmaExacta(c: Context): Boolean {
        val am = c.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    fun puedePantallaCompleta(c: Context): Boolean {
        val nm = c.getSystemService(NotificationManager::class.java)
        return Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent()
    }

    fun programar(c: Context, cuando: Long, prueba: Boolean) {
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = piAlarma(c, prueba)
        if (puedeAlarmaExacta(c)) {
            val abrir = PendingIntent.getActivity(
                c, 3, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(cuando, abrir), pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando, pi)
        }
    }

    /** Próxima vez que toca la hora HH:mm configurada. */
    fun proxima(hora: String): Long {
        val partes = hora.split(":")
        val h = partes.getOrNull(0)?.toIntOrNull() ?: 8
        val m = partes.getOrNull(1)?.toIntOrNull() ?: 0
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis() + 1000) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    /** Programa según la configuración; si está desactivada, cancela. */
    fun programarSegunConfig(c: Context): Long? {
        if (!Config.activo(c)) { cancelar(c); return null }
        val t = proxima(Config.hora(c))
        programar(c, t, prueba = false)
        return t
    }

    fun cancelar(c: Context) {
        c.getSystemService(AlarmManager::class.java).cancel(piAlarma(c, false))
    }

    private fun cargarImagen(c: Context): Bitmap? = try {
        val img = Config.actualImagen(c)
        if (img.startsWith("file:///android_asset/")) {
            c.assets.open(img.removePrefix("file:///android_asset/")).use { BitmapFactory.decodeStream(it) }
        } else {
            BitmapFactory.decodeFile(Uri.parse(img).path)
        }
    } catch (e: Exception) { null }

    /** Publica la notificación con full-screen intent con lo guardado en Config.actual*. */
    fun mostrar(c: Context) {
        crearCanal(c)
        val abrirAlerta = PendingIntent.getActivity(
            c, 10,
            Intent(c, AlertActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val apagar = PendingIntent.getBroadcast(
            c, 11, Intent(c, DismissReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val titulo = Config.actualTitulo(c)
        val mensaje = Config.actualMensaje(c)
        val imagen = cargarImagen(c)

        val b = NotificationCompat.Builder(c, CANAL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(false)
            .setVibrate(PATRON)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(abrirAlerta)
            .setFullScreenIntent(abrirAlerta, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Apagar", apagar)
        if (imagen != null) {
            b.setLargeIcon(imagen)
            b.setStyle(NotificationCompat.BigPictureStyle().bigPicture(imagen).setSummaryText(mensaje))
        } else {
            b.setStyle(NotificationCompat.BigTextStyle().bigText(mensaje))
        }
        try {
            c.getSystemService(NotificationManager::class.java).notify(NOTIF_ID, b.build())
        } catch (e: SecurityException) { /* sin permiso de notificaciones */ }

        val km = c.getSystemService(KeyguardManager::class.java)
        val pm = c.getSystemService(PowerManager::class.java)
        val desbloqueado = pm.isInteractive && !km.isKeyguardLocked

        Config.setDiag(c,
            (if (desbloqueado) "Celular desbloqueado" else "Celular bloqueado/apagado") +
            (if (puedeSobreponer(c)) ", permiso flotante OK" else ", SIN permiso 'Mostrar sobre otras apps'"))

        if (desbloqueado && puedeSobreponer(c)) {
            // Celular en uso (en cualquier app): capa flotante a pantalla completa encima de todo.
            AlertaFlotante.mostrar(c)
        } else if (puedeSobreponer(c)) {
            // Bloqueado o apagado: además del full-screen intent, se abre la pantalla de alerta.
            try {
                c.startActivity(
                    Intent(c, AlertActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
            } catch (e: Exception) { /* queda la notificación full-screen */ }
        }
    }

    fun puedeSobreponer(c: Context) = Settings.canDrawOverlays(c)

    /** Apaga: quita la notificación y cierra la pantalla de alerta si está abierta. */
    fun apagar(c: Context) {
        Vibracion.detener()
        AlertaFlotante.ocultar(c)
        c.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        c.sendBroadcast(Intent(ACCION_CERRAR).setPackage(c.packageName))
    }
}
