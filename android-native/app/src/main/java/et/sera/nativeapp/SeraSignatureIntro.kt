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

// Pixel-traced from the original SERA screenshot (white silhouette, not a font). Keep the same
// silhouette and upper-right square in the system and Compose splash layers.
// The full-screen transition dilates THIS shape; it is not a white crossfade.
private const val SIGNATURE_S = "M64.49,9.48 L60.68,9.48 L60.36,9.16 L51.14,9.48 L50.82,9.80 L49.23,9.80 L48.91,10.11 L47.64,10.11 L47.32,10.43 L46.05,10.43 L44.78,11.07 L42.55,11.39 L38.42,13.29 L37.78,13.29 L33.65,15.52 L28.56,19.65 L24.74,24.74 L21.88,30.47 L21.88,31.42 L20.93,33.96 L20.93,39.37 L22.20,43.51 L24.42,47.00 L27.92,50.50 L31.74,53.05 L39.37,56.54 L43.19,57.82 L45.41,58.13 L47.32,59.09 L45.73,61.00 L27.92,77.53 L27.29,77.85 L26.01,77.22 L23.47,77.22 L22.52,77.53 L20.29,79.44 L19.34,81.67 L19.34,83.58 L20.29,85.80 L24.11,89.30 L31.10,93.43 L31.74,93.43 L33.96,94.71 L35.55,95.02 L38.42,96.30 L39.37,96.30 L42.23,97.25 L43.51,97.25 L43.82,97.57 L45.10,97.57 L45.41,97.89 L55.27,98.84 L55.59,98.52 L59.72,98.52 L60.04,98.20 L64.18,97.89 L64.49,97.57 L65.77,97.57 L67.04,96.93 L68.31,96.93 L69.27,96.30 L71.17,95.98 L75.63,93.75 L78.49,91.84 L82.94,87.39 L86.12,82.62 L86.12,81.99 L86.76,81.35 L87.07,79.76 L88.03,77.85 L88.35,74.35 L88.66,74.04 L88.03,68.63 L86.44,65.13 L85.17,63.22 L81.35,59.41 L76.58,56.23 L71.17,53.68 L65.45,51.77 L64.49,51.77 L62.27,50.82 L58.13,50.18 L57.50,49.55 L57.50,48.91 L80.08,27.92 L81.03,27.92 L81.35,28.24 L84.53,28.24 L85.48,27.92 L86.76,26.97 L88.35,24.11 L88.35,21.88 L86.76,19.02 L81.35,14.88 L76.90,12.66 L73.40,11.39 L72.45,11.39 L68.31,10.11 L64.81,9.80 Z"
private val signatureTeal = Color(0xFF087E83)
private val signatureMint = Color(0xFF8CE9E1)

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
                            topLeft = Offset(86.44f, 8.84f),
                            size = Size(5.09f, 5.09f)
                        )
                    }
                }
            }
        }
    }
}
