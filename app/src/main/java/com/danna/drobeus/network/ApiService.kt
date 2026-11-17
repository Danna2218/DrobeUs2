package com.danna.drobeus.network

import com.danna.drobeus.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ----- Auth -----
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("api/auth/registro")
    suspend fun registro(@Body request: RegistroRequest): Response<AuthResponse>

    @POST("api/auth/logout")
    suspend fun logout(@Body request: RefreshRequest): Response<MensajeResponse>

    @PUT("api/auth/perfil/{id}")
    suspend fun actualizarPerfil(
        @Path("id") id: String,
        @Body request: PerfilRequest
    ): Response<PerfilResponse>

    @GET("api/auth/yo")
    suspend fun miPerfil(): Response<PerfilResponse>

    // DELETE con cuerpo: pide la contraseña para confirmar
    @HTTP(method = "DELETE", path = "api/auth/cuenta", hasBody = true)
    suspend fun eliminarCuenta(@Body request: EliminarCuentaRequest): Response<MensajeResponse>

    // ----- Inicio -----
    @GET("api/materias/hoy")
    suspend fun clasesDeHoy(@Query("dia") dia: String): Response<ClasesResponse>

    @GET("api/tareas/pendientes")
    suspend fun tareasPendientes(): Response<PendientesResponse>

    // ----- Materias -----
    @GET("api/materias")
    suspend fun materias(@Query("periodo") periodo: String?): Response<MateriasResponse>

    @POST("api/materias")
    suspend fun crearMateria(@Body request: MateriaRequest): Response<MensajeResponse>

    @PUT("api/materias/{id}")
    suspend fun editarMateria(
        @Path("id") id: String,
        @Body request: MateriaRequest
    ): Response<MensajeResponse>

    @DELETE("api/materias/{id}")
    suspend fun eliminarMateria(@Path("id") id: String): Response<MensajeResponse>

    // ----- Apuntes -----
    @GET("api/apuntes")
    suspend fun apuntes(
        @Query("materia") materia: String,
        @Query("buscar") buscar: String? = null
    ): Response<ApuntesResponse>

    @POST("api/apuntes")
    suspend fun crearApunte(@Body request: ApunteRequest): Response<MensajeResponse>

    @PUT("api/apuntes/{id}")
    suspend fun editarApunte(
        @Path("id") id: String,
        @Body request: ApunteRequest
    ): Response<MensajeResponse>

    @DELETE("api/apuntes/{id}")
    suspend fun eliminarApunte(@Path("id") id: String): Response<MensajeResponse>

    // ----- Tareas -----
    @GET("api/tareas")
    suspend fun tareas(
        @Query("materia") materia: String,
        @Query("estado") estado: String? = null
    ): Response<TareasResponse>

    @POST("api/tareas")
    suspend fun crearTarea(@Body request: TareaRequest): Response<MensajeResponse>

    @PUT("api/tareas/{id}")
    suspend fun editarTarea(
        @Path("id") id: String,
        @Body request: TareaRequest
    ): Response<MensajeResponse>

    @PATCH("api/tareas/{id}/entregar")
    suspend fun entregarTarea(@Path("id") id: String): Response<MensajeResponse>

    @DELETE("api/tareas/{id}")
    suspend fun eliminarTarea(@Path("id") id: String): Response<MensajeResponse>

    // ----- Notas -----
    @GET("api/notas")
    suspend fun notas(@Query("materia") materia: String): Response<NotasResponse>

    @POST("api/notas")
    suspend fun crearNota(@Body request: NotaRequest): Response<MensajeResponse>

    @PUT("api/notas/{id}")
    suspend fun editarNota(
        @Path("id") id: String,
        @Body request: NotaRequest
    ): Response<MensajeResponse>

    @DELETE("api/notas/{id}")
    suspend fun eliminarNota(@Path("id") id: String): Response<MensajeResponse>

    @GET("api/notas/promedio")
    suspend fun promedio(@Query("periodo") periodo: String?): Response<PromedioResponse>

    // ----- Agenda -----
    @GET("api/agenda/semana")
    suspend fun semana(@Query("fecha") fecha: String): Response<SemanaResponse>

    @GET("api/agenda/recordatorios")
    suspend fun recordatorios(@Query("dias") dias: Int): Response<RecordatoriosResponse>

    // ----- Horario desde archivo -----
    @Multipart
    @POST("api/horario/analizar")
    suspend fun analizarHorario(@Part archivo: MultipartBody.Part): Response<AnalisisResponse>

    @POST("api/materias/lote")
    suspend fun crearMateriasLote(@Body request: LoteRequest): Response<MensajeResponse>

    // ----- Archivos de una materia -----
    @Multipart
    @POST("api/archivos")
    suspend fun subirArchivo(
        @Part archivo: MultipartBody.Part,
        @Part("materia") materia: RequestBody,
        @Part("nombre") nombre: RequestBody
    ): Response<MensajeResponse>

    @GET("api/archivos")
    suspend fun archivos(@Query("materia") materia: String): Response<ArchivosResponse>

    @Streaming
    @GET("api/archivos/{id}/descargar")
    suspend fun descargarArchivo(@Path("id") id: String): Response<ResponseBody>

    @DELETE("api/archivos/{id}")
    suspend fun eliminarArchivo(@Path("id") id: String): Response<MensajeResponse>
}