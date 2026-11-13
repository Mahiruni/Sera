package et.sera.nativeapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable internal fun SeraCallVideoView(track:VideoTrack,egl:EglBase,mirror:Boolean,modifier:Modifier=Modifier){
    key(track,egl){
        AndroidView(factory={context->
            SurfaceViewRenderer(context).apply{
                init(egl.eglBaseContext,null)
                setMirror(mirror)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                if(mirror)setZOrderMediaOverlay(true)
                track.addSink(this)
            }
        },modifier=modifier,onReset=null,onRelease={view->
            runCatching{track.removeSink(view)}
            runCatching{view.release()}
        },update={})
    }
}
