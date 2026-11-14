package et.sera.nativeapp

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.size.Size

/** Bounded decode, adaptive authenticated previews, original fallback. */
@Composable
fun SeraMediaImage(url: String, description: String, widthPx: Int, heightPx: Int, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop, accessToken:String?=null, previewUrl:String?=null) {
    val context=LocalContext.current
    var fallback by remember(url,previewUrl,accessToken){mutableStateOf(false)}
    val preview=remember(context,previewUrl,accessToken){
        if(accessToken==null||previewUrl==null||!Regex("https://sera[.]hisabtech[.]com/api/post-media/[0-9a-fA-F-]{36}").matches(previewUrl))null
        else {
            val cm=context.getSystemService(android.net.ConnectivityManager::class.java)
            val kbps=runCatching{cm.getNetworkCapabilities(cm.activeNetwork)?.linkDownstreamBandwidthKbps?:0}.getOrDefault(0)
            val metered=runCatching{cm.isActiveNetworkMetered}.getOrDefault(true)
            val w=if(kbps in 1..1000)480 else if(metered)800 else 1200
            previewUrl+"?w="+w
        }
    }
    val selectedUrl=if(fallback)url else preview?:url
    val request=remember(context,selectedUrl,widthPx,heightPx,accessToken){ImageRequest.Builder(context)
        .data(selectedUrl).size(Size(widthPx,heightPx)).crossfade(true)
        .apply {
            val privateSigned=selectedUrl.startsWith(BuildConfig.SUPABASE_URL.trimEnd('/')+"/storage/v1/object/sign/")
            if(privateSigned){
                diskCachePolicy(CachePolicy.DISABLED)
                // Avatar paths change with each uploaded photo; reuse only within
                // a short account/token-scoped memory window, never on disk.
                val avatar=selectedUrl.startsWith(BuildConfig.SUPABASE_URL.trimEnd('/')+"/storage/v1/object/sign/sera-account-private/")&&selectedUrl.substringBefore('?').contains("/avatar/")
                if(avatar&&!accessToken.isNullOrBlank()){
                    val owner=java.security.MessageDigest.getInstance("SHA-256").digest(accessToken.toByteArray()).joinToString(""){"%02x".format(it)}
                    val window=android.os.SystemClock.elapsedRealtime()/60000L
                    memoryCacheKey("sera-avatar:$owner:$window:"+selectedUrl.substringBefore('?')+":$widthPx:$heightPx")
                    memoryCachePolicy(CachePolicy.ENABLED)
                }else memoryCachePolicy(CachePolicy.DISABLED)
            }
            if(accessToken!=null && (selectedUrl==preview||selectedUrl.startsWith(BuildConfig.SUPABASE_URL.trimEnd('/')+"/storage/v1/object/authenticated/sera-post-media/"))){
                httpHeaders(NetworkHeaders.Builder().set("Authorization","Bearer $accessToken").set("apikey",BuildConfig.SUPABASE_ANON_KEY).build())
                diskCachePolicy(CachePolicy.DISABLED);memoryCachePolicy(CachePolicy.DISABLED)
            }
        }.build()}
    AsyncImage(model=request,contentDescription=description,contentScale=scale,modifier=modifier,
        onError={if(preview!=null&&selectedUrl==preview)fallback=true})
}
