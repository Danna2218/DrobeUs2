package com.danna.drobeus

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.danna.drobeus.databinding.ActivityNotasBinding
import com.danna.drobeus.databinding.ItemNotaMateriaBinding
import com.danna.drobeus.network.ApiClient
import kotlinx.coroutines.launch

// Notas: promedio del periodo y promedio de cada materia
class NotasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotasBinding
    private lateinit var prefs: UserPrefs
    private var periodo = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = UserPrefs(this)
        if (!prefs.isLogged()) {
            irALogin()
            return
        }

        periodo = prefs.semester()
        NavBar.configurar(this, R.id.navNotas)
        binding.tvPeriodo.setOnClickListener { cambiarPeriodo() }
    }

    override fun onResume() {
        super.onResume()
        if (prefs.isLogged()) cargar()
    }

    private fun cargar() {
        binding.tvPeriodo.text = "Semestre $periodo ▾"

        lifecycleScope.launch {
            try {
                val r = ApiClient.api.promedio(periodo.ifEmpty { null })
                if (r.code() == 401) { irALogin(); return@launch }

                val cuerpo = r.body()
                binding.tvPromedio.text = Util.promedioTexto(cuerpo?.promedioPeriodo)

                val materias = cuerpo?.materias ?: emptyList()
                binding.listaNotas.removeAllViews()
                binding.tvVacio.visibility = if (materias.isEmpty()) View.VISIBLE else View.GONE

                materias.forEach { m ->
                    val item = ItemNotaMateriaBinding.inflate(layoutInflater, binding.listaNotas, false)
                    item.tvTitulo.text = m.materia
                    item.tvDetalle.text =
                        "${m.totalNotas} ${if (m.totalNotas == 1) "nota registrada" else "notas registradas"}"
                    item.tvValor.text = Util.promedioTexto(m.promedio)
                    item.dot.backgroundTintList = ColorStateList.valueOf(Util.color(m.color))

                    // Toque: abre la materia directamente en su pestaña de notas
                    item.root.setOnClickListener {
                        startActivity(
                            Intent(this@NotasActivity, DetalleMateriaActivity::class.java)
                                .putExtra("id", m.materiaId)
                                .putExtra("nombre", m.materia)
                                .putExtra("color", m.color)
                                .putExtra("pestana", "NOTAS")
                        )
                    }
                    binding.listaNotas.addView(item.root)
                }
            } catch (e: Exception) {
                Util.toast(this@NotasActivity, Util.errorTexto(e))
            }
        }
    }

    private fun cambiarPeriodo() {
        val campo = EditText(this).apply {
            setText(periodo)
            hint = "Periodo (ej. 2026-2)"
            val p = Util.dp(this@NotasActivity, 24)
            setPadding(p, p, p, p)
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

    private fun irALogin() {
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
