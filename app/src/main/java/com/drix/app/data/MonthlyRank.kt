package com.drix.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pedidos aceptados por Drix en el MES, por cliente. Se suman en Firestore (monthly/{año-mes}_{uid}) para que el
 * administrador vea el top de usuarios. La escritura es "dispara y olvida": si no hay internet, Firestore la guarda
 * y la envía al volver la conexión.
 */
object MonthlyRank {
    fun monthKey(ms: Long = System.currentTimeMillis()): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(ms))

    fun onAccepted() {
        val u = FirebaseAuth.getInstance().currentUser ?: return
        val key = monthKey()
        runCatching {
            FirebaseFirestore.getInstance().collection("monthly").document("${key}_${u.uid}").set(
                mapOf(
                    "month" to key, "uid" to u.uid, "email" to u.email.orEmpty().trim().lowercase(),
                    "name" to DrixPrefs.name().take(80), "orders" to FieldValue.increment(1), "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
        }
    }
}
