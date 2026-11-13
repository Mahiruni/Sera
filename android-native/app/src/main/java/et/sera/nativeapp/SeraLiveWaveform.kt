package et.sera.nativeapp

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription

/** Live microphone samples only; no fabricated audio waveform. */
@Composable
internal fun SeraLiveWaveform(levels:List<Float>,color:Color,modifier:Modifier=Modifier){
    Canvas(modifier.semantics{contentDescription="Live microphone waveform"}){
        if(levels.isEmpty())return@Canvas
        val step=size.width/levels.size
        levels.forEachIndexed{i,level->
            val height=(size.height*(.16f+.84f*level.coerceIn(0f,1f))).coerceAtLeast(2f)
            val x=(i+.5f)*step
            drawLine(color,Offset(x,(size.height-height)/2),Offset(x,(size.height+height)/2),strokeWidth=(step*.5f).coerceAtLeast(1f),cap=StrokeCap.Round)
        }
    }
}
