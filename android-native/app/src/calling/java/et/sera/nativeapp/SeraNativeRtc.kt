package et.sera.nativeapp

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.media.AudioDeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.webrtc.*
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class SeraAudioRoute(val id:Int,val label:String)

/** Voice only. TURN relay is mandatory; no direct exposure of peer IP addresses. */
internal class SeraNativeRtc(private val context:Context,config:JSONObject,private val onState:(String)->Unit){
    private val manager=context.getSystemService(AudioManager::class.java)
    private val previousMode=manager.mode
    private val previousSpeaker=manager.isSpeakerphoneOn
    private val focusRequest=android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setOnAudioFocusChangeListener { change->if(change==AudioManager.AUDIOFOCUS_LOSS)onState("failed") }.build()
    private var closed=false
    companion object {
        // Invoked only by the separately signed CI instrumentation; no public UI or network endpoint.
        // Keep all coroutine execution inside the already-shrunk target APK.
        @JvmStatic fun verifySignedEngine(context:Context, main:java.util.concurrent.Executor){
            fun <T> onMain(block:()->T):T {
                val task=java.util.concurrent.FutureTask<T>{block()}
                main.execute(task)
                return task.get(15,java.util.concurrent.TimeUnit.SECONDS)
            }
            val config=JSONObject().put("iceServers",org.json.JSONArray().put(JSONObject()
                .put("urls","turn:127.0.0.1:3478?transport=udp")
                .put("username","smoke-test").put("credential","smoke-test")))
            repeat(2){
                val engine=onMain{SeraNativeRtc(context,config){}}
                try{
                    val sdp=kotlinx.coroutines.runBlocking { engine.prepareLocalOffer() }
                    check(sdp.contains("m=audio ")){"Native WebRTC did not create an audio offer"}
                }finally{onMain{engine.close()}}
            }
        }
        private var initialized=false
        @Synchronized private fun ensureInitialized(context:Context){
            if(!initialized){
                PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context.applicationContext).createInitializationOptions())
                initialized=true
            }
        }
    }
    private val factory:PeerConnectionFactory
    private val source:AudioSource
    private val track:AudioTrack
    private val pc:PeerConnection
    init {
        android.util.Log.i("SeraRtc","Initializing WebRTC")
        ensureInitialized(context)
        factory=PeerConnectionFactory.builder().createPeerConnectionFactory()
        android.util.Log.i("SeraRtc","WebRTC factory ready")
        val servers=config.getJSONArray("iceServers")
        val ice=(0 until servers.length()).map{val s=servers.getJSONObject(it);PeerConnection.IceServer.builder(s.getString("urls")).setUsername(s.getString("username")).setPassword(s.getString("credential")).createIceServer()}
        val rtc=PeerConnection.RTCConfiguration(ice).apply{sdpSemantics=PeerConnection.SdpSemantics.UNIFIED_PLAN;iceTransportsType=PeerConnection.IceTransportsType.RELAY}
        val observer=object:PeerConnection.Observer{
            override fun onSignalingChange(s:PeerConnection.SignalingState){}
            override fun onIceConnectionChange(s:PeerConnection.IceConnectionState){}
            override fun onIceConnectionReceivingChange(receiving:Boolean){}
            override fun onIceGatheringChange(s:PeerConnection.IceGatheringState){}
            override fun onIceCandidate(candidate:IceCandidate){}
            override fun onIceCandidatesRemoved(candidates:Array<out IceCandidate>){}
            override fun onAddStream(stream:MediaStream){}
            override fun onRemoveStream(stream:MediaStream){}
            override fun onDataChannel(channel:DataChannel){}
            override fun onRenegotiationNeeded(){}
            override fun onAddTrack(receiver:RtpReceiver,streams:Array<out MediaStream>){}
            override fun onConnectionChange(state:PeerConnection.PeerConnectionState){onState(when(state){PeerConnection.PeerConnectionState.CONNECTED->"connected";PeerConnection.PeerConnectionState.CONNECTING->"connecting";PeerConnection.PeerConnectionState.DISCONNECTED->"disconnected";PeerConnection.PeerConnectionState.FAILED->"failed";else->"new"})}
        }
        pc=factory.createPeerConnection(rtc,observer)?:error("Voice connection could not initialize.")
        android.util.Log.i("SeraRtc","Peer connection ready")
        source=factory.createAudioSource(MediaConstraints())
        track=factory.createAudioTrack("sera-voice",source)
        pc.addTrack(track,listOf("sera-audio"))
        manager.mode=AudioManager.MODE_IN_COMMUNICATION
        manager.requestAudioFocus(focusRequest)
        android.util.Log.i("SeraRtc","Audio track ready")
    }
    private suspend fun description(offer:Boolean):SessionDescription=suspendCancellableCoroutine{c->
        val observer=object:SdpObserver{
            override fun onCreateSuccess(s:SessionDescription){if(c.isActive)c.resume(s)}
            override fun onCreateFailure(message:String){if(c.isActive)c.resumeWithException(IllegalStateException("Could not prepare call."))}
            override fun onSetSuccess(){}
            override fun onSetFailure(message:String){}
        }
        if(offer)pc.createOffer(observer,MediaConstraints())else pc.createAnswer(observer,MediaConstraints())
    }
    private suspend fun set(s:SessionDescription,local:Boolean)=suspendCancellableCoroutine<Unit>{c->
        val observer=object:SdpObserver{
            override fun onCreateSuccess(s:SessionDescription){}
            override fun onCreateFailure(message:String){}
            override fun onSetSuccess(){if(c.isActive)c.resume(Unit)}
            override fun onSetFailure(message:String){if(c.isActive)c.resumeWithException(IllegalStateException("Could not apply call connection."))}
        }
        if(local)pc.setLocalDescription(observer,s)else pc.setRemoteDescription(observer,s)
    }
    suspend fun prepareLocalOffer():String=withTimeout(10000){val offer=description(true);set(offer,true);offer.description}
    suspend fun offer():String=withTimeout(35000){prepareLocalOffer();gather()}
    suspend fun answer(sdp:String):String=withTimeout(35000){set(SessionDescription(SessionDescription.Type.OFFER,sdp),false);set(description(false),true);gather()}
    suspend fun remoteAnswer(sdp:String)=withTimeout(10000){set(SessionDescription(SessionDescription.Type.ANSWER,sdp),false)}
    private suspend fun gather():String{
        val deadline=android.os.SystemClock.elapsedRealtime()+25000L
        while(!closed&&pc.iceGatheringState()!=PeerConnection.IceGatheringState.COMPLETE&&android.os.SystemClock.elapsedRealtime()<deadline){
            // Non-trickle signaling can proceed as soon as a valid relay candidate exists.
            val current=pc.localDescription?.description.orEmpty()
            if(current.lineSequence().any{it.startsWith("a=candidate:")&&it.contains(" typ relay ")})break
            delay(100)
        }
        if(closed)throw kotlinx.coroutines.CancellationException("Call closed")
        val sdp=pc.localDescription?.description?:error("Call connection unavailable.")
        if(!sdp.lineSequence().any{it.startsWith("a=candidate:")&&it.contains(" typ relay ")})throw SeraRelayUnavailable()
        return sdp
    }
    suspend fun audioSample():SeraAudioSample?=kotlinx.coroutines.withTimeoutOrNull(2500){
        suspendCancellableCoroutine{continuation->
            if(closed){continuation.resume(null);return@suspendCancellableCoroutine}
            pc.getStats{report->
                val audio=report.statsMap.values.filter{stat->stat.type=="inbound-rtp"&&(stat.members["kind"]=="audio"||stat.members["mediaType"]=="audio")}
                val samples=audio.mapNotNull{stat->val received=(stat.members["packetsReceived"] as? Number)?.toLong();val lost=(stat.members["packetsLost"] as? Number)?.toLong();if(received!=null&&lost!=null)SeraAudioSample(received,lost)else null}
                val sample=if(samples.isEmpty())null else SeraAudioSample(samples.sumOf{it.received},samples.sumOf{it.lost})
                if(continuation.isActive)continuation.resume(sample)
            }
        }
    }
    fun mute(value:Boolean){if(!closed)track.setEnabled(!value)}
    fun speaker(value:Boolean){
        if(closed)return
        if(Build.VERSION.SDK_INT>=31){
            val type=if(value)AudioDeviceInfo.TYPE_BUILTIN_SPEAKER else AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            val device=manager.availableCommunicationDevices.firstOrNull{it.type==type}
            if(device!=null)manager.setCommunicationDevice(device)else if(!value)manager.clearCommunicationDevice()
        }else @Suppress("DEPRECATION") run{manager.isSpeakerphoneOn=value}
    }
    fun routes():List<SeraAudioRoute>{
        if(closed)return emptyList()
        if(Build.VERSION.SDK_INT>=31)return listOf(SeraAudioRoute(-1,"System default"))+manager.availableCommunicationDevices.map{device->
            SeraAudioRoute(device.id,when(device.type){
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER->"Speaker"
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE->"Earpiece"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,AudioDeviceInfo.TYPE_BLE_HEADSET,AudioDeviceInfo.TYPE_BLE_SPEAKER->"Bluetooth"
                AudioDeviceInfo.TYPE_WIRED_HEADSET,AudioDeviceInfo.TYPE_WIRED_HEADPHONES,AudioDeviceInfo.TYPE_USB_HEADSET->"Headset"
                else->"Audio device"
            })
        }
        return listOf(SeraAudioRoute(-1,"System default"),SeraAudioRoute(-2,"Speaker"))
    }
    fun selectRoute(id:Int):Boolean{
        if(closed)return false
        return runCatching{
            if(Build.VERSION.SDK_INT>=31){
                if(id==-1){manager.clearCommunicationDevice();true}
                else manager.availableCommunicationDevices.firstOrNull{it.id==id}?.let{manager.setCommunicationDevice(it)}?:false
            }else{speaker(id==-2);true}
        }.getOrDefault(false)
    }
    fun close(){
        if(closed)return;closed=true
        runCatching{track.setEnabled(false)}
        runCatching{pc.close();pc.dispose()}
        runCatching{track.dispose();source.dispose();factory.dispose()}
        runCatching{manager.abandonAudioFocusRequest(focusRequest);if(Build.VERSION.SDK_INT>=31)manager.clearCommunicationDevice()else @Suppress("DEPRECATION") run{manager.isSpeakerphoneOn=previousSpeaker};manager.mode=previousMode}
    }
}
