package com.danna.drobeus

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.danna.drobeus.databinding.ActivityPerfilBinding
import com.danna.drobeus.model.EliminarCuentaRequest
import com.danna.drobeus.model.PerfilRequest
import com.danna.drobeus.model.RefreshRequest
import com.danna.drobeus.network.ApiClient
import kotlinx.coroutines.launch

// Mi perfil: ver y editar mis datos, cerrar sesión o eliminar la cuenta
class PerfilActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPerfilBinding
    private lateinit var prefs: UserPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPerfilBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = UserPrefs(this)
        if (!prefs.isLogged()) {
            irALogin()
            return
        }

        // Primero lo guardado en el celular; luego se actualiza con lo del servidor
        binding.etNombre.setText(prefs.name())
        binding.etInstitucion.setText(prefs.school())
        binding.etCarrera.setText(prefs.career())
        binding.etPeriodo.setText(prefs.semester())

        binding.tvVolver.setOnClickListener { finish() }
        binding.btnGuardar.setOnClickListener { guardar() }
        binding.btnCerrarSesion.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Cerrar sesión")
                .setMessage("¿Quieres cerrar la sesión de ${prefs.name()}?")
                .setPositiveButton("Sí, salir") { _, _ -> cerrarSesion() }
                .setNegativeButton("Cancelar", null)
                .show()
        }
        binding.btnEliminarCuenta.setOnClickListener { pedirContrasena() }

        cargar()
    }

    private fun cargar() {
        lifecycleScope.launch {
            try {
                val r = ApiClient.api.miPerfil()
                if (r.code() == 401) { irALogin(); return@launch }
                val u = r.body()?.usuario ?: return@launch

                binding.tvCorreo.text = u.correo
                binding.etNombre.setText(u.nombre)
                binding.etInstitucion.setText(u.institucion ?: "")
                binding.etCarrera.setText(u.carrera ?: "")
                binding.etPeriodo.setText(u.periodoActual ?: "")
            } catch (e: Exception) {
                Util.toast(this@PerfilActivity, Util.errorTexto(e))
            }
        }
    }

    private fun guardar() {
        val nombre = binding.etNombre.text.toString().trim()
        val institucion = binding.etInstitucion.text.toString().trim()
        val carrera = binding.etCarrera.text.toString().trim()
        val periodo = binding.etPeriodo.text.toString().trim()

        if (nombre.isEmpty()) {
            binding.etNombre.error = "Escribe tu nombre"
            return
        }

        binding.btnGuardar.isEnabled = false
        binding.btnGuardar.text = "Guardando..."

        lifecycleScope.launch {
            try {
                val r = ApiClient.api.actualizarPerfil(
                    prefs.userId(),
                    PerfilRequest(
                        nombre = nombre,
                        institucion = institucion,
                        carrera = carrera,
                        periodoActual = periodo.ifEmpty { null }
                    )
                )
                if (r.isSuccessful) {
                    prefs.saveName(nombre)
                    prefs.saveProfile(institucion, carrera, periodo, null)
                    Util.toast(this@PerfilActivity, "Perfil actualizado")
                } else {
                    Util.toast(this@PerfilActivity, Util.mensajeDeError(r.errorBody()?.string()))
                }
            } catch (e: Exception) {
                Util.toast(this@PerfilActivity, Util.errorTexto(e))
            } finally {
                binding.btnGuardar.isEnabled = true
                binding.btnGuardar.text = "Guardar cambios"
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

    // Eliminar la cuenta pide la contraseña para confirmar
    private fun pedirContrasena() {
        val campo = EditText(this).apply {
            hint = "Tu contraseña"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            val p = Util.dp(this@PerfilActivity, 24)
            setPadding(p, p, p, p)
        }
        AlertDialog.Builder(this)
            .setTitle("Eliminar mi cuenta")
            .setMessage("Se borrarán tu cuenta y todos tus datos (materias, apuntes, archivos, tareas y notas). No se puede deshacer.")
            .setView(campo)
            .setPositiveButton("Eliminar") { _, _ ->
                eliminarCuenta(campo.text.toString())
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun eliminarCuenta(contrasena: String) {
        if (contrasena.isEmpty()) {
            Util.toast(this, "Escribe tu contraseña para confirmar")
            return
        }
        lifecycleScope.launch {
            try {
                val r = ApiClient.api.eliminarCuenta(EliminarCuentaRequest(contrasena))
                if (r.isSuccessful) {
                    Util.toast(this@PerfilActivity, "Cuenta eliminada")
                    prefs.logout()
                    irALogin()
                } else {
                    Util.toast(this@PerfilActivity, Util.mensajeDeError(r.errorBody()?.string()))
                }
            } catch (e: Exception) {
                Util.toast(this@PerfilActivity, Util.errorTexto(e))
            }
        }
    }

    private fun irALogin() {
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
