package com.cortex.assistant

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurePrefs(context: Context) {
    private val prefs by lazy {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "cortex_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getApiKey(): String = try { prefs.getString("gemini_key", "").orEmpty() } catch (_: Exception) { "" }
    fun saveApiKey(value: String) { runCatching { prefs.edit().putString("gemini_key", value.trim()).apply() } }
    fun getName(): String = try { prefs.getString("user_name", "").orEmpty() } catch (_: Exception) { "" }
    fun saveName(value: String) { runCatching { prefs.edit().putString("user_name", value.trim()).apply() } }
    fun getString(key: String, default: String): String = try { prefs.getString(key, default).orEmpty() } catch (_: Exception) { default }
    fun putString(key: String, value: String) { runCatching { prefs.edit().putString(key, value).apply() } }
    fun getBoolean(key: String, default: Boolean): Boolean = try { prefs.getBoolean(key, default) } catch (_: Exception) { default }
    fun putBoolean(key: String, value: Boolean) { runCatching { prefs.edit().putBoolean(key, value).apply() } }
    fun forgetAll() { runCatching { prefs.edit().clear().apply() } }
}
