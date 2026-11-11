package et.sera.nativeapp

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

// Traced from the owner's original SERA splash screenshot. Keep the same
// silhouette and upper-right square in the system and Compose splash layers.
// The full-screen transition dilates THIS shape; it is not a white crossfade.
private const val SIGNATURE_S = "M62.7,9.5 L52.6,9.5 L48.1,10.1 L41.8,11.7 L35.5,14.5 L32.0,16.8 L27.5,20.9 L24.4,25.3 L21.8,30.7 L20.9,35.1 L21.2,40.5 L24.7,47.2 L28.5,50.7 L32.9,53.5 L47.2,58.9 L27.5,77.6 L25.6,77.0 L22.8,77.3 L21.2,78.3 L19.6,80.5 L19.6,84.3 L23.7,88.7 L32.0,93.5 L38.6,96.0 L50.7,98.2 L57.3,98.2 L65.3,97.3 L70.6,95.7 L77.6,91.9 L82.1,87.8 L85.5,82.7 L87.8,77.0 L88.1,70.3 L85.9,64.6 L81.7,59.9 L75.7,55.7 L70.0,53.2 L57.6,50.0 L57.0,49.4 L79.2,28.2 L84.0,28.2 L86.5,26.9 L88.1,23.7 L87.8,21.2 L87.1,19.9 L83.3,16.4 L76.3,12.6 L70.3,10.7 Z"
private val signatureTeal = Color(0xFF087E83)
private val signatureMint = Color(0xFFB8E7E2)

private fun smoothSegment(position: Float, start: Float, end: Float): Float {
    val t = ((position - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * The platform splash is the static first frame. This draws the signature
 * expansion on top of the REAL app while session restoration happens.
 *
 * Never run for a warm start, saved-instance restoration, or deep link.
 * A system-disabled animation skips the sequence entirely.
 */
@Composable
internal fun SeraSignatureIntro(
    destinationReady: Boolean,
    onWhiteFrame: () -> Unit,
    onComplete: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val overlayOpacity = remember { Animatable(1f) }
    val readyNow = rememberUpdatedState(destinationReady)
    val onWhiteNow = rememberUpdatedState(onWhiteFrame)
    val onCompleteNow = rememberUpdatedState(onComplete)
    val logoPath = remember { PathParser().parsePathString(SIGNATURE_S).toPath() }

    LaunchedEffect(Unit) {
        val animationsEnabled = ValueAnimator.areAnimatorsEnabled()
        if (animationsEnabled) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1700, easing = LinearEasing)
            )
        } else {
            progress.snapTo(1f)
        }
        // Hold at the clean white endpoint if authentication still needs time.
        while (!readyNow.value) delay(32)
        onWhiteNow.value()
        if (animationsEnabled) {
            overlayOpacity.animateTo(0f, tween(durationMillis = 230))
        }
        onCompleteNow.value()
    }

    Box(
        modifier = Modifier.fillMaxSize()
            .alpha(overlayOpacity.value)
            .pointerInput(Unit) { detectTapGestures(onTap = {}) }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val frame = progress.value
            if (frame >= 0.94f) {
                drawRect(Color.White)
            } else {
                drawRect(signatureTeal)

                // Scene 1: logo awakens; scene 2: almost-imperceptible rotation.
                val awaken = smoothSegment(frame, 0.02f, 0.22f)
                val breathe = smoothSegment(frame, 0.22f, 0.48f)
                val expansion = smoothSegment(frame, 0.50f, 0.92f)
                val logoScale = 0.92f + 0.08f * awaken + 0.045f * breathe +
                    3.35f * expansion
                val tilt = if (frame < 0.50f)
                    (1.2 * sin((frame / 0.50f).toDouble() * PI)).toFloat() else 0f
                val visibility = (0.86f + 0.14f * awaken).coerceIn(0f, 1f)

                // Scale the ORIGINAL vector geometry. The silhouette itself
                // grows a rounded white boundary until the entire screen is white.
                val baseSize = minOf(size.width * 0.53f, size.height * 0.26f)
                val pixelsPerUnit = baseSize / 108f
                withTransform({
                    translate(
                        left = size.width / 2f - 54f,
                        top = size.height / 2f - 54f
                    )
                    scale(
                        scaleX = pixelsPerUnit * logoScale,
                        scaleY = pixelsPerUnit * logoScale,
                        pivot = Offset(54f, 54f)
                    )
                    rotate(tilt, pivot = Offset(54f, 54f))
                }) {
                    if (expansion > 0f) {
                        drawPath(
                            logoPath,
                            color = Color.White,
                            style = Stroke(
                                width = 520f * expansion.pow(2.15f),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                    drawPath(logoPath, color = Color.White.copy(alpha = visibility))
                    if (expansion < 0.76f) {
                        drawRect(
                            color = signatureMint.copy(alpha = 1f - expansion / 0.76f),
                            topLeft = Offset(85.7f, 8.3f),
                            size = Size(6.2f, 6.2f)
                        )
                    }
                }
            }
        }
    }
}
