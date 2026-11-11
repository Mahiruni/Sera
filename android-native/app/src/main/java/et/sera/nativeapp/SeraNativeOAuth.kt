package et.sera.nativeapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import org.json.JSONObject

private const val CALLBACK = "sera://auth/callback"
private const val OAUTH_PREFS = "sera_native_oauth_pending"

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
    context.getSharedPreferences(OAUTH_PREFS, Context.MODE_PRIVATE).edit()
        .putString("verifier", verifier).putString("state", state)
        .putLong("created", System.currentTimeMillis()).apply()
    val url = Uri.parse(BuildConfig.SUPABASE_URL.trimEnd('/') + "/auth/v1/authorize")
        .buildUpon().appendQueryParameter("provider", provider)
        .appendQueryParameter("redirect_to", "$CALLBACK?state=$state")
        .appendQueryParameter("code_challenge", challenge)
        .appendQueryParameter("code_challenge_method", "s256")
        .build()
    return try {
        CustomTabsIntent.Builder().build().launchUrl(context, url)
        ""
    } catch (_: Exception) {
        context.getSharedPreferences(OAUTH_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        "Could not open the secure sign-in page."
    }
}

internal fun finishNativeOAuth(context: Context, uri: Uri): Pair<Pair<String, String>?, String> {
    if (uri.scheme != "sera" || uri.host != "auth" || uri.path != "/callback")
        return null to "Invalid sign-in callback."
    val prefs = context.getSharedPreferences(OAUTH_PREFS, Context.MODE_PRIVATE)
    val verifier = prefs.getString("verifier", null)
    val expectedState = prefs.getString("state", null)
    val created = prefs.getLong("created", 0L)
    // Consume the pending attempt exactly once, even when a callback is malformed.
    prefs.edit().clear().apply()
    if (verifier.isNullOrEmpty() || expectedState.isNullOrEmpty() ||
        System.currentTimeMillis() - created !in 0..600000)
        return null to "Sign-in session expired. Please retry."
    val returnedState = uri.getQueryParameter("state")
    if (returnedState != expectedState)
        return null to "Sign-in verification failed. Please retry."
    val error = uri.getQueryParameter("error_description") ?: uri.getQueryParameter("error")
    if (!error.isNullOrBlank()) return null to "Sign-in was not completed: $error"
    val code = uri.getQueryParameter("code")
    if (code.isNullOrBlank()) return null to "No authorization code was returned."
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
            return null to "Identity provider sign-in could not be completed (HTTP ${connection.responseCode})."
        val data = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val token = data.optString("access_token")
        val refresh = data.optString("refresh_token")
        if (token.isBlank() || refresh.isBlank()) null to "Incomplete sign-in response."
        else (token to refresh) to ""
    } catch (_: Exception) { null to "Unable to complete sign-in. Please retry." }
    finally { connection?.disconnect() }
}
