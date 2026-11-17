package com.danna.drobeus

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.danna.drobeus.databinding.ActivityOnboardingBinding
import com.danna.drobeus.model.PerfilRequest
import com.danna.drobeus.network.ApiClient
import kotlinx.coroutines.launch

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var prefs: UserPrefs
    private var horarioUri: Uri? = null

    private val selector = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            horarioUri = uri
            binding.btnHorario.text = "Horario cargado ✓"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = UserPrefs(this)

        binding.btnHorario.setOnClickListener {
            selector.launch(Archivos.TIPOS_HORARIO)
        }

        binding.btnIniciar.setOnClickListener {
            val uni = binding.etUniversidad.text.toString().trim()
            val carrera = binding.etCarrera.text.toString().trim()
            val periodo = binding.etSemestre.text.toString().trim()

            if (uni.isEmpty() || carrera.isEmpty() || periodo.isEmpty()) {
                Toast.makeText(this, "Completa todos los datos", Toast.LENGTH_SHORT).show()
            } else {
                guardarPerfil(uni, carrera, periodo)
            }
        }
    }

    private fun guardarPerfil(uni: String, carrera: String, periodo: String) {
        binding.btnIniciar.isEnabled = false
        binding.btnIniciar.text = "Guardando..."

        lifecycleScope.launch {
            try {
                val respuesta = ApiClient.api.actualizarPerfil(
                    prefs.userId(),
                    PerfilRequest(institucion = uni, carrera = carrera, periodoActual = periodo)
                )
                if (respuesta.isSuccessful) {
                    prefs.saveProfile(uni, carrera, periodo, horarioUri?.toString())

                    val uri = horarioUri
                    if (uri != null) {
                        // La app lee el horario, crea las materias y luego entra al Inicio
                        HorarioImport.importar(this@OnboardingActivity, uri, periodo, 0) {
                            irAlInicio()
                        }
                    } else {
                        irAlInicio()
                    }
                } else {
                    Toast.makeText(
                        this@OnboardingActivity,
                        "No se pudo guardar tu perfil. Intenta de nuevo.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@OnboardingActivity,
                    "No se pudo conectar con el servidor. Revisa tu internet.",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                binding.btnIniciar.isEnabled = true
                binding.btnIniciar.text = "Iniciar"
            }
        }
    }

    private fun irAlInicio() {
        startActivity(
            Intent(this, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}