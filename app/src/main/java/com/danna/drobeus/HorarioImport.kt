package com.danna.drobeus

import android.net.Uri
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.danna.drobeus.model.LoteRequest
import com.danna.drobeus.model.MateriaPropuesta
import com.danna.drobeus.model.MateriaRequest
import com.danna.drobeus.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Carga automática del horario: el servidor lee el documento (foto, PDF, Word o texto),
// la app te muestra las materias detectadas y, si confirmas, las guarda.
object HorarioImport {

    // alTerminar(true) si se guardaron materias, false si no
    fun importar(
        activity: AppCompatActivity,
        uri: Uri,
        periodo: String,
        totalActual: Int,
        alTerminar: (Boolean) -> Unit
    ) {
        val progreso = AlertDialog.Builder(activity)
            .setTitle("Analizando tu horario")
            .setMessage("Estoy leyendo el documento y organizando tus materias. Puede tardar hasta un minuto...")
            .setCancelable(false)
            .create()
        progreso.show()

        activity.lifecycleScope.launch {
            try {
                val leido = withContext(Dispatchers.IO) { Archivos.leer(activity, uri) }
                if (leido == null) {
                    progreso.dismiss()
                    Util.toast(activity, "No se pudo leer el archivo")
                    alTerminar(false)
                    return@launch
                }

                val r = ApiClient.api.analizarHorario(Archivos.parte("archivo", leido))
                progreso.dismiss()

                if (!r.isSuccessful) {
                    Util.toast(
                        activity,
                        Util.mensajeDeError(r.errorBody()?.string(), "No se pudo analizar el documento")
                    )
                    alTerminar(false)
                    return@launch
                }

                val propuestas = r.body()?.materias ?: emptyList()
                if (propuestas.isEmpty()) {
                    Util.toast(
                        activity,
                        "No encontré materias en ese documento. Prueba con una imagen más clara o usa + Nueva materia."
                    )
                    alTerminar(false)
                    return@launch
                }

                confirmar(activity, propuestas, periodo, totalActual, alTerminar)
            } catch (e: Exception) {
                progreso.dismiss()
                Util.toast(activity, Util.errorTexto(e))
                alTerminar(false)
            }
        }
    }

    private fun confirmar(
        activity: AppCompatActivity,
        propuestas: List<MateriaPropuesta>,
        periodo: String,
        totalActual: Int,
        alTerminar: (Boolean) -> Unit
    ) {
        val texto = propuestas.joinToString("\n\n") { m ->
            val h = Util.horarioATexto(m.horario)
            "• ${m.nombre}" + if (h.isNotEmpty()) "\n   $h" else ""
        }

        AlertDialog.Builder(activity)
            .setTitle("Encontré ${propuestas.size} materias")
            .setMessage(texto)
            .setCancelable(false)
            .setPositiveButton("Guardar todas") { _, _ ->
                guardar(activity, propuestas, periodo, totalActual, alTerminar)
            }
            .setNegativeButton("Cancelar") { _, _ -> alTerminar(false) }
            .show()
    }

    private fun guardar(
        activity: AppCompatActivity,
        propuestas: List<MateriaPropuesta>,
        periodo: String,
        totalActual: Int,
        alTerminar: (Boolean) -> Unit
    ) {
        activity.lifecycleScope.launch {
            try {
                val lote = LoteRequest(
                    periodo = periodo.ifEmpty { null },
                    materias = propuestas.mapIndexed { i, m ->
                        MateriaRequest(
                            nombre = m.nombre,
                            profesor = m.profesor,
                            aula = m.aula,
                            color = Util.PALETA[(totalActual + i) % Util.PALETA.size],
                            horario = m.horario?.ifEmpty { null }
                        )
                    }
                )
                val r = ApiClient.api.crearMateriasLote(lote)
                if (r.isSuccessful) {
                    Util.toast(activity, "${propuestas.size} materias guardadas")
                    alTerminar(true)
                } else {
                    Util.toast(activity, Util.mensajeDeError(r.errorBody()?.string()))
                    alTerminar(false)
                }
            } catch (e: Exception) {
                Util.toast(activity, Util.errorTexto(e))
                alTerminar(false)
            }
        }
    }
}
