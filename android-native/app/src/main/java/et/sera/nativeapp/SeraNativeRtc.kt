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

/** Voice only. TURN relay is mandatory; no direct exposure of peer IP addresses. */
internal class SeraNativeRtc(private val context:Context,config:JSONObject,private val onState:(String)->Unit){
    private val manager=context.getSystemService(AudioManager::class.java)
    private val previousMode=manager.mode
    private val previousSpeaker=manager.isSpeakerphoneOn
    private val focusRequest=android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setOnAudioFocusChangeListener { change->if(change==AudioManager.AUDIOFOCUS_LOSS)onState("failed") }.build()
    private var closed=false
    private val factory:PeerConnectionFactory
    private val source:AudioSource
    private val track:AudioTrack
    private val pc:PeerConnection
    init {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context.applicationContext).createInitializationOptions())
        factory=PeerConnectionFactory.builder().createPeerConnectionFactory()
        val servers=config.getJSONArray("iceServers")
        val ice=(0 until servers.length()).map{val s=servers.getJSONObject(it);PeerConnection.IceServer.builder(s.getString("urls")).setUsername(s.getString("username")).setPassword(s.getString("credential")).createIceServer()}
        val rtc=PeerConnection.RTCConfiguration(ice).apply{sdpSemantics=PeerConnection.SdpSemantics.UNIFIED_PLAN;iceTransportsType=PeerConnection.IceTransportsType.RELAY}
        val observer=object:PeerConnection.Observer{
            override fun onSignalingChange(s:PeerConnection.SignalingState){}
            override fun onIceConnectionChange(s:PeerConnection.IceConnectionState){onState(when(s){PeerConnection.IceConnectionState.CONNECTED,PeerConnection.IceConnectionState.COMPLETED->"connected";PeerConnection.IceConnectionState.DISCONNECTED->"disconnected";PeerConnection.IceConnectionState.FAILED->"failed";PeerConnection.IceConnectionState.CHECKING->"connecting";else->"new"})}
            override fun onIceConnectionReceivingChange(receiving:Boolean){}
            override fun onIceGatheringChange(s:PeerConnection.IceGatheringState){}
            override fun onIceCandidate(candidate:IceCandidate){}
            override fun onIceCandidatesRemoved(candidates:Array<out IceCandidate>){}
            override fun onAddStream(stream:MediaStream){}
            override fun onRemoveStream(stream:MediaStream){}
            override fun onDataChannel(channel:DataChannel){}
            override fun onRenegotiationNeeded(){}
            override fun onAddTrack(receiver:RtpReceiver,streams:Array<out MediaStream>){}
            override fun onConnectionChange(state:PeerConnection.PeerConnectionState){if(state==PeerConnection.PeerConnectionState.FAILED)onState("failed")}
        }
        pc=factory.createPeerConnection(rtc,observer)?:error("Voice connection could not initialize.")
        source=factory.createAudioSource(MediaConstraints())
        track=factory.createAudioTrack("sera-voice",source)
        pc.addTrack(track,listOf("sera-audio"))
        manager.mode=AudioManager.MODE_IN_COMMUNICATION
        manager.requestAudioFocus(focusRequest)
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
    suspend fun offer():String=withTimeout(15000){set(description(true),true);gather()}
    suspend fun answer(sdp:String):String=withTimeout(15000){set(SessionDescription(SessionDescription.Type.OFFER,sdp),false);set(description(false),true);gather()}
    suspend fun remoteAnswer(sdp:String)=withTimeout(10000){set(SessionDescription(SessionDescription.Type.ANSWER,sdp),false)}
    private suspend fun gather():String{
        while(pc.iceGatheringState()!=PeerConnection.IceGatheringState.COMPLETE){delay(100)}
        return pc.localDescription?.description?:error("Call connection unavailable.")
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
    fun close(){
        if(closed)return;closed=true
        runCatching{track.setEnabled(false)}
        runCatching{pc.close();pc.dispose()}
        runCatching{track.dispose();source.dispose();factory.dispose()}
        runCatching{manager.abandonAudioFocusRequest(focusRequest);if(Build.VERSION.SDK_INT>=31)manager.clearCommunicationDevice()else @Suppress("DEPRECATION") run{manager.isSpeakerphoneOn=previousSpeaker};manager.mode=previousMode}
    }
}
