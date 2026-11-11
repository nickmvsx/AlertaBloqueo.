package pe.nick.alertabloqueo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Llega a la hora programada: muestra la alerta local y, si es diaria, programa la siguiente. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Config.setActual(c, Config.titulo(c), Config.mensaje(c), Config.IMAGEN_DEFECTO)
        Programador.mostrar(c)
        val prueba = i.getBooleanExtra(Programador.EXTRA_PRUEBA, false)
        if (!prueba && Config.activo(c)) {
            if (Config.diario(c)) Programador.programarSegunConfig(c)
            else Config.guardar(c, Config.titulo(c), Config.mensaje(c), Config.hora(c), false, false)
        }
    }
}

/** Botón "Apagar" de la notificación. */
class DismissReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = Programador.apagar(c)
}

/** Al reiniciar el teléfono: vuelve a programar la alerta y a escuchar mensajes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED) {
            Programador.programarSegunConfig(c)
            if (Config.recibir(c)) EscuchaService.iniciar(c)
        }
    }
}
