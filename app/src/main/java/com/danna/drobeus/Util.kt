package com.danna.drobeus

import android.content.Context
import android.graphics.Color
import android.widget.Toast
import com.danna.drobeus.model.HorarioItem
import com.danna.drobeus.model.MensajeResponse
import com.google.gson.Gson
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Funciones compartidas por varias pantallas
object Util {

    val PALETA = listOf("#FF4F9A", "#38BDF8", "#FBBF24", "#A78BFA", "#34D399", "#FB923C")

    // Los parciales, exámenes y quices van en la pestaña "Fechas"; lo demás en "Trabajos"
    val TIPOS_FECHA = listOf("parcial", "examen", "quiz")

    private val ES = Locale.forLanguageTag("es-CO")

    fun dp(ctx: Context, valor: Int) = (valor * ctx.resources.displayMetrics.density).toInt()

    fun toast(ctx: Context, texto: String) =
        Toast.makeText(ctx, texto, Toast.LENGTH_LONG).show()

    fun color(hex: String?, defecto: Int = Color.WHITE): Int =
        try { Color.parseColor(hex) } catch (e: Exception) { defecto }

    fun capitalizar(texto: String?): String =
        (texto ?: "").replaceFirstChar { it.uppercase() }

    // Texto claro para cada tipo de error de red
    fun errorTexto(e: Exception): String = when (e) {
        is UnknownHostException, is SocketTimeoutException, is ConnectException ->
            "Sin conexión con el servidor. Si Render estaba dormido, espera un minuto e intenta de nuevo."
        else -> "Error: ${e.javaClass.simpleName}: ${e.message}"
    }

    fun mensajeDeError(cuerpo: String?, defecto: String = "No se pudo completar la acción"): String {
        val m = try {
            Gson().fromJson(cuerpo, MensajeResponse::class.java)?.mensaje
        } catch (e: Exception) {
            null
        }
        return m ?: defecto
    }

    fun promedioTexto(n: Double?): String =
        if (n == null) "—" else String.format(Locale.US, "%.2f", n)

    // ---------- Fechas ----------
    private fun utc(patron: String, locale: Locale = Locale.US) =
        SimpleDateFormat(patron, locale).apply { timeZone = TimeZone.getTimeZone("UTC") }

    private fun leerFecha(texto: String): Date? =
        try { utc("yyyy-MM-dd").parse(texto) } catch (e: Exception) { null }

    // "2026-11-20T00:00:00.000Z" -> "20 nov 2026"
    fun fechaBonita(iso: String?): String {
        val d = iso?.take(10)?.let { leerFecha(it) } ?: return "sin fecha"
        return utc("d MMM yyyy", ES).format(d)
    }

    // Días que faltan desde hoy (negativo si ya pasó)
    fun diasHasta(iso: String?): Int? {
        val d = iso?.take(10)?.let { leerFecha(it) } ?: return null
        val hoy = leerFecha(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) ?: return null
        return ((d.time - hoy.time) / 86400000L).toInt()
    }

    // "2026-11-09", "2026-11-15" -> "del 9 al 15 de noviembre"
    fun rangoSemana(desde: String?, hasta: String?): String {
        val d = desde?.take(10)?.let { leerFecha(it) } ?: return ""
        val h = hasta?.take(10)?.let { leerFecha(it) } ?: return ""
        val mes = utc("MMMM", ES)
        val diaMes = utc("d 'de' MMMM", ES)
        return if (mes.format(d) == mes.format(h)) {
            "del ${utc("d", ES).format(d)} al ${diaMes.format(h)}"
        } else {
            "del ${diaMes.format(d)} al ${diaMes.format(h)}"
        }
    }

    // ---------- Horario: "lunes 07:00-09:00, miercoles 07:00-09:00" ----------
    fun horarioATexto(h: List<HorarioItem>?): String =
        h.orEmpty().joinToString(", ") { "${it.dia} ${it.horaInicio}-${it.horaFin}" }

    // Devuelve null si el texto tiene un formato inválido; lista vacía si está en blanco
    fun textoAHorario(texto: String): List<HorarioItem>? {
        if (texto.isBlank()) return emptyList()
        val regex = Regex("^([A-Za-zÁÉÍÓÚáéíóúÑñ]+)\\s+(\\d{1,2}:\\d{2})\\s*-\\s*(\\d{1,2}:\\d{2})$")
        val resultado = mutableListOf<HorarioItem>()
        for (parte in texto.split(",", ";")) {
            val m = regex.matchEntire(parte.trim()) ?: return null
            val dia = normalizarDia(m.groupValues[1]) ?: return null
            val ini = formatoHora(m.groupValues[2]) ?: return null
            val fin = formatoHora(m.groupValues[3]) ?: return null
            resultado.add(HorarioItem(dia, ini, fin))
        }
        return resultado
    }

    private fun normalizarDia(texto: String): String? {
        val limpio = Normalizer.normalize(texto.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace("\\p{M}".toRegex(), "")
        val validos = listOf("lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo")
        return if (limpio in validos) limpio else null
    }

    private fun formatoHora(texto: String): String? {
        val m = Regex("^([01]?\\d|2[0-3]):([0-5]\\d)$").matchEntire(texto.trim()) ?: return null
        return "${m.groupValues[1].padStart(2, '0')}:${m.groupValues[2]}"
    }
}
