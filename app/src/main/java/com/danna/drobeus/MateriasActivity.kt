package com.danna.drobeus

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.danna.drobeus.databinding.ActivityMateriasBinding
import com.danna.drobeus.databinding.DialogMateriaBinding
import com.danna.drobeus.databinding.ItemMateriaBinding
import com.danna.drobeus.model.HorarioItem
import com.danna.drobeus.model.MateriaRequest
import com.danna.drobeus.model.MateriaResumen
import com.danna.drobeus.model.MensajeResponse
import com.danna.drobeus.network.ApiClient
import com.google.gson.Gson
import kotlinx.coroutines.launch
import retrofit2.Response
import java.text.Normalizer
import java.util.Locale

class MateriasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMateriasBinding
    private lateinit var prefs: UserPrefs
    private var periodo = ""
    private var materias: List<MateriaResumen> = emptyList()
    private val paleta = Util.PALETA

    // Elegir la foto, PDF o Word del horario para que la app cree las materias sola
    private val selectorHorario = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            HorarioImport.importar(this, uri, periodo, materias.size) { guardado ->
                if (guardado) cargar()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMateriasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = UserPrefs(this)
        if (!prefs.isLogged()) {
            irALogin()
            return
        }

        periodo = prefs.semester()
        NavBar.configurar(this, R.id.navMaterias)

        binding.tvPeriodo.setOnClickListener { cambiarPeriodo() }
        binding.btnNuevaMateria.setOnClickListener { mostrarFormulario(null) }
        binding.btnCargarHorario.setOnClickListener {
            selectorHorario.launch(Archivos.TIPOS_HORARIO)
        }
    }

    override fun onResume() {
        super.onResume()
        if (prefs.isLogged()) cargar()
    }

    // ---------- Cargar y mostrar ----------
    private fun cargar() {
        binding.tvPeriodo.text = "Semestre $periodo ▾"
        binding.tvPildora.text = "Materias del Semestre $periodo"

        lifecycleScope.launch {
            try {
                val r = ApiClient.api.materias(periodo.ifEmpty { null })
                if (r.code() == 401) { irALogin(); return@launch }
                materias = r.body()?.materias ?: emptyList()
                pintar()
            } catch (e: Exception) {
                android.util.Log.e("DrobeUs", "Error al cargar materias", e)
                toast(Util.errorTexto(e))
            }
        }
    }

    private fun pintar() {
        binding.gridMaterias.removeAllViews()
        binding.tvVacio.visibility = if (materias.isEmpty()) View.VISIBLE else View.GONE

        materias.forEach { m ->
            val item = ItemMateriaBinding.inflate(layoutInflater, binding.gridMaterias, false)
            item.tvNombre.text = m.nombre

            val a = m.totalApuntes ?: 0
            val t = m.totalTareas ?: 0
            item.tvDetalle.text =
                "$a ${if (a == 1) "apunte" else "apuntes"} · $t ${if (t == 1) "tarea" else "tareas"}"

            try {
                item.dot.backgroundTintList =
                    ColorStateList.valueOf(Color.parseColor(m.color ?: "#FFFFFF"))
            } catch (e: Exception) { /* color inválido: se ignora */ }

            item.root.layoutParams = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply {
                width = 0
                setMargins(dp(5), dp(5), dp(5), dp(5))
            }

            // Toque: abre la materia (Apuntes, Trabajos, Fechas y Notas)
            item.root.setOnClickListener {
                startActivity(
                    Intent(this, DetalleMateriaActivity::class.java)
                        .putExtra("id", m.id)
                        .putExtra("nombre", m.nombre)
                        .putExtra("profesor", m.profesor)
                        .putExtra("aula", m.aula)
                        .putExtra("color", m.color)
                )
            }
            // ⋮ o pulsación larga: editar o eliminar
            item.tvOpciones.setOnClickListener { opciones(m) }
            item.root.setOnLongClickListener { opciones(m); true }

            binding.gridMaterias.addView(item.root)
        }
    }

    // ---------- Cambiar de periodo ----------
    private fun cambiarPeriodo() {
        val campo = EditText(this).apply {
            setText(periodo)
            hint = "Periodo (ej. 2026-2)"
            setPadding(dp(24), dp(16), dp(24), dp(16))
        }
        AlertDialog.Builder(this)
            .setTitle("Cambiar de periodo")
            .setView(campo)
            .setPositiveButton("Ver") { _, _ ->
                periodo = campo.text.toString().trim()
                cargar()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ---------- Editar y eliminar ----------
    private fun opciones(m: MateriaResumen) {
        AlertDialog.Builder(this)
            .setTitle(m.nombre)
            .setItems(arrayOf("Editar", "Eliminar")) { _, cual ->
                if (cual == 0) mostrarFormulario(m) else confirmarEliminar(m)
            }
            .show()
    }

    private fun confirmarEliminar(m: MateriaResumen) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar materia")
            .setMessage("Se eliminará ${m.nombre} junto con sus apuntes, archivos, tareas y notas.")
            .setPositiveButton("Eliminar") { _, _ ->
                ejecutar("Materia eliminada") { ApiClient.api.eliminarMateria(m.id) }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ---------- Formulario de crear / editar ----------
    private fun mostrarFormulario(existente: MateriaResumen?) {
        val f = DialogMateriaBinding.inflate(layoutInflater)
        var colorElegido = existente?.color ?: paleta[materias.size % paleta.size]

        if (existente != null) {
            f.etNombre.setText(existente.nombre)
            f.etProfesor.setText(existente.profesor ?: "")
            f.etAula.setText(existente.aula ?: "")

            // El primer horario va en los tres campos; los demás, en "otros días"
            val horarioActual = existente.horario.orEmpty()
            horarioActual.firstOrNull()?.let {
                f.etDia.setText(it.dia)
                f.etHoraInicio.setText(it.horaInicio)
                f.etHoraFin.setText(it.horaFin)
            }
            f.etHorarioExtra.setText(Util.horarioATexto(horarioActual.drop(1)))
        }
        construirSelectorColor(f.selectorColor, colorElegido) { colorElegido = it }

        val dialogo = AlertDialog.Builder(this)
            .setTitle(if (existente == null) "Nueva materia" else "Editar materia")
            .setView(f.root)
            .setPositiveButton("Guardar", null)
            .setNegativeButton("Cancelar", null)
            .create()

        dialogo.setOnShowListener {
            dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val nombre = f.etNombre.text.toString().trim()
                val profesor = f.etProfesor.text.toString().trim()
                val aula = f.etAula.text.toString().trim()

                if (nombre.isEmpty()) {
                    f.etNombre.error = "Escribe el nombre"
                    return@setOnClickListener
                }

                val horario = mutableListOf<HorarioItem>()
                val dia = f.etDia.text.toString().trim()
                val ini = f.etHoraInicio.text.toString().trim()
                val fin = f.etHoraFin.text.toString().trim()

                // El horario es opcional, pero si se llena debe estar completo
                if (dia.isNotEmpty() || ini.isNotEmpty() || fin.isNotEmpty()) {
                    val d = normalizarDia(dia)
                    val hi = formatoHora(ini)
                    val hf = formatoHora(fin)
                    if (d == null) { f.etDia.error = "Día inválido"; return@setOnClickListener }
                    if (hi == null) { f.etHoraInicio.error = "Usa el formato 07:00"; return@setOnClickListener }
                    if (hf == null) { f.etHoraFin.error = "Usa el formato 09:00"; return@setOnClickListener }
                    horario.add(HorarioItem(d, hi, hf))
                }

                // Otros días de la misma materia
                val extra = f.etHorarioExtra.text.toString().trim()
                if (extra.isNotEmpty()) {
                    val adicional = Util.textoAHorario(extra)
                    if (adicional == null) {
                        f.etHorarioExtra.error = "Usa: miercoles 07:00-09:00, viernes 09:00-11:00"
                        return@setOnClickListener
                    }
                    horario.addAll(adicional)
                }

                dialogo.dismiss()

                if (existente == null) {
                    ejecutar("Materia creada") {
                        ApiClient.api.crearMateria(
                            MateriaRequest(
                                nombre = nombre, profesor = profesor, aula = aula,
                                color = colorElegido, periodo = periodo.ifEmpty { null },
                                horario = horario.ifEmpty { null }
                            )
                        )
                    }
                } else {
                    ejecutar("Materia actualizada") {
                        ApiClient.api.editarMateria(
                            existente.id,
                            MateriaRequest(
                                nombre = nombre, profesor = profesor, aula = aula,
                                color = colorElegido, horario = horario
                            )
                        )
                    }
                }
            }
        }
        dialogo.show()
    }

    // Círculos de color: el elegido se ve grande y los demás atenuados
    private fun construirSelectorColor(
        contenedor: LinearLayout,
        inicial: String,
        alElegir: (String) -> Unit
    ) {
        contenedor.removeAllViews()
        val vistas = mutableListOf<Pair<String, View>>()

        fun resaltar(elegido: String) {
            vistas.forEach { (hex, v) ->
                val activo = hex.equals(elegido, ignoreCase = true)
                v.alpha = if (activo) 1f else 0.35f
                v.scaleX = if (activo) 1.25f else 1f
                v.scaleY = if (activo) 1.25f else 1f
            }
        }

        val opciones = if (paleta.any { it.equals(inicial, ignoreCase = true) }) paleta
        else paleta + inicial

        opciones.forEach { hex ->
            val v = View(this)
            v.layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply {
                setMargins(dp(5), dp(6), dp(5), dp(6))
            }
            v.setBackgroundResource(R.drawable.bg_dot)
            v.backgroundTintList = ColorStateList.valueOf(Util.color(hex))
            v.setOnClickListener {
                alElegir(hex)
                resaltar(hex)
            }
            contenedor.addView(v)
            vistas.add(hex to v)
        }
        resaltar(inicial)
    }

    // ---------- Utilidades ----------
    private fun ejecutar(exito: String, llamada: suspend () -> Response<MensajeResponse>) {
        lifecycleScope.launch {
            try {
                val r = llamada()
                if (r.code() == 401) {
                    irALogin(); return@launch
                }
                if (r.isSuccessful) {
                    toast(exito)
                    cargar()
                } else {
                    toast(mensajeDeError(r.errorBody()?.string()))
                }

            } catch (e: Exception) {
                android.util.Log.e("DrobeUs", "Error en la llamada", e)
                toast(Util.errorTexto(e))
            }
        }
    }

    private fun mensajeDeError(cuerpo: String?): String {
        val m = try {
            Gson().fromJson(cuerpo, MensajeResponse::class.java)?.mensaje
        } catch (e: Exception) {
            null
        }
        return m ?: "No se pudo completar la acción"
    }

    private fun normalizarDia(texto: String): String? {
        val limpio = Normalizer.normalize(texto.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace("\\p{M}".toRegex(), "")
        val validos = listOf("lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo")
        return if (limpio in validos) limpio else null
    }

    // Acepta 7:00 o 07:00 y devuelve siempre HH:mm
    private fun formatoHora(texto: String): String? {
        val m = Regex("^([01]?\\d|2[0-3]):([0-5]\\d)$").matchEntire(texto.trim()) ?: return null
        return "${m.groupValues[1].padStart(2, '0')}:${m.groupValues[2]}"
    }

    private fun irALogin() {
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    private fun dp(valor: Int) = (valor * resources.displayMetrics.density).toInt()

    private fun toast(texto: String) = Toast.makeText(this, texto, Toast.LENGTH_LONG).show()
}
