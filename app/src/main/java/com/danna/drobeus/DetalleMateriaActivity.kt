package com.danna.drobeus

import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.danna.drobeus.databinding.ActivityDetalleMateriaBinding
import com.danna.drobeus.databinding.DialogFormBinding
import com.danna.drobeus.databinding.ItemCardBinding
import com.danna.drobeus.model.Apunte
import com.danna.drobeus.model.ApunteRequest
import com.danna.drobeus.model.ArchivoInfo
import com.danna.drobeus.model.MensajeResponse
import com.danna.drobeus.model.Nota
import com.danna.drobeus.model.NotaRequest
import com.danna.drobeus.model.Tarea
import com.danna.drobeus.model.TareaRequest
import com.danna.drobeus.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.File
import java.util.Calendar
import java.util.Locale

private enum class TipoCampo { TEXTO, LARGO, FECHA, NUMERO }

private data class CampoForm(
    val hint: String,
    val valor: String = "",
    val tipo: TipoCampo = TipoCampo.TEXTO
)

private class Fila(val fecha: String, val agregar: () -> Unit)

// Dentro de una materia: Apuntes (texto, fotos y archivos), Trabajos, Fechas y Notas
class DetalleMateriaActivity : AppCompatActivity() {

    private enum class Pestana { APUNTES, TRABAJOS, FECHAS, NOTAS }

    private lateinit var binding: ActivityDetalleMateriaBinding
    private lateinit var prefs: UserPrefs
    private var materiaId = ""
    private var colorMateria = Color.WHITE
    private var actual = Pestana.APUNTES
    private var fotoUri: Uri? = null

