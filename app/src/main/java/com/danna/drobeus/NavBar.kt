package com.danna.drobeus

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.widget.TextView
import android.widget.Toast
import kotlin.jvm.java
import kotlin.to


object NavBar {

    // Marca en negrita la pestaña actual y hace que las demás naveguen
    fun configurar(activity: Activity, actual: Int) {
        val destinos: Map<Int, Class<out Activity>> = mapOf(
            R.id.navInicio to HomeActivity::class.java,
            R.id.navMaterias to MateriasActivity::class.java,
            R.id.navAgenda to AgendaActivity::class.java,
            R.id.navNotas to NotasActivity::class.java
        )

        listOf(R.id.navInicio, R.id.navMaterias, R.id.navAgenda, R.id.navNotas).forEach { id ->
            val vista = activity.findViewById<TextView>(id) ?: return@forEach
            vista.setTypeface(null, if (id == actual) Typeface.BOLD else Typeface.NORMAL)
            vista.setOnClickListener {
                val destino = destinos[id]
                when {
                    id == actual -> Unit
                    destino == null ->
                        Toast.makeText(activity, "Disponible muy pronto", Toast.LENGTH_SHORT).show()
                    else -> {
                        val intent = Intent(activity, destino)
                        if (id == R.id.navInicio) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        activity.startActivity(intent)
                        if (id != R.id.navInicio && actual != R.id.navInicio) activity.finish()
                    }
                }
            }
        }
    }
}