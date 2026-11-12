package et.sera.nativeapp

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.security.MessageDigest
import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

// The web OAuth client is the ID-token audience. Android registration is tied
// to et.sera.nativeapp and the production signing certificate in Google Cloud.
private const val GOOGLE_SERVER_CLIENT_ID = "307543468253-n1atc3v08jelbekr50puc31o9jlnb9ts.apps.googleusercontent.com"

private fun Context.activity(): Activity? = when(this) {
 is Activity -> this
 is ContextWrapper -> baseContext.activity()
 else -> null
}

internal suspend fun signInWithNativeGoogle(context: Context): Pair<Pair<String,String>?,String> {
 val activity=context.activity() ?: return null to "Open SERA to continue sign-in."
 if(BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) return null to "Authentication is not configured."
 val nonce=Base64.encodeToString(ByteArray(32).also{SecureRandom().nextBytes(it)},Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
 val hash=MessageDigest.getInstance("SHA-256").digest(nonce.toByteArray()).joinToString(""){ "%02x".format(it.toInt() and 255) }
 return try {
  val option=GetSignInWithGoogleOption.Builder(GOOGLE_SERVER_CLIENT_ID).setNonce(hash).build()
  val request=GetCredentialRequest.Builder().addCredentialOption(option).build()
  val credential=CredentialManager.create(activity).getCredential(context=activity,request=request).credential
  if(credential !is CustomCredential || credential.type!=GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) return null to "Google returned an unsupported credential."
  val token=GoogleIdTokenCredential.createFrom(credential.data).idToken
  withContext(Dispatchers.IO){exchangeGoogleToken(token,nonce)}
 } catch(e:CancellationException){throw e}
 catch(_:GetCredentialCancellationException){null to "Google sign-in cancelled."}
 catch(_:GetCredentialException){null to "Google sign-in is unavailable. Check your Google account and try again."}
 catch(_:Exception){null to "Unable to complete Google sign-in. Please retry."}
}

private fun exchangeGoogleToken(token:String,nonce:String):Pair<Pair<String,String>?,String>{
 var connection:HttpURLConnection?=null
 return try {
  connection=(URL(BuildConfig.SUPABASE_URL.trimEnd('/')+"/auth/v1/token?grant_type=id_token").openConnection() as HttpURLConnection).apply{
   requestMethod="POST";doOutput=true;connectTimeout=12000;readTimeout=16000
   setRequestProperty("apikey",BuildConfig.SUPABASE_ANON_KEY)
   setRequestProperty("Content-Type","application/json")
  }
  connection.outputStream.use{it.write(JSONObject().put("provider","google").put("id_token",token).put("nonce",nonce).toString().toByteArray())}
  if(connection.responseCode !in 200..299) return null to "Google account verification failed. Please retry."
  val data=JSONObject(connection.inputStream.bufferedReader().use{it.readText()})
  val access=data.optString("access_token");val refresh=data.optString("refresh_token")
  if(access.isBlank()||refresh.isBlank()) null to "Incomplete sign-in response." else (access to refresh) to ""
 }catch(_:Exception){null to "Unable to connect securely. Please retry."}finally{connection?.disconnect()}
}
