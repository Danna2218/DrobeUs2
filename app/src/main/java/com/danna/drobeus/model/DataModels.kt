package com.danna.drobeus.model

import com.google.gson.annotations.SerializedName

// ----- Materias -----
data class HorarioItem(val dia: String, val horaInicio: String, val horaFin: String)

data class MateriaResumen(
    val id: String,
    val nombre: String,
    val profesor: String?,
    val aula: String?,
    val color: String?,
    val periodo: String?,
    val horario: List<HorarioItem>?,
    val totalApuntes: Int?,
    val totalTareas: Int?
)

data class MateriasResponse(val ok: Boolean, val total: Int?, val materias: List<MateriaResumen>?)

data class MateriaRequest(
    val nombre: String? = null,
    val profesor: String? = null,
    val aula: String? = null,
    val color: String? = null,
    val periodo: String? = null,
    val horario: List<HorarioItem>? = null
)

// ----- Apuntes -----
data class Apunte(
    @SerializedName("_id") val id: String,
    val titulo: String,
    val tipo: String?,
    val contenido: String?,
    val materia: String?,
    val createdAt: String?
)

data class ApuntesResponse(val ok: Boolean, val total: Int?, val apuntes: List<Apunte>?)

data class ApunteRequest(
    val materia: String? = null,
    val titulo: String? = null,
    val tipo: String? = null,
    val contenido: String? = null
)

// ----- Tareas -----
data class Tarea(
    @SerializedName("_id") val id: String,
    val titulo: String,
    val tipo: String?,
    val materia: String?,
    val fechaEntrega: String?,
    val entregada: Boolean?,
    val completada: Boolean?,
    val hecha: Boolean?,
    val estado: String?
) {
    // El servidor guarda el estado en uno de estos campos
    val estaEntregada: Boolean
        get() = entregada == true || completada == true || hecha == true ||
                estado.equals("entregada", ignoreCase = true)
}

data class TareasResponse(val ok: Boolean, val total: Int?, val tareas: List<Tarea>?)

data class TareaRequest(
    val materia: String? = null,
    val titulo: String? = null,
    val tipo: String? = null,
    val fechaEntrega: String? = null
)

// ----- Notas -----
data class Nota(
    @SerializedName("_id") val id: String,
    val descripcion: String,
    val valor: Double,
    val porcentaje: Double?,
    val materia: String?
)

data class NotasResponse(val ok: Boolean, val total: Int?, val notas: List<Nota>?)

data class NotaRequest(
    val materia: String? = null,
    val descripcion: String? = null,
    val valor: Double? = null,
    val porcentaje: Double? = null
)

data class PromedioMateria(
    val materiaId: String,
    val materia: String,
    val color: String?,
    val totalNotas: Int,
    val porcentajeEvaluado: Double?,
    val promedio: Double?
)

data class PromedioResponse(
    val ok: Boolean,
    val periodo: String?,
    val promedioPeriodo: Double?,
    val materias: List<PromedioMateria>?
)

// ----- Agenda -----
data class DiaSemana(val dia: String, val numero: Int, val fecha: String)

data class EntregaSemana(
    val id: String,
    val titulo: String,
    val tipo: String?,
    val materia: String?,
    val color: String?,
    val fechaEntrega: String?,
    val dia: String?
)

data class SemanaResponse(
    val ok: Boolean,
    val desde: String?,
    val hasta: String?,
    val dias: List<DiaSemana>?,
    val entregas: List<EntregaSemana>?
)

data class Recordatorio(
    val tareaId: String,
    val titulo: String,
    val materia: String?,
    val mensaje: String,
    val diasRestantes: Int?
)

data class RecordatoriosResponse(val ok: Boolean, val total: Int?, val recordatorios: List<Recordatorio>?)


// ----- Importar horario desde un archivo -----
data class MateriaPropuesta(
    val nombre: String,
    val profesor: String?,
    val aula: String?,
    val horario: List<HorarioItem>?
)

data class AnalisisResponse(
    val ok: Boolean,
    val mensaje: String?,
    val total: Int?,
    val materias: List<MateriaPropuesta>?
)

data class LoteRequest(val periodo: String?, val materias: List<MateriaRequest>)

// ----- Archivos de una materia -----
data class ArchivoInfo(
    @SerializedName("_id") val id: String,
    val nombre: String,
    val mime: String?,
    val tamano: Long?,
    val createdAt: String?
)

data class ArchivosResponse(val ok: Boolean, val total: Int?, val archivos: List<ArchivoInfo>?)