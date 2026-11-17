package com.danna.drobeus

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.danna.drobeus.databinding.ActivityHomeBinding
import com.danna.drobeus.databinding.ItemCardBinding
import com.danna.drobeus.model.RefreshRequest
import com.danna.drobeus.network.ApiClient
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var prefs: UserPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = UserPrefs(this)

        // Sin sesión real: volver al login
        if (!prefs.isLogged()) {
            irALogin()
            return
        }

        binding.tvSaludo.text = "Hola, ${prefs.name().substringBefore(" ")}"
        val fecha = SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date())
        binding.tvFecha.text = "$fecha · Semestre ${prefs.semester()}"

        NavBar.configurar(this, R.id.navInicio)

        binding.tvPerfil.setOnClickListener {
            startActivity(Intent(this, PerfilActivity::class.java))
        }

        binding.tvCambiarCuenta.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Cambiar cuenta")
                .setMessage("¿Quieres cerrar la sesión de ${prefs.name()}?")
                .setPositiveButton("Sí, salir") { _, _ -> cerrarSesion() }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    // Se recarga cada vez que se vuelve al Inicio
    override fun onResume() {
        super.onResume()
        if (prefs.isLogged()) {
            // El nombre puede haber cambiado desde Mi perfil
            binding.tvSaludo.text = "Hola, ${prefs.name().substringBefore(" ")}"
            cargarDatos()
        }
    }

    private fun cargarDatos() {
        lifecycleScope.launch {
            try {
                // Clases de hoy
                val rClases = ApiClient.api.clasesDeHoy(diaDeHoy())
                if (rClases.code() == 401) { irALogin(); return@launch }

                binding.listaClases.removeAllViews()
                val clases = rClases.body()?.clases ?: emptyList()
                if (clases.isEmpty()) {
                    agregarItem(binding.listaClases, "Sin clases hoy", "Disfruta tu día")
                        .dot.visibility = View.GONE
                }
                clases.forEach {
                    val detalle = "${it.horaInicio} – ${it.horaFin} · ${it.aula ?: ""}"
                    val item = agregarItem(binding.listaClases, it.materia, detalle)
                    try {
                        item.dot.backgroundTintList =
                            ColorStateList.valueOf(Color.parseColor(it.color ?: "#FFFFFF"))
                    } catch (e: Exception) { /* color inválido: se ignora */ }
                }

                // Tareas por entregar
                val rTareas = ApiClient.api.tareasPendientes()
                if (rTareas.code() == 401) { irALogin(); return@launch }

                binding.listaEntregas.removeAllViews()
                val tareas = rTareas.body()?.pendientes ?: emptyList()
                if (tareas.isEmpty()) {
                    agregarItem(binding.listaEntregas, "Sin tareas pendientes", "Estás al día")
                        .dot.visibility = View.GONE
                }
                tareas.forEach {
                    val detalle = "${it.materia ?: ""} · ${it.falta ?: ""}"
                    agregarItem(binding.listaEntregas, it.titulo, detalle)
                        .dot.visibility = View.GONE
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@HomeActivity,
                    "No se pudieron cargar los datos. Revisa tu internet.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Cierra la sesión en el servidor (revoca el refresh token) y en el celular
    private fun cerrarSesion() {
        lifecycleScope.launch {
            try {
                ApiClient.api.logout(RefreshRequest(prefs.refreshToken()))
            } catch (e: Exception) { /* sin internet: igual se cierra localmente */ }
            prefs.logout()
            irALogin()
        }
    }

    private fun irALogin() {
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    private fun diaDeHoy(): String {
        return when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "lunes"
            Calendar.TUESDAY -> "martes"
            Calendar.WEDNESDAY -> "miercoles"
            Calendar.THURSDAY -> "jueves"
            Calendar.FRIDAY -> "viernes"
            Calendar.SATURDAY -> "sabado"
            else -> "domingo"
        }
    }

    private fun agregarItem(padre: LinearLayout, titulo: String, detalle: String): ItemCardBinding {
        val item = ItemCardBinding.inflate(layoutInflater, padre, false)
        item.tvTitulo.text = titulo
        item.tvDetalle.text = detalle
        padre.addView(item.root)
        return item
    }
}