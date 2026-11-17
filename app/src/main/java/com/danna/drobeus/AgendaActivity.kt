package com.danna.drobeus

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.danna.drobeus.databinding.ActivityAgendaBinding
import com.danna.drobeus.databinding.ItemCardBinding
import com.danna.drobeus.model.DiaSemana
import com.danna.drobeus.model.EntregaSemana
import com.danna.drobeus.network.ApiClient
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Agenda: la semana de lunes a domingo con entregas, parciales y recordatorios de estudio
class AgendaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAgendaBinding
    private lateinit var prefs: UserPrefs
    private val formato = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val letras = listOf("L", "M", "X", "J", "V", "S", "D")

    private var base: Calendar = Calendar.getInstance()
    private var dias: List<DiaSemana> = emptyList()
    private var entregas: List<EntregaSemana> = emptyList()
    private var diaElegido: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAgendaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = UserPrefs(this)
        if (!prefs.isLogged()) {
            irALogin()
            return
        }

        NavBar.configurar(this, R.id.navAgenda)

        binding.tvAnterior.setOnClickListener { moverSemana(-7) }
        binding.tvSiguiente.setOnClickListener { moverSemana(7) }
        binding.tvHoy.setOnClickListener {
            base = Calendar.getInstance()
            diaElegido = null
            cargar()
        }
    }

    override fun onResume() {
        super.onResume()
        if (prefs.isLogged()) cargar()
    }

    private fun moverSemana(delta: Int) {
        base.add(Calendar.DAY_OF_YEAR, delta)
        diaElegido = null
        cargar()
    }

    private fun cargar() {
        lifecycleScope.launch {
            try {
                val r = ApiClient.api.semana(formato.format(base.time))
                if (r.code() == 401) { irALogin(); return@launch }

                val cuerpo = r.body()
                dias = cuerpo?.dias ?: emptyList()
                entregas = cuerpo?.entregas ?: emptyList()
                binding.tvSemana.text = "Semana " + Util.rangoSemana(cuerpo?.desde, cuerpo?.hasta)
                pintarDias()
                pintarEntregas()

                // Recordatorios de estudio para lo que vence en los próximos días
                val rr = ApiClient.api.recordatorios(3)
                binding.listaRecordatorios.removeAllViews()
                val recordatorios = rr.body()?.recordatorios ?: emptyList()
                if (recordatorios.isEmpty()) {
                    agregarItem(binding.listaRecordatorios, "Sin recordatorios por ahora", null)
                }
                recordatorios.forEach { agregarItem(binding.listaRecordatorios, "🔔 ${it.mensaje}", null) }
            } catch (e: Exception) {
                Util.toast(this@AgendaActivity, Util.errorTexto(e))
            }
        }
    }


    private fun pintarDias() {
        binding.diasSemana.removeAllViews()
        val hoy = formato.format(Date())

        dias.forEachIndexed { i, d ->
            val vista = TextView(this)
            vista.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(Util.dp(this@AgendaActivity, 2), 0, Util.dp(this@AgendaActivity, 2), 0)
            }
            vista.text = "${letras.getOrElse(i) { "" }}\n${d.numero}"
            vista.gravity = Gravity.CENTER
            vista.typeface = Typeface.SERIF
            vista.setTextColor(android.graphics.Color.WHITE)
            vista.textSize = 12f
            val p = Util.dp(this@AgendaActivity, 8)
            vista.setPadding(0, p, 0, p)

            val esHoy = d.fecha == hoy
            val elegido = d.fecha == diaElegido
            if (esHoy || elegido) vista.setBackgroundResource(R.drawable.bg_field)
            vista.setTypeface(Typeface.SERIF, if (esHoy || elegido) Typeface.BOLD else Typeface.NORMAL)
            vista.alpha = if (diaElegido == null || elegido) 1f else 0.6f

            vista.setOnClickListener {
                diaElegido = if (elegido) null else d.fecha
                pintarDias()
                pintarEntregas()
            }
            binding.diasSemana.addView(vista)
        }
    }

    private fun pintarEntregas() {
        binding.listaEntregas.removeAllViews()
        val filtradas = entregas.filter { diaElegido == null || it.fechaEntrega?.take(10) == diaElegido }

        filtradas.forEach { e ->
            val numero = e.fechaEntrega?.take(10)?.substringAfterLast('-')?.toIntOrNull()
            val dia = Util.capitalizar(e.dia).take(3)
            val detalle = listOfNotNull(
                if (numero != null) "$dia $numero" else null,
                e.materia
            ).joinToString(" · ")
            val item = agregarItem(binding.listaEntregas, e.titulo, detalle)
            item.dot.visibility = View.VISIBLE
            item.dot.backgroundTintList = ColorStateList.valueOf(Util.color(e.color))
        }

        binding.tvVacio.text =
            if (diaElegido == null) "No tienes entregas esta semana." else "No tienes entregas ese día."
        binding.tvVacio.visibility = if (filtradas.isEmpty()) View.VISIBLE else View.GONE
    }


    private fun agregarItem(padre: LinearLayout, titulo: String, detalle: String?): ItemCardBinding {
        val item = ItemCardBinding.inflate(layoutInflater, padre, false)
        item.tvTitulo.text = titulo
        if (detalle == null) {
            item.tvDetalle.visibility = View.GONE
            item.dot.visibility = View.GONE
        } else {
            item.tvDetalle.text = detalle
        }
        padre.addView(item.root)
        return item
    }

    private fun irALogin() {
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
