package et.sera.nativeapp

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/** Shared connection pool; no implicit replay of non-idempotent writes. */
internal object SeraHttp {
    val client=OkHttpClient.Builder()
        .connectTimeout(7,TimeUnit.SECONDS).readTimeout(12,TimeUnit.SECONDS)
        .callTimeout(18,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).build()
    data class Result(val status:Int,val body:String?,val error:String)
    fun rest(token:String,path:String,method:String="GET",body:JSONObject?=null,prefer:String?=null):Result{
        return try{
            val builder=Request.Builder().url(BuildConfig.SUPABASE_URL.trimEnd('/')+"/rest/v1/"+path)
                .header("apikey",BuildConfig.SUPABASE_ANON_KEY).header("Authorization","Bearer "+token)
                .header("Accept","application/json").header("Cache-Control","no-store")
            if(prefer!=null)builder.header("Prefer",prefer)
            val payload=body?.toString()?.toRequestBody("application/json".toMediaType())
            builder.method(method,if(method=="POST"&&payload==null)"{}".toRequestBody("application/json".toMediaType()) else payload)
            client.newCall(builder.build()).execute().use{response->
                if(response.isSuccessful)Result(response.code,response.body?.string()?:"","")
                else Result(response.code,null,"Request failed (HTTP ${response.code}). Please retry.")
            }
        }catch(_:Exception){Result(0,null,"Connection failed. Check your network and retry.")}
    }
}
