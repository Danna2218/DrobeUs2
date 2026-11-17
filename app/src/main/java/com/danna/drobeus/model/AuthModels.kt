package com.danna.drobeus.model

data class Usuario(
    val id: String,
    val nombre: String,
    val correo: String,
    val institucion: String? = null,
    val carrera: String? = null,
    val periodoActual: String? = null
)

data class LoginRequest(val correo: String, val contrasena: String)

data class RegistroRequest(
    val nombre: String,
    val correo: String,
    val contrasena: String,
    val institucion: String? = null,
    val carrera: String? = null
)

data class AuthResponse(
    val ok: Boolean,
    val mensaje: String?,
    val accessToken: String?,
    val refreshToken: String?,
    val usuario: Usuario?
)

data class RefreshRequest(val refreshToken: String)

data class RefreshResponse(
    val ok: Boolean,
    val accessToken: String?,
    val refreshToken: String?
)

data class PerfilRequest(
    val nombre: String? = null,
    val institucion: String? = null,
    val carrera: String? = null,
    val periodoActual: String? = null
)

data class PerfilResponse(val ok: Boolean, val mensaje: String?, val usuario: Usuario?)

data class MensajeResponse(val ok: Boolean, val mensaje: String?)

data class EliminarCuentaRequest(val contrasena: String)