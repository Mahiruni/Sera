package et.sera.nativeapp

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Lightweight skeleton placeholders. Avoids shimmer gradients and excess GPU work. */
@Composable
internal fun SeraLoadingCards(count: Int = 3, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "sera loading")
    val opacity = transition.animateFloat(
        initialValue = 0.42f, targetValue = 0.78f,
        animationSpec = infiniteRepeatable(tween(850), repeatMode = RepeatMode.Reverse),
        label = "skeleton opacity"
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(count) {
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Spacer(Modifier.size(42.dp).alpha(opacity.value).background(Color(0xFFD6E8E7), CircleShape))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Spacer(Modifier.fillMaxWidth(0.62f).height(13.dp).alpha(opacity.value)
                            .background(Color(0xFFD6E8E7), RoundedCornerShape(6.dp)))
                        Spacer(Modifier.fillMaxWidth(0.42f).height(10.dp).alpha(opacity.value)
                            .background(Color(0xFFE1ECEB), RoundedCornerShape(6.dp)))
                    }
                }
                Spacer(Modifier.fillMaxWidth(0.88f).height(12.dp).alpha(opacity.value)
                    .background(Color(0xFFE1ECEB), RoundedCornerShape(6.dp)))
            }
        }
    }
}
