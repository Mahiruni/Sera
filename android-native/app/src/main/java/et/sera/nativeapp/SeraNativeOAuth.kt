package et.sera.nativeapp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import org.json.JSONObject

private const val CALLBACK = "sera://auth/callback"
private const val OAUTH_PREFS = "sera_native_oauth_secure_v2"
private val oauthLock = Any()
private fun oauthPrefs(context: Context): SharedPreferences = EncryptedSharedPreferences.create(
 context, OAUTH_PREFS, MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
 EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
 EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
internal fun isSeraOAuthCallback(uri:Uri?):Boolean = uri?.scheme=="sera" && uri.host=="auth" && uri.path=="/callback" && uri.userInfo==null && uri.port==-1
internal fun cancelNativeOAuth(context:Context) { synchronized(oauthLock){runCatching{oauthPrefs(context).edit().clear().commit()}} }
internal fun nativeOAuthPending(context:Context):Boolean = runCatching { val p=oauthPrefs(context); p.contains("verifier") && System.currentTimeMillis()-p.getLong("created",0L) in 0..600000L }.getOrDefault(false)

internal fun beginNativeOAuth(context: Context, provider: String): String {
    if (provider !in setOf("google", "linkedin_oidc"))
        return "Unsupported identity provider."
    if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank())
        return "Authentication is not configured."
    val random = ByteArray(32).also { SecureRandom().nextBytes(it) }
    val verifier = Base64.encodeToString(random, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    val challenge = Base64.encodeToString(
        MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    )
    val state = Base64.encodeToString(ByteArray(24).also { SecureRandom().nextBytes(it) },
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    val stored = runCatching { synchronized(oauthLock) {
        oauthPrefs(context).edit().clear().putString("verifier", verifier).putString("state", state)
            .putString("provider", provider).putLong("created", System.currentTimeMillis()).commit()
    } }.getOrDefault(false)
    if (!stored) return "Could not prepare secure sign-in. Please retry."
    val url = Uri.parse(BuildConfig.SUPABASE_URL.trimEnd('/') + "/auth/v1/authorize")
        .buildUpon().appendQueryParameter("provider", provider)
        .appendQueryParameter("redirect_to", "$CALLBACK?state=$state")
        .appendQueryParameter("code_challenge", challenge)
        .appendQueryParameter("code_challenge_method", "s256")
        .build()
    return try {
        // Use a standard secure Custom Tab. Partial-height windows can break provider consent layouts.
        val tab = CustomTabsIntent.Builder()
            .setToolbarColor(Color.rgb(8, 126, 131))
            .setShowTitle(true)
            .setUrlBarHidingEnabled(false)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .build()
        tab.launchUrl(context, url)
        ""
    } catch (_: Exception) {
        cancelNativeOAuth(context)
        "Could not open the secure sign-in page."
    }
}

internal fun finishNativeOAuth(context: Context, uri: Uri): Pair<Pair<String, String>?, String> {
    if (!isSeraOAuthCallback(uri)) return null to "Invalid sign-in callback."
    val returnedState = uri.getQueryParameter("state")
    val verifier = try { synchronized(oauthLock) {
        val prefs = oauthPrefs(context)
        val expected = prefs.getString("state", null)
        val value = prefs.getString("verifier", null)
        val created = prefs.getLong("created",0L)
        if (expected.isNullOrBlank() || value.isNullOrBlank() || System.currentTimeMillis()-created !in 0..600000L)
            return null to "Sign-in session expired. Please retry."
        // Reject an unrelated callback without destroying a legitimate pending attempt.
        if (returnedState == null || !MessageDigest.isEqual(returnedState.toByteArray(),expected.toByteArray()))
            return null to "Sign-in verification failed. Please retry."
        if (!prefs.edit().clear().commit()) return null to "Unable to secure sign-in. Please retry."
        value
    } } catch (_:Exception) { return null to "Unable to recover secure sign-in. Please retry." }
    if (!uri.getQueryParameter("error").isNullOrBlank() || !uri.getQueryParameter("error_description").isNullOrBlank())
        return null to "Sign-in was cancelled or declined. Please try again."
    val code = uri.getQueryParameter("code")
    if (code.isNullOrBlank() || code.length>4096) return null to "No valid authorization code was returned."
    var connection: HttpURLConnection? = null
    return try {
        connection = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') +
            "/auth/v1/token?grant_type=pkce").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 12000
            readTimeout = 16000
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Content-Type", "application/json")
        }
        connection.outputStream.use {
            it.write(JSONObject().put("auth_code", code).put("code_verifier", verifier)
                .toString().toByteArray())
        }
        if (connection.responseCode !in 200..299)
            return null to "Identity provider sign-in could not be completed. Please retry."
        val data = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val token = data.optString("access_token")
        val refresh = data.optString("refresh_token")
        if (token.isBlank() || refresh.isBlank()) null to "Incomplete sign-in response."
        else (token to refresh) to ""
    } catch (_: Exception) { null to "Unable to complete sign-in. Please retry." }
    finally { connection?.disconnect() }
}
