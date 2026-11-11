package et.sera.nativeapp

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** One optically consistent surface for native composers, comments and job sheets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SeraSignatureSheet(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Color.White,
        contentColor = Color(0xFF18383D),
        tonalElevation = 0.dp,
        scrimColor = Color(0x990C262D),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        dragHandle = {
            Box(
                Modifier.padding(top = 12.dp, bottom = 13.dp)
                    .size(width = 44.dp, height = 4.dp)
                    .background(Color(0xFFC5D8D5), RoundedCornerShape(20.dp))
            )
        },
        content = content
    )
}

internal enum class SeraReactionKind { LIKE, COMMENT, REPOST, SHARE }

/** Native vector paths matched to the 24 x 24 SERA web reaction family. */
@Composable
internal fun SeraReactionGlyph(
    kind: SeraReactionKind,
    active: Boolean = false,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    val ink by animateColorAsState(
        targetValue = tint ?: if (active) Color(0xFF087E83) else Color(0xFF687F83),
        animationSpec = spring(), label = "SERA reaction color"
    )
    val paths = remember(kind) {
        val segments = when (kind) {
            SeraReactionKind.LIKE -> listOf("M12 20.4 L4.7 13.4 C0.3 9.3 3.2 3.2 7.6 3.9 C9.4 4.1 10.7 5.1 12 6.5 C13.3 5.1 14.7 4.1 16.4 3.9 C20.8 3.2 23.7 9.3 19.3 13.4 Z")
            SeraReactionKind.COMMENT -> listOf("M20.3 11.7 C20.3 16.6 16.6 19.8 11.9 19.8 C10.7 19.8 9.5 19.6 8.4 19.2 L4 20 L4.8 15.9 C4.1 14.6 3.7 13.2 3.7 11.7 C3.7 6.8 7.4 3.4 12 3.4 C16.8 3.4 20.3 6.9 20.3 11.7 Z", "M8.5 11.8 L15.5 11.8")
            SeraReactionKind.REPOST -> listOf("M4 8.1 V6.6 C4 5.5 4.9 4.6 6 4.6 H16.3 L13.8 2.2 M16.3 4.6 L13.8 7", "M20 15.9 V17.4 C20 18.5 19.1 19.4 18 19.4 H7.7 L10.2 21.8 M7.7 19.4 L10.2 17", "M4 12.2 V16.2 M20 11.8 V7.8")
            SeraReactionKind.SHARE -> listOf("M12 15.6 V3.3 M12 3.3 L7.7 7.6 M12 3.3 L16.3 7.6", "M5.7 12.1 H4.2 V18.6 C4.2 19.7 5.1 20.6 6.2 20.6 H17.8 C18.9 20.6 19.8 19.7 19.8 18.6 V12.1 H18.3")
        }
        segments.map { PathParser().parsePathString(it).toPath() }
    }
    Canvas(modifier.size(23.dp)) {
        val factor = size.minDimension / 24f
        withTransform({ scale(factor, factor, pivot = Offset.Zero) }) {
            val stroke = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            paths.forEachIndexed { index, path ->
                if (kind == SeraReactionKind.LIKE && active && index == 0) drawPath(path, ink)
                else drawPath(path, ink, style = stroke)
            }
        }
    }
}
