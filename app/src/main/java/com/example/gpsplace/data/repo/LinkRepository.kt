package com.example.gpsplace.data.repo

import com.example.gpsplace.data.local.Role
import com.example.gpsplace.data.local.SettingsStore
import com.example.gpsplace.data.remote.FirebaseSource
import com.example.gpsplace.data.remote.RemoteLink
import com.example.gpsplace.data.remote.listen
import com.example.gpsplace.data.remote.millis
import com.example.gpsplace.data.remote.toRemoteLink
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date
import kotlin.random.Random

data class IssuedCode(val code: String, val expiresAt: Long)

/** 보호자 화면(S-02)이 보는 연결 코드 상태 */
sealed interface CodeState {
    data object Waiting : CodeState
    data class Linked(val linkId: String) : CodeState
    data object Denied : CodeState
    data object Expired : CodeState
    data object Cancelled : CodeState
}

enum class ClaimResult { Ok, Invalid, Offline }

sealed interface LinkState {
    data class Active(val link: RemoteLink, val fromCache: Boolean) : LinkState
    /** 해제되었거나 더 이상 볼 수 없음 (NFR-04) */
    data object Released : LinkState
    data class Failed(val error: Throwable) : LinkState
}

/**
 * 기기 연결 (FR-01). 코드 6자리·유효 10분, 보호대상자 동의로 links 문서를 만든다.
 * Firestore 규칙(firestore.rules)이 같은 조건을 서버에서 다시 검사한다.
 */
