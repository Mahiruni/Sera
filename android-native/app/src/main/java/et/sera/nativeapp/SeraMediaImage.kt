package et.sera.nativeapp

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size

/** Coil 3 image loading with bounded decode size and cache-friendly requests. */
@Composable
fun SeraMediaImage(url: String, description: String, widthPx: Int, heightPx: Int, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .size(Size(widthPx, heightPx))
            .crossfade(true)
            .build(),
        contentDescription = description,
        contentScale = scale,
        modifier = modifier
    )
}
