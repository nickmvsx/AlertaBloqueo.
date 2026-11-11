package pe.nick.alertabloqueo

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator

/** Vibración fuerte y repetida (sin sonido). Se detiene sola a los 2 minutos. */
object Vibracion {
    private var vibrador: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())

    fun iniciar(c: Context) {
        detener()
        val v = c.applicationContext.getSystemService(Vibrator::class.java) ?: return
        vibrador = v
        val patron = Programador.PATRON
        val amplitudes = IntArray(patron.size) { if (it % 2 == 1) 255 else 0 }
        val efecto = if (v.hasAmplitudeControl())
            VibrationEffect.createWaveform(patron, amplitudes, 1)
        else
            VibrationEffect.createWaveform(patron, 1)

        if (Build.VERSION.SDK_INT >= 33) {
            v.vibrate(efecto, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(efecto, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
        handler.postDelayed({ detener() }, 2 * 60 * 1000L)
    }

    fun detener() {
        vibrador?.cancel()
        vibrador = null
        handler.removeCallbacksAndMessages(null)
    }
}
