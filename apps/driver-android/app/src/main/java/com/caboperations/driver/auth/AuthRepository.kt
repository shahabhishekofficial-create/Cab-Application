package com.caboperations.driver.auth

import android.content.Context
import android.util.Base64
import com.caboperations.driver.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

@Serializable
data class AuthSession(val accessToken: String, val refreshToken: String, val expiresAt: Long? = null)

@Serializable
data class LoginRequest(val email: String, val password: String)

class AuthRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("supabase_auth", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun session(): AuthSession? = prefs.getString("session", null)?.let {
        runCatching { json.decodeFromString<AuthSession>(decrypt(it)) }.getOrNull()
    }

    fun login(email: String, password: String): Result<AuthSession> = runCatching {
        require(BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()) { "SUPABASE_NOT_CONFIGURED" }
        val body = "{\"email\":${quote(email)},\"password\":${quote(password)}}"
        val connection = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/auth/v1/token?grant_type=password").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 10_000; readTimeout = 15_000; doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
        }
        val response = try {
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error(parseError(text, "LOGIN_FAILED"))
            text
        } finally { connection.disconnect() }
        val obj = Json.parseToJsonElement(response).jsonObject
        val access = obj["access_token"]?.jsonPrimitive?.content ?: error("LOGIN_FAILED")
        val refresh = obj["refresh_token"]?.jsonPrimitive?.content ?: error("LOGIN_FAILED")
        val expires = obj["expires_in"]?.jsonPrimitive?.longOrNull?.let { System.currentTimeMillis() + it * 1000L }
        val result = AuthSession(access, refresh, expires)
        prefs.edit().putString("session", encrypt(json.encodeToString(result))).apply()
        result
    }

    fun logout() { prefs.edit().remove("session").apply() }

    private fun quote(value: String) = Json.encodeToString(value)
    private fun parseError(text: String, fallback: String): String = runCatching { Json.parseToJsonElement(text).jsonObject["msg"]?.jsonPrimitive?.content ?: fallback }.getOrDefault(fallback)

    private fun key(): SecretKey {
        val alias = "cab-auth-key"
        val ks = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val all = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all.copyOfRange(0, 12))) }
        return String(cipher.doFinal(all.copyOfRange(12, all.size)), StandardCharsets.UTF_8)
    }
}
