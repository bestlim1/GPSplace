package com.example.gpsplace.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class Role(val key: String) {
    Guardian("guardian"),
    Protected("protected");

    companion object {
        fun fromKey(key: String?): Role? = entries.firstOrNull { it.key == key }
    }
}

/** 연결 상태를 오프라인에서도 알 수 있도록 기기에 남겨 두는 값 */
data class LocalLink(
    val role: Role? = null,
    val linkId: String? = null,
    val linkActive: Boolean = false,
    /** 보호자: 대기 중인 연결 코드 */
    val pendingCode: String? = null,
    val pendingCodeExpiresAt: Long = 0,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

/** 설정값 (DataStore). 위치·체류 기록은 Room에 둔다. */
class SettingsStore(private val context: Context) {
    private object Keys {
        val role = stringPreferencesKey("role")
        val linkId = stringPreferencesKey("linkId")
        val linkActive = booleanPreferencesKey("linkActive")
        val pendingCode = stringPreferencesKey("pendingCode")
        val pendingCodeExpiresAt = longPreferencesKey("pendingCodeExpiresAt")
        val storageFull = booleanPreferencesKey("storageFull")
        val lastUploadAt = longPreferencesKey("lastUploadAt")
        val lastUploadError = stringPreferencesKey("lastUploadError")
    }

    val link: Flow<LocalLink> = context.dataStore.data.map { it.toLocalLink() }

    suspend fun currentLink(): LocalLink = link.first()

    private fun Preferences.toLocalLink() = LocalLink(
        role = Role.fromKey(this[Keys.role]),
        linkId = this[Keys.linkId],
        linkActive = this[Keys.linkActive] ?: false,
        pendingCode = this[Keys.pendingCode],
        pendingCodeExpiresAt = this[Keys.pendingCodeExpiresAt] ?: 0,
    )

    suspend fun setRole(role: Role) = context.dataStore.edit { it[Keys.role] = role.key }

    suspend fun setLink(linkId: String, active: Boolean) = context.dataStore.edit {
        it[Keys.linkId] = linkId
        it[Keys.linkActive] = active
        it.remove(Keys.pendingCode)
        it.remove(Keys.pendingCodeExpiresAt)
    }

    suspend fun setLinkActive(active: Boolean) = context.dataStore.edit { it[Keys.linkActive] = active }

    suspend fun setPendingCode(code: String?, expiresAt: Long) = context.dataStore.edit {
        if (code == null) {
            it.remove(Keys.pendingCode)
            it.remove(Keys.pendingCodeExpiresAt)
        } else {
            it[Keys.pendingCode] = code
            it[Keys.pendingCodeExpiresAt] = expiresAt
        }
    }

    /** 연결 해제 뒤 역할을 다시 고를 수 있게 비운다. */
    suspend fun clearLink() = context.dataStore.edit {
        it.remove(Keys.linkId)
        it[Keys.linkActive] = false
        it.remove(Keys.pendingCode)
        it.remove(Keys.pendingCodeExpiresAt)
    }

    suspend fun clearRole() = context.dataStore.edit { it.remove(Keys.role) }

    val storageFull: Flow<Boolean> = context.dataStore.data.map { it[Keys.storageFull] ?: false }

    suspend fun setStorageFull(full: Boolean) = context.dataStore.edit { it[Keys.storageFull] = full }

    val lastUploadAt: Flow<Long?> = context.dataStore.data.map { it[Keys.lastUploadAt] }
    val lastUploadError: Flow<String?> = context.dataStore.data.map { it[Keys.lastUploadError] }

    suspend fun setUploadResult(at: Long?, error: String?) = context.dataStore.edit {
        if (at != null) it[Keys.lastUploadAt] = at
        if (error == null) it.remove(Keys.lastUploadError) else it[Keys.lastUploadError] = error
    }
}
