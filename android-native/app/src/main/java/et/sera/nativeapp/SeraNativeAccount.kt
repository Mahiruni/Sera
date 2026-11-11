package et.sera.nativeapp

import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Public Supabase Auth operations only; never embed any service-role credentials in Android. */
internal data class SeraAccountResponse(
    val success: Boolean,
    val message: String,
    val accessToken: String? = null,
    val refreshToken: String? = null
)

private const val ACCOUNT_CONFIRM_REDIRECT = "https://sera.hisabtech.com"

/** Direct Auth sign-up keeps account creation inside the native application. */
internal fun registerSeraAccount(
    fullName: String,
    email: String,
    password: String,
    locale: String,
    journey: String
): SeraAccountResponse {
    val cleanName = fullName.trim()
    val cleanEmail = email.trim().lowercase(Locale.ROOT)
    if (cleanName.length !in 2..100) return SeraAccountResponse(false, "Enter your full name (2–100 characters).")
    if (cleanEmail.length > 254 || !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(cleanEmail))
        return SeraAccountResponse(false, "Enter a valid email address.")
    if (password.length !in 12..128) return SeraAccountResponse(false, "Use a password between 12 and 128 characters.")
    if (journey != "candidate" && journey != "employer") return SeraAccountResponse(false, "Choose an account type.")
    if (locale !in setOf("en", "am", "om", "ti", "ar", "aa", "so")) return SeraAccountResponse(false, "Choose a supported language.")

    val data = JSONObject()
        .put("email", cleanEmail)
        .put("password", password)
        .put("data", JSONObject()
            .put("full_name", cleanName)
            .put("locale", locale)
            .put("journey", journey)
            .put("account_terms_version", "2026-11-07")
            .put("account_terms_acknowledged_at", java.time.Instant.now().toString()))
    val response = nativeAuthRequest("signup", data)
    if (!response.success) return response
    return SeraAccountResponse(
        true,
        if (response.accessToken != null) "Account created and signed in."
        else "Account request received. Check your email for the confirmation link; then return to SERA and sign in. Delivery may take some time.",
        response.accessToken,
        response.refreshToken
    )
}

internal fun resendSeraConfirmation(email: String): SeraAccountResponse {
    val cleanEmail = email.trim().lowercase(Locale.ROOT)
    if (!Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(cleanEmail))
        return SeraAccountResponse(false, "Enter a valid email address.")
    val r = nativeAuthRequest("resend", JSONObject().put("type", "signup").put("email", cleanEmail))
    return if (r.success) SeraAccountResponse(true, "If this address is awaiting verification, a new confirmation email has been requested. Check inbox and spam.")
    else r
}

internal fun recoverSeraPassword(email: String): SeraAccountResponse {
    val cleanEmail = email.trim().lowercase(Locale.ROOT)
    if (!Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(cleanEmail))
        return SeraAccountResponse(false, "Enter a valid email address.")
    val r = nativeAuthRequest("recover", JSONObject().put("email", cleanEmail))
    return if (r.success) SeraAccountResponse(true, "If the email belongs to an account, a password-reset link has been requested. Check inbox and spam.")
    else r
}

private fun nativeAuthRequest(path: String, body: JSONObject): SeraAccountResponse {
    if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank())
        return SeraAccountResponse(false, "Account services are not configured in this build.")
    var conn: HttpURLConnection? = null
    return try {
        val url = URL(
            BuildConfig.SUPABASE_URL.trimEnd('/') + "/auth/v1/" + path +
                "?redirect_to=" + Uri.encode(ACCOUNT_CONFIRM_REDIRECT)
        )
        if (url.protocol != "https") return SeraAccountResponse(false, "Secure connection unavailable.")
        conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 12000
            readTimeout = 25000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Accept", "application/json")
        }
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val http = conn.responseCode
        val data = (if (http in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "{}"
        val result = runCatching { JSONObject(data) }.getOrElse { JSONObject() }
        if (http in 200..299) {
            SeraAccountResponse(
                success = true,
                message = "Request accepted.",
                accessToken = result.optString("access_token").takeIf { it.isNotBlank() },
                refreshToken = result.optString("refresh_token").takeIf { it.isNotBlank() }
            )
        } else {
            val code = result.optString("error_code", result.optString("code"))
            val error = when {
                http == 429 || code.contains("rate_limit") ->
                    "Too many email requests. Please wait before trying again."
                code == "email_address_not_authorized" ->
                    "Email delivery is not enabled for this address. Please contact SERA support."
                code == "email_provider_disabled" -> "Email registration is temporarily unavailable."
                code == "weak_password" -> "Choose a stronger password of at least 12 characters."
                http in 500..599 -> "Account email service is temporarily unavailable. Please retry later."
                else -> "Unable to process your request. Check your details or try again later."
            }
            SeraAccountResponse(false, error)
        }
    } catch (_: Exception) {
        SeraAccountResponse(false, "Connection timed out or failed. Check your network and try again.")
    } finally {
        conn?.disconnect()
    }
}


/** Welcome mail is requested independently of login; failures never block account access. */
internal fun requestSeraWelcome(accessToken: String) {
    if (accessToken.isBlank()) return
    var conn: HttpURLConnection? = null
    try {
        val url = URL("https://sera.hisabtech.com/api/native/auth/welcome")
        conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 6000
            readTimeout = 16000
            doOutput = false
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
        }
        val result = conn.responseCode
        if (result in 200..299) conn.inputStream?.close() else conn.errorStream?.close()
    } catch (_: Exception) {
        // Welcome delivery is best-effort and does not interrupt a verified session.
    } finally {
        conn?.disconnect()
    }
}
