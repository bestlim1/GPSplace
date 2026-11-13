package com.example.gpsplace.data.remote

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

/** Firebase 접근 한곳. Firestore 데이터베이스는 기본값이 아니라 “gpsplace”다 (서울 리전). */
class FirebaseSource {
    val auth: FirebaseAuth = FirebaseAuth.getInstance()
    val db: FirebaseFirestore = FirebaseFirestore.getInstance(FirebaseApp.getInstance(), DATABASE_ID)

    val currentUid: String? get() = auth.currentUser?.uid

    /** 익명 로그인. 이미 로그인되어 있으면 그 uid를 쓴다. */
    suspend fun ensureSignedIn(): String =
        auth.currentUser?.uid ?: auth.signInAnonymously().await().user?.uid
            ?: error("익명 로그인 결과에 사용자가 없습니다")

    suspend fun fcmToken(): String? = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()

    fun user(uid: String): DocumentReference = db.collection("users").document(uid)
    fun code(code: String): DocumentReference = db.collection("codes").document(code)
    fun links(): CollectionReference = db.collection("links")
    fun link(linkId: String): DocumentReference = links().document(linkId)
    fun pointHours(linkId: String): CollectionReference = link(linkId).collection("pointHours")
    fun stays(linkId: String): CollectionReference = link(linkId).collection("stays")
    fun zones(linkId: String): CollectionReference = link(linkId).collection("zones")
    fun alerts(linkId: String): CollectionReference = link(linkId).collection("alerts")

    companion object {
        const val DATABASE_ID = "gpsplace"
    }
}
