package et.sera.nativeapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

/** Coil 3 image loading with bounded decode size and cache-friendly requests. */
@Composable
fun SeraMediaImage(url: String, description: String, widthPx: Int, heightPx: Int, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop, accessToken:String?=null) {
    val context = LocalContext.current
    val request=remember(context,url,widthPx,heightPx,accessToken){ImageRequest.Builder(context)
            .data(url)
            .size(Size(widthPx, heightPx))
            .crossfade(true)
            .apply { if(accessToken!=null && url.startsWith(BuildConfig.SUPABASE_URL.trimEnd('/')+"/storage/v1/object/authenticated/sera-post-media/")){ httpHeaders(NetworkHeaders.Builder().set("Authorization","Bearer $accessToken").set("apikey",BuildConfig.SUPABASE_ANON_KEY).build());diskCachePolicy(CachePolicy.DISABLED);memoryCachePolicy(CachePolicy.DISABLED) } }
            .build()}
    AsyncImage(
        model = request,
        contentDescription = description,
        contentScale = scale,
        modifier = modifier
    )
}