    // Elegir una foto, PDF o cualquier documento del celular
    private val selectorArchivo = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) subirDesdeUri(uri)
    }

    // Tomar una foto con la cámara
    private val tomarFoto = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { tomada ->
        val uri = fotoUri
        if (tomada && uri != null) subirDesdeUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetalleMateriaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = UserPrefs(this)
        materiaId = intent.getStringExtra("id") ?: ""
        if (!prefs.isLogged() || materiaId.isEmpty()) {
            finish()
            return
        }

        binding.tvNombre.text = intent.getStringExtra("nombre") ?: ""
        binding.tvInfo.text = listOf(intent.getStringExtra("profesor"), intent.getStringExtra("aula"))
            .filter { !it.isNullOrBlank() }.joinToString(" · ")
        colorMateria = Util.color(intent.getStringExtra("color"))

        binding.tvVolver.setOnClickListener { finish() }
        binding.tvOpciones.setOnClickListener { opcionesMateria() }
        binding.tabApuntes.setOnClickListener { seleccionar(Pestana.APUNTES) }
        binding.tabTrabajos.setOnClickListener { seleccionar(Pestana.TRABAJOS) }
        binding.tabFechas.setOnClickListener { seleccionar(Pestana.FECHAS) }
        binding.tabNotas.setOnClickListener { seleccionar(Pestana.NOTAS) }
        binding.btnAnadir.setOnClickListener { anadir() }
        binding.etBuscar.setOnEditorActionListener { _, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_SEARCH) { cargar(); true } else false
        }

        // Desde la pantalla Notas se abre directamente en la pestaña de notas
        seleccionar(if (intent.getStringExtra("pestana") == "NOTAS") Pestana.NOTAS else Pestana.APUNTES)
    }

    // ---------- Opciones de la materia ----------
    private fun opcionesMateria() {
        AlertDialog.Builder(this)
            .setTitle(binding.tvNombre.text.toString())
            .setItems(arrayOf("Eliminar materia")) { _, _ ->
                confirmar(
                    "Eliminar materia",
                    "Se eliminará esta materia junto con sus apuntes, archivos, tareas y notas."
                ) { eliminarMateria() }
            }
            .show()
    }

    private fun eliminarMateria() {
        lifecycleScope.launch {
            try {
                val r = ApiClient.api.eliminarMateria(materiaId)
                if (r.isSuccessful) {
                    Util.toast(this@DetalleMateriaActivity, "Materia eliminada")
                    finish()
                } else {
                    Util.toast(this@DetalleMateriaActivity, Util.mensajeDeError(r.errorBody()?.string()))
                }
            } catch (e: Exception) {
                Util.toast(this@DetalleMateriaActivity, Util.errorTexto(e))
            }
        }
    }

    // ---------- Pestañas ----------
    private fun seleccionar(p: Pestana) {
        actual = p

        mapOf(
            Pestana.APUNTES to binding.tabApuntes,
            Pestana.TRABAJOS to binding.tabTrabajos,
            Pestana.FECHAS to binding.tabFechas,
            Pestana.NOTAS to binding.tabNotas
        ).forEach { (pestana, vista) ->
            val activa = pestana == p
            vista.setTypeface(null, if (activa) Typeface.BOLD else Typeface.NORMAL)
            vista.alpha = if (activa) 1f else 0.6f
            vista.setBackgroundResource(if (activa) R.drawable.bg_field else 0)
        }

        binding.etBuscar.visibility = if (p == Pestana.APUNTES) View.VISIBLE else View.GONE
        binding.tvResumen.visibility = if (p == Pestana.NOTAS) View.VISIBLE else View.GONE
        binding.btnAnadir.text = when (p) {
            Pestana.APUNTES -> "+ Añadir apunte, foto o archivo"
            Pestana.TRABAJOS -> "+ Añadir trabajo"
            Pestana.FECHAS -> "+ Añadir fecha"
            Pestana.NOTAS -> "+ Añadir nota"
        }
        cargar()
    }

    private fun cargar() {
        lifecycleScope.launch {
            try {
                when (actual) {
                    Pestana.APUNTES -> cargarApuntes()
                    Pestana.TRABAJOS -> cargarTareas(false)
                    Pestana.FECHAS -> cargarTareas(true)
                    Pestana.NOTAS -> cargarNotas()
                }
            } catch (e: Exception) {
                Util.toast(this@DetalleMateriaActivity, Util.errorTexto(e))
            }
        }
    }

    // ---------- Apuntes, fotos y archivos ----------
    private suspend fun cargarApuntes() {
        val pestana = actual
        val buscar = binding.etBuscar.text.toString().trim().ifEmpty { null }

        val rApuntes = ApiClient.api.apuntes(materiaId, buscar)
        if (rApuntes.code() == 401) { sesionExpirada(); return }
        val rArchivos = ApiClient.api.archivos(materiaId)
        if (pestana != actual) return

        val apuntes = rApuntes.body()?.apuntes ?: emptyList()
        val archivos = (if (rArchivos.isSuccessful) rArchivos.body()?.archivos else null)
            .orEmpty()
            .filter { buscar == null || it.nombre.contains(buscar, ignoreCase = true) }

        val filas = mutableListOf<Fila>()
        apuntes.forEach { a ->
            val resumen = (a.contenido ?: "").replace("\n", " ").take(60)
            filas.add(Fila(a.createdAt ?: "") {
                agregarItem(a.titulo, "$resumen · ${Util.fechaBonita(a.createdAt)}") { opcionesApunte(a) }
            })
        }
        archivos.forEach { f ->
            filas.add(Fila(f.createdAt ?: "") {
                agregarItem(
                    f.nombre,
                    "${tipoArchivo(f.mime)} · ${tamanoTexto(f.tamano)} · ${Util.fechaBonita(f.createdAt)}"
                ) { opcionesArchivo(f) }
            })
        }

        binding.lista.removeAllViews()
        filas.sortByDescending { it.fecha }
        filas.forEach { it.agregar() }
        mostrarVacio(filas.isEmpty(), "Aún no hay apuntes. Pulsa + Añadir.")
    }

    private fun opcionesApunte(a: Apunte) {
        AlertDialog.Builder(this)
            .setTitle(a.titulo)
            .setItems(arrayOf("Ver completo", "Editar", "Eliminar")) { _, cual ->
                when (cual) {
                    0 -> AlertDialog.Builder(this)
                        .setTitle(a.titulo)
                        .setMessage(a.contenido ?: "(sin contenido)")
                        .setPositiveButton("Cerrar", null).show()
                    1 -> formularioApunte(a)
                    else -> confirmar("Eliminar apunte", "¿Eliminar \"${a.titulo}\"?") {
                        ejecutar("Apunte eliminado") { ApiClient.api.eliminarApunte(a.id) }
                    }
                }
            }.show()
    }

    private fun formularioApunte(existente: Apunte?) {
        formulario(
            if (existente == null) "Nuevo apunte" else "Editar apunte",
            listOf(
                CampoForm("Título", existente?.titulo ?: ""),
                CampoForm("Contenido", existente?.contenido ?: "", TipoCampo.LARGO)
            )
        ) { v ->
            if (v[0].isEmpty()) return@formulario "Escribe el título"
            if (existente == null) {
                ejecutar("Apunte creado") {
                    ApiClient.api.crearApunte(
                        ApunteRequest(materia = materiaId, titulo = v[0], tipo = "texto", contenido = v[1])
                    )
                }
            } else {
                ejecutar("Apunte actualizado") {
                    ApiClient.api.editarApunte(existente.id, ApunteRequest(titulo = v[0], contenido = v[1]))
                }
            }
            null
        }
    }

    // ---------- Archivos y fotos ----------
    private fun opcionesArchivo(f: ArchivoInfo) {
        AlertDialog.Builder(this)
            .setTitle(f.nombre)
            .setItems(arrayOf("Abrir", "Eliminar")) { _, cual ->
                if (cual == 0) abrirArchivo(f)
                else confirmar("Eliminar archivo", "¿Eliminar \"${f.nombre}\"?") {
                    ejecutar("Archivo eliminado") { ApiClient.api.eliminarArchivo(f.id) }
                }
            }.show()
    }

    private fun lanzarCamara() {
        val carpeta = File(cacheDir, "archivos").apply { mkdirs() }
        val archivo = File(carpeta, "foto_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", archivo)
        fotoUri = uri
        tomarFoto.launch(uri)
    }

    private fun subirDesdeUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                Util.toast(this@DetalleMateriaActivity, "Subiendo archivo...")
                val leido = withContext(Dispatchers.IO) { Archivos.leer(this@DetalleMateriaActivity, uri) }
                if (leido == null) {
                    Util.toast(this@DetalleMateriaActivity, "No se pudo leer el archivo")
                    return@launch
                }
                if (leido.bytes.size > 10 * 1024 * 1024) {
                    Util.toast(this@DetalleMateriaActivity, "El archivo supera los 10 MB")
                    return@launch
                }

                val r = ApiClient.api.subirArchivo(
                    Archivos.parte("archivo", leido),
                    Archivos.texto(materiaId),
                    Archivos.texto(leido.nombre)
                )
                if (r.isSuccessful) {
                    Util.toast(this@DetalleMateriaActivity, "Archivo guardado")
                    cargar()
                } else {
                    Util.toast(this@DetalleMateriaActivity, Util.mensajeDeError(r.errorBody()?.string()))
                }
            } catch (e: Exception) {
                Util.toast(this@DetalleMateriaActivity, Util.errorTexto(e))
            }
        }
    }

    // Descarga el archivo al celular y lo abre con la app que corresponda
    private fun abrirArchivo(f: ArchivoInfo) {
        lifecycleScope.launch {
            try {
                Util.toast(this@DetalleMateriaActivity, "Abriendo ${f.nombre}...")
                val r = ApiClient.api.descargarArchivo(f.id)
                val cuerpo = r.body()
                if (!r.isSuccessful || cuerpo == null) {
                    Util.toast(this@DetalleMateriaActivity, "No se pudo descargar el archivo")
                    return@launch
                }

                val carpeta = File(cacheDir, "archivos").apply { mkdirs() }
                val destino = File(carpeta, nombreSeguro(f.nombre))
                withContext(Dispatchers.IO) {
                    cuerpo.byteStream().use { entrada ->
                        destino.outputStream().use { salida -> entrada.copyTo(salida) }
                    }
                }

                val uri = FileProvider.getUriForFile(
                    this@DetalleMateriaActivity, "$packageName.fileprovider", destino
                )
                val intent = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, f.mime ?: "*/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try {
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Util.toast(this@DetalleMateriaActivity, "No tienes una app para abrir este tipo de archivo")
                }
            } catch (e: Exception) {
                Util.toast(this@DetalleMateriaActivity, Util.errorTexto(e))
            }
        }
    }

    private fun nombreSeguro(nombre: String) = nombre.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun tipoArchivo(mime: String?): String = when {
        mime == null -> "Archivo"
        mime.startsWith("image/") -> "Foto"
        mime == "application/pdf" -> "PDF"
        mime.contains("word") -> "Word"
        else -> "Archivo"
    }

    private fun tamanoTexto(bytes: Long?): String {
        val b = bytes ?: return ""
        return when {
            b < 1024 -> "$b B"
            b < 1024 * 1024 -> "${b / 1024} KB"
            else -> String.format(Locale.US, "%.1f MB", b / 1048576.0)
        }
    }

    // ---------- Trabajos (taller, informe, tarea) y Fechas (parcial, examen, quiz) ----------
    private suspend fun cargarTareas(fechas: Boolean) {
        val pestana = actual
        val r = ApiClient.api.tareas(materiaId)
        if (r.code() == 401) { sesionExpirada(); return }
        if (pestana != actual) return

        val lista = (r.body()?.tareas ?: emptyList())
            .filter { ((it.tipo ?: "").lowercase() in Util.TIPOS_FECHA) == fechas }
            .sortedBy { it.fechaEntrega }

        binding.lista.removeAllViews()
        lista.forEach { t ->
            val dias = Util.diasHasta(t.fechaEntrega)
            val estado = when {
                t.estaEntregada -> "Entregada ✓"
                dias == null -> ""
                dias < 0 -> "Vencida hace ${-dias} día(s)"
                dias == 0 -> "Hoy"
                dias == 1 -> "Mañana"
                else -> "En $dias días"
            }
            agregarItem(
                t.titulo,
                "${Util.capitalizar(t.tipo)} · ${Util.fechaBonita(t.fechaEntrega)} · $estado",
                atenuado = t.estaEntregada
            ) { opcionesTarea(t, fechas) }
        }
        mostrarVacio(
            lista.isEmpty(),
            if (fechas) "No hay fechas de parciales. Pulsa + Añadir fecha."
            else "No hay trabajos. Pulsa + Añadir trabajo."
        )
    }

    private fun opcionesTarea(t: Tarea, fechas: Boolean) {
        val opciones = if (t.estaEntregada) arrayOf("Editar", "Eliminar")
        else arrayOf("Marcar como entregada", "Editar", "Eliminar")

        AlertDialog.Builder(this)
            .setTitle(t.titulo)
            .setItems(opciones) { _, cual ->
                when (opciones[cual]) {
                    "Marcar como entregada" ->
                        ejecutar("Marcada como entregada") { ApiClient.api.entregarTarea(t.id) }
                    "Editar" -> formularioTarea(t, fechas)
                    else -> confirmar("Eliminar", "¿Eliminar \"${t.titulo}\"?") {
                        ejecutar("Eliminado") { ApiClient.api.eliminarTarea(t.id) }
                    }
                }
            }.show()
    }

    private fun formularioTarea(existente: Tarea?, fechas: Boolean) {
        val tipoDefecto = if (fechas) "parcial" else "tarea"
        formulario(
            when {
                existente == null && fechas -> "Nueva fecha"
                existente == null -> "Nuevo trabajo"
                else -> "Editar"
            },
            listOf(
                CampoForm(if (fechas) "Título (ej. Parcial 1)" else "Título", existente?.titulo ?: ""),
                CampoForm(
                    if (fechas) "Tipo (parcial, examen, quiz)" else "Tipo (taller, informe, tarea)",
                    existente?.tipo ?: tipoDefecto
                ),
                CampoForm("Fecha (toca para elegir)", existente?.fechaEntrega?.take(10) ?: "", TipoCampo.FECHA)
            )
        ) { v ->
            val tipo = v[1].lowercase().ifEmpty { tipoDefecto }
            when {
                v[0].isEmpty() -> return@formulario "Escribe el título"
                v[2].isEmpty() -> return@formulario "Elige la fecha"
                fechas && tipo !in Util.TIPOS_FECHA -> return@formulario "El tipo debe ser parcial, examen o quiz"
                !fechas && tipo in Util.TIPOS_FECHA -> return@formulario "Los parciales van en la pestaña Fechas"
            }
            if (existente == null) {
                ejecutar("Guardado") {
                    ApiClient.api.crearTarea(
                        TareaRequest(materia = materiaId, titulo = v[0], tipo = tipo, fechaEntrega = v[2])
                    )
                }
            } else {
                ejecutar("Actualizado") {
                    ApiClient.api.editarTarea(
                        existente.id, TareaRequest(titulo = v[0], tipo = tipo, fechaEntrega = v[2])
                    )
                }
            }
            null
        }
    }

    // ---------- Notas ----------
    private suspend fun cargarNotas() {
        val pestana = actual
        val r = ApiClient.api.notas(materiaId)
        if (r.code() == 401) { sesionExpirada(); return }
        if (pestana != actual) return

        val lista = r.body()?.notas ?: emptyList()
        binding.lista.removeAllViews()
        lista.forEach { n ->
            agregarItem(n.descripcion, "Nota ${formato(n.valor)} · ${formato(n.porcentaje ?: 0.0)}%") {
                opcionesNota(n)
            }
        }

        // Promedio ponderado de esta materia
        val porcentaje = lista.sumOf { it.porcentaje ?: 0.0 }
        val ponderado = lista.sumOf { it.valor * (it.porcentaje ?: 0.0) }
        binding.tvResumen.text = if (porcentaje > 0) {
            "PROMEDIO: ${String.format(Locale.US, "%.2f", ponderado / porcentaje)}" +
                    "   ·   Evaluado: ${formato(porcentaje)}%   ·   ${lista.size} notas"
        } else {
            "Aún no tienes notas registradas"
        }
        mostrarVacio(lista.isEmpty(), "Aún no hay notas. Pulsa + Añadir nota.")
    }

    private fun opcionesNota(n: Nota) {
        AlertDialog.Builder(this)
            .setTitle(n.descripcion)
            .setItems(arrayOf("Editar", "Eliminar")) { _, cual ->
                if (cual == 0) formularioNota(n)
                else confirmar("Eliminar nota", "¿Eliminar \"${n.descripcion}\"?") {
                    ejecutar("Nota eliminada") { ApiClient.api.eliminarNota(n.id) }
                }
            }.show()
    }

    private fun formularioNota(existente: Nota?) {
        formulario(
            if (existente == null) "Nueva nota" else "Editar nota",
            listOf(
                CampoForm("Descripción (ej. Quiz 1)", existente?.descripcion ?: ""),
                CampoForm("Nota (0 a 5)", existente?.let { formato(it.valor) } ?: "", TipoCampo.NUMERO),
                CampoForm("Porcentaje (0 a 100)", existente?.porcentaje?.let { formato(it) } ?: "", TipoCampo.NUMERO)
            )
        ) { v ->
            val valor = v[1].replace(',', '.').toDoubleOrNull()
            val porcentaje = v[2].replace(',', '.').toDoubleOrNull()
            when {
                v[0].isEmpty() -> return@formulario "Escribe la descripción"
                valor == null || valor < 0.0 || valor > 5.0 -> return@formulario "La nota debe estar entre 0 y 5"
                porcentaje == null || porcentaje < 0.0 || porcentaje > 100.0 ->
                    return@formulario "El porcentaje debe estar entre 0 y 100"
            }
            if (existente == null) {
                ejecutar("Nota registrada") {
                    ApiClient.api.crearNota(
                        NotaRequest(materia = materiaId, descripcion = v[0], valor = valor, porcentaje = porcentaje)
                    )
                }
            } else {
                ejecutar("Nota actualizada") {
                    ApiClient.api.editarNota(
                        existente.id, NotaRequest(descripcion = v[0], valor = valor, porcentaje = porcentaje)
                    )
                }
            }
            null
        }
    }

    // ---------- Botón "+ Añadir" ----------
    private fun anadir() {
        when (actual) {
            Pestana.APUNTES -> AlertDialog.Builder(this)
                .setTitle("Añadir a Apuntes")
                .setItems(arrayOf("Escribir un apunte", "Tomar una foto", "Subir foto, PDF o documento")) { _, cual ->
                    when (cual) {
                        0 -> formularioApunte(null)
                        1 -> lanzarCamara()
                        else -> selectorArchivo.launch(arrayOf("*/*"))
                    }
                }.show()
            Pestana.TRABAJOS -> formularioTarea(null, false)
            Pestana.FECHAS -> formularioTarea(null, true)
            Pestana.NOTAS -> formularioNota(null)
        }
    }

    // ---------- Formulario genérico ----------
    // alGuardar devuelve un mensaje de error si algo está mal, o null si todo está bien
    private fun formulario(
        titulo: String,
        campos: List<CampoForm>,
        alGuardar: (List<String>) -> String?
    ) {
        val f = DialogFormBinding.inflate(layoutInflater)
        val vistas = listOf(f.etA, f.etB, f.etC, f.etD)

        vistas.forEachIndexed { i, v ->
            val c = campos.getOrNull(i)
            if (c == null) {
                v.visibility = View.GONE
                return@forEachIndexed
            }
            v.visibility = View.VISIBLE
            v.hint = c.hint
            v.setText(c.valor)
            when (c.tipo) {
                TipoCampo.LARGO -> {
                    v.setSingleLine(false)
                    v.minLines = 3
                    v.gravity = Gravity.TOP
                    v.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    v.inputType = InputType.TYPE_CLASS_TEXT or
                            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                }
                TipoCampo.FECHA -> {
                    v.isFocusable = false
                    v.isClickable = true
                    v.setOnClickListener { elegirFecha(v) }
                }
                TipoCampo.NUMERO -> v.inputType =
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                TipoCampo.TEXTO -> v.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            }
        }

        val dialogo = AlertDialog.Builder(this)
            .setTitle(titulo)
            .setView(f.root)
            .setPositiveButton("Guardar", null)
            .setNegativeButton("Cancelar", null)
            .create()

        dialogo.setOnShowListener {
            dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val valores = vistas.take(campos.size).map { it.text.toString().trim() }
                val error = alGuardar(valores)
                if (error != null) Util.toast(this, error) else dialogo.dismiss()
            }
        }
        dialogo.show()
    }

    private fun elegirFecha(campo: EditText) {
        val hoy = Calendar.getInstance()
        val partes = campo.text.toString().split("-")
        val anio = partes.getOrNull(0)?.toIntOrNull() ?: hoy.get(Calendar.YEAR)
        val mes = (partes.getOrNull(1)?.toIntOrNull() ?: (hoy.get(Calendar.MONTH) + 1)) - 1
        val dia = partes.getOrNull(2)?.toIntOrNull() ?: hoy.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, a, m, d ->
            campo.setText(String.format(Locale.US, "%04d-%02d-%02d", a, m + 1, d))
        }, anio, mes, dia).show()
    }

    // ---------- Utilidades ----------
    private fun agregarItem(
        titulo: String,
        detalle: String,
        atenuado: Boolean = false,
        alTocar: () -> Unit
    ) {
        val item = ItemCardBinding.inflate(layoutInflater, binding.lista, false)
        item.tvTitulo.text = titulo
        item.tvDetalle.text = detalle
        item.dot.backgroundTintList = ColorStateList.valueOf(colorMateria)
        item.root.alpha = if (atenuado) 0.55f else 1f
        item.root.setOnClickListener { alTocar() }
        binding.lista.addView(item.root)
    }

    private fun mostrarVacio(vacio: Boolean, texto: String) {
        binding.tvVacio.text = texto
        binding.tvVacio.visibility = if (vacio) View.VISIBLE else View.GONE
    }

    private fun confirmar(titulo: String, mensaje: String, accion: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(titulo)
            .setMessage(mensaje)
            .setPositiveButton("Eliminar") { _, _ -> accion() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun ejecutar(exito: String, llamada: suspend () -> Response<MensajeResponse>) {
        lifecycleScope.launch {
            try {
                val r = llamada()
                if (r.code() == 401) { sesionExpirada(); return@launch }
                if (r.isSuccessful) {
                    Util.toast(this@DetalleMateriaActivity, exito)
                    cargar()
                } else {
                    Util.toast(this@DetalleMateriaActivity, Util.mensajeDeError(r.errorBody()?.string()))
                }
            } catch (e: Exception) {
                Util.toast(this@DetalleMateriaActivity, Util.errorTexto(e))
            }
        }
    }

    private fun sesionExpirada() {
        prefs.logout()
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    // 3.0 -> "3", 3.5 -> "3.5"
    private fun formato(n: Double): String =
        if (n % 1.0 == 0.0) n.toInt().toString() else String.format(Locale.US, "%.2f", n).trimEnd('0')
}