class LinkRepository(
    private val firebase: FirebaseSource,
    private val settings: SettingsStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** S-01: 역할을 기기에 저장하고 users/{uid}.role을 올린다. 네트워크가 없으면 서버 저장은 다음 단계로 미룬다. */
    suspend fun chooseRole(role: Role) {
        settings.setRole(role)
        runCatching { syncUserDoc(role) }
    }

    suspend fun syncUserDoc(role: Role? = null) {
        val uid = withTimeout(NETWORK_TIMEOUT_MS) { firebase.ensureSignedIn() }
        val data = buildMap<String, Any> {
            (role ?: settings.currentLink().role)?.let { put("role", it.key) }
            firebase.fcmToken()?.let { put("fcmToken", it) }
            put("createdAt", FieldValue.serverTimestamp())
        }
        val ref = firebase.user(uid)
        val existing = runCatching { ref.get(Source.CACHE).await() }.getOrNull()
        val payload = if (existing?.contains("createdAt") == true) data - "createdAt" else data
        ref.set(payload, SetOptions.merge())
    }

    suspend fun updateFcmToken(token: String) {
        val uid = firebase.currentUid ?: return
        firebase.user(uid).set(mapOf("fcmToken" to token), SetOptions.merge())
    }

    // ---- 보호자 ----

    /** 새 코드를 만든다. 이전 코드가 있으면 취소한다. */
    suspend fun issueCode(previous: String?): IssuedCode = withTimeout(NETWORK_TIMEOUT_MS) {
        val uid = firebase.ensureSignedIn()
        settings.setRole(Role.Guardian)
        if (previous != null) cancelCode(previous)
        repeat(MAX_CODE_TRIES) {
            val code = Random.nextInt(0, 1_000_000).toString().padStart(6, '0')
            val ref = firebase.code(code)
            val expiresAt = clock() + CODE_VALID_MS
            val created = firebase.db.runTransaction { tx ->
                val snapshot = tx.get(ref)
                if (snapshot.exists() && isLive(snapshot)) {
                    false
                } else {
                    tx.set(
                        ref,
                        mapOf(
                            "guardianUid" to uid,
                            "status" to "waiting",
                            "createdAt" to FieldValue.serverTimestamp(),
                            "expiresAt" to Timestamp(Date(expiresAt)),
                        )
                    )
                    true
                }
            }.await()
            if (created) {
                settings.setPendingCode(code, expiresAt)
                return@withTimeout IssuedCode(code, expiresAt)
            }
        }
        error("사용할 수 있는 코드를 찾지 못했습니다")
    }

    fun observeCode(code: String): Flow<CodeState> = firebase.code(code).listen().map { snapshot ->
        when (snapshot.getString("status")) {
            "consumed" -> snapshot.getString("linkId")?.let { CodeState.Linked(it) } ?: CodeState.Waiting
            "denied" -> CodeState.Denied
            "cancelled" -> CodeState.Cancelled
            else -> if (isExpired(snapshot)) CodeState.Expired else CodeState.Waiting
        }
    }

    /** 대기 중인 코드를 더 이상 못 쓰게 한다 (D-04 [요청 취소], [새 코드 만들기]). */
    suspend fun cancelCode(code: String) {
        val task = firebase.code(code).update("status", "cancelled")
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) { runCatching { task.await() } }
        if (settings.currentLink().pendingCode == code) settings.setPendingCode(null, 0)
    }

    /** 보호대상자가 동의해 연결이 완료됐을 때 보호자 쪽 저장 */
    suspend fun completeAsGuardian(linkId: String) {
        settings.setRole(Role.Guardian)
        settings.setLink(linkId, active = true)
        firebase.currentUid?.let { uid ->
            firebase.user(uid).set(mapOf("role" to Role.Guardian.key, "linkId" to linkId), SetOptions.merge())
        }
    }

    // ---- 보호대상자 ----

    /** S-13: 코드 확인. 맞으면 동의 대기(pending_consent)로 바꾼다. */
    suspend fun claimCode(code: String): ClaimResult = try {
        withTimeout(NETWORK_TIMEOUT_MS) {
            val uid = firebase.ensureSignedIn()
            val ref = firebase.code(code)
            firebase.db.runTransaction { tx ->
                val snapshot = tx.get(ref)
                when {
                    !snapshot.exists() || isExpired(snapshot) -> ClaimResult.Invalid
                    snapshot.getString("status") == "waiting" -> {
                        tx.update(ref, mapOf("status" to "pending_consent", "protectedUid" to uid))
                        ClaimResult.Ok
                    }
                    // 동의 화면에서 뒤로 왔다가 같은 코드를 다시 넣은 경우
                    snapshot.getString("status") == "pending_consent" && snapshot.getString("protectedUid") == uid ->
                        ClaimResult.Ok
                    else -> ClaimResult.Invalid
                }
            }.await()
        }
    } catch (e: FirebaseFirestoreException) {
        if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) ClaimResult.Offline else ClaimResult.Invalid
    } catch (e: Exception) {
        ClaimResult.Offline
    }

    /** S-14 [동의하고 연결]: links 문서를 만들고 코드를 consumed로 바꾼다. 반환 = linkId */
    suspend fun consent(code: String, locationGranted: Boolean, serviceOn: Boolean): String = withTimeout(NETWORK_TIMEOUT_MS) {
        val uid = firebase.ensureSignedIn()
        val codeRef = firebase.code(code)
        val codeSnapshot = codeRef.get(Source.SERVER).await()
        val guardianUid = codeSnapshot.getString("guardianUid") ?: error("코드가 없습니다")
        val linkRef = firebase.links().document()
        firebase.db.batch()
            .set(
                linkRef,
                mapOf(
                    "guardianUid" to guardianUid,
                    "protectedUid" to uid,
                    "status" to "active",
                    "codeId" to code,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "pendingCount" to 0,
                    "permission" to permissionMap(locationGranted, serviceOn),
                    "reportedAt" to FieldValue.serverTimestamp(),
                )
            )
            .update(codeRef, mapOf("status" to "consumed", "linkId" to linkRef.id))
            .set(firebase.user(uid), mapOf("role" to Role.Protected.key, "linkId" to linkRef.id), SetOptions.merge())
            .commit()
            .await()
        settings.setRole(Role.Protected)
        settings.setLink(linkRef.id, active = true)
        linkRef.id
    }

    /** S-14 [거부] */
    suspend fun deny(code: String) {
        val task = firebase.code(code).update("status", "denied")
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) { runCatching { task.await() } }
    }

    // ---- 공통 ----

    fun observeLink(linkId: String): Flow<LinkState> = firebase.link(linkId).listen()
        .map { snapshot ->
            val link = snapshot.toRemoteLink()
            when {
                link != null && link.active -> LinkState.Active(link, snapshot.metadata.isFromCache)
                link != null -> LinkState.Released
                // 문서가 없음: 캐시에 아직 없을 뿐일 수 있으니 서버 응답일 때만 해제로 본다
                !snapshot.metadata.isFromCache -> LinkState.Released
                else -> LinkState.Failed(IllegalStateException("연결 정보를 아직 받지 못했습니다"))
            }
        }
        .catch { error ->
            val denied = (error as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
            emit(if (denied) LinkState.Released else LinkState.Failed(error))
        }

    /** S-12 연결 해제 (D-01). 위치 정보가 남지 않도록 lastPoint도 지운다. 오프라인이면 연결되는 대로 전송된다. */
    suspend fun release(linkId: String) {
        val task = firebase.link(linkId).update(
            mapOf(
                "status" to "released",
                "releasedAt" to FieldValue.serverTimestamp(),
                "lastPoint" to FieldValue.delete(),
            )
        )
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) { runCatching { task.await() } }
        onReleased()
    }

    /** 해제를 알게 되었을 때 기기 쪽 정리. 역할은 다시 고를 수 있게 한다. */
    suspend fun onReleased() {
        settings.clearLink()
    }

    private fun isLive(snapshot: DocumentSnapshot): Boolean {
        val status = snapshot.getString("status")
        return (status == "waiting" || status == "pending_consent") && !isExpired(snapshot)
    }

    private fun isExpired(snapshot: DocumentSnapshot): Boolean {
        val createdAt = snapshot.getTimestamp("createdAt").millis() ?: return false
        return clock() > createdAt + CODE_VALID_MS
    }

    companion object {
        const val CODE_VALID_MS = 10 * 60_000L
        const val NETWORK_TIMEOUT_MS = 10_000L
        private const val MAX_CODE_TRIES = 5

        fun permissionMap(locationGranted: Boolean, serviceOn: Boolean) = mapOf(
            "location" to if (locationGranted) "granted" else "denied",
            "service" to if (serviceOn) "on" else "off",
        )
    }
}
