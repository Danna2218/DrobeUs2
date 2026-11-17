package com.danna.drobeus

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.danna.drobeus.databinding.ActivityRegisterBinding
import com.danna.drobeus.model.AuthResponse
import com.danna.drobeus.model.RegistroRequest
import com.danna.drobeus.network.ApiClient
import com.google.gson.Gson
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var prefs: UserPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = UserPrefs(this)

        binding.tvIrLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        binding.btnRegistrar.setOnClickListener {
            val nombre = binding.etNombre.text.toString().trim()
            val correo = binding.etCorreo.text.toString().trim()
            val clave = binding.etClave.text.toString()

            when {
                nombre.isEmpty() -> binding.etNombre.error = "Escribe tu nombre"
                !Patterns.EMAIL_ADDRESS.matcher(correo).matches() ->
                    binding.etCorreo.error = "Correo no válido"
                clave.length < 6 -> binding.etClave.error = "Mínimo 6 caracteres"
                else -> registrar(nombre, correo, clave)
            }
        }
    }

    private fun registrar(nombre: String, correo: String, clave: String) {
        binding.btnRegistrar.isEnabled = false
        binding.btnRegistrar.text = "Creando cuenta..."

        lifecycleScope.launch {
            try {
                val respuesta = ApiClient.api.registro(RegistroRequest(nombre, correo, clave))
                val cuerpo = respuesta.body()

                if (respuesta.isSuccessful && cuerpo?.usuario != null &&
                    cuerpo.accessToken != null && cuerpo.refreshToken != null
                ) {
                    val u = cuerpo.usuario
                    // onboarded = false: falta completar la pantalla "Empecemos"
                    prefs.saveSession(
                        u.id, u.nombre, u.correo, u.periodoActual ?: "",
                        cuerpo.accessToken, cuerpo.refreshToken, false
                    )
                    Toast.makeText(this@RegisterActivity, "Cuenta creada", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@RegisterActivity, OnboardingActivity::class.java))
                    finish()
                } else {
                    val mensaje = try {
                        Gson().fromJson(
                            respuesta.errorBody()?.string(),
                            AuthResponse::class.java
                        )?.mensaje
                    } catch (e: Exception) {
                        null
                    }
                    Toast.makeText(
                        this@RegisterActivity,
                        mensaje ?: "No se pudo crear la cuenta",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@RegisterActivity,
                    "No se pudo conectar con el servidor. Revisa tu internet.",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                binding.btnRegistrar.isEnabled = true
                binding.btnRegistrar.text = "Registrarme"
            }
        }
    }
}