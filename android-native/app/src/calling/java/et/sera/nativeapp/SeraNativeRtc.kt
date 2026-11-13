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
internal class SeraNativeRtc(private val context:Context,config:JSONObject,private val manageAudioFocus:Boolean=true,private val onState:(String)->Unit){
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
                    check(sdp.contains("useinbandfec=1")&&sdp.contains("maxaveragebitrate=32000")){"Speech protection was not negotiated in local SDP"}
                }finally{onMain{engine.close()}}
            }
        }
        /** Isolated emulator QA: real authenticated relay, no production credentials.
         *  Two native peers in one process share an audio device, so this proves transport
         *  and renegotiation, not human speech quality or two physical microphones. */
        @JvmStatic fun verifyRelayRecovery(context:Context,main:java.util.concurrent.Executor,url:String,username:String,credential:String){
            fun <T> onMain(block:()->T):T{
                val task=java.util.concurrent.FutureTask<T>{block()}
                main.execute(task)
                return task.get(15,java.util.concurrent.TimeUnit.SECONDS)
            }
            val config=JSONObject().put("iceServers",org.json.JSONArray().put(JSONObject().put("urls",url).put("username",username).put("credential",credential)))
            val a=onMain{SeraNativeRtc(context,config,false){}}
            try{
                val b=onMain{SeraNativeRtc(context,config,false){}}
                try{
                    kotlinx.coroutines.runBlocking{
                        fun ufrag(sdp:String)=sdp.lineSequence().firstOrNull{it.startsWith("a=ice-ufrag:")}?.trim()
                        fun relayOnly(sdp:String):Boolean{
                            val candidates=sdp.lineSequence().filter{it.startsWith("a=candidate:")}.toList()
                            return candidates.isNotEmpty()&&candidates.all{it.contains(" typ relay ")}
                        }
                        suspend fun awaitConnected(){
                            withTimeout(25000){
                                while(!(a.connectedThroughRelay()&&b.connectedThroughRelay()))delay(250)
                            }
                        }
                        val offer=a.offer();check(relayOnly(offer)){"Native initial offer is not relay-only"}
                        val answer=b.answer(offer);check(relayOnly(answer)){"Native initial answer is not relay-only"}
                        a.remoteAnswer(answer);awaitConnected()
                        val initialA=a.track;val initialB=b.track
                        val next=a.restartOffer()
                        check(ufrag(next)!=null&&ufrag(next)!=ufrag(offer)&&relayOnly(next)){"Native restart offer did not rotate credentials"}
                        val nextAnswer=b.restartAnswer(next)
                        check(ufrag(nextAnswer)!=null&&ufrag(nextAnswer)!=ufrag(answer)&&relayOnly(nextAnswer)){"Native restart answer did not rotate credentials"}
                        a.remoteAnswer(nextAnswer);awaitConnected()
                        check(a.track===initialA&&b.track===initialB){"Native restart replaced the audio track"}
                        check(a.signalingStable()&&b.signalingStable()){"Native restart signaling did not become stable"}
                    }
                }finally{onMain{b.close()}}
            }finally{onMain{a.close()}}
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
    private val sender:RtpSender
    private val speech=SeraSpeechAdaptation()
    private var appliedBitrate:Int?=null
    private var lastRemoteReport:Double?=null
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
        source=factory.createAudioSource(MediaConstraints().apply{
            optional.add(MediaConstraints.KeyValuePair("googEchoCancellation","true"))
            optional.add(MediaConstraints.KeyValuePair("googNoiseSuppression","true"))
            optional.add(MediaConstraints.KeyValuePair("googAutoGainControl","true"))
        })
        track=factory.createAudioTrack("sera-voice",source)
        sender=pc.addTrack(track,listOf("sera-audio"))?:error("Voice track unavailable.")
        if(manageAudioFocus){manager.mode=AudioManager.MODE_IN_COMMUNICATION;manager.requestAudioFocus(focusRequest)}
        android.util.Log.i("SeraRtc","Audio track ready")
    }
    private suspend fun description(offer:Boolean,restart:Boolean=false):SessionDescription=suspendCancellableCoroutine{c->
        val observer=object:SdpObserver{
            override fun onCreateSuccess(s:SessionDescription){if(c.isActive)c.resume(s)}
            override fun onCreateFailure(message:String){if(c.isActive)c.resumeWithException(IllegalStateException("Could not prepare call."))}
            override fun onSetSuccess(){}
            override fun onSetFailure(message:String){}
        }
        if(offer)pc.createOffer(observer,MediaConstraints().apply{if(restart)mandatory.add(MediaConstraints.KeyValuePair("IceRestart","true"))})else pc.createAnswer(observer,MediaConstraints())
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
    private fun speechDescription(s:SessionDescription)=SessionDescription(s.type,seraSpeechSdp(s.description))
    private fun applySpeechProfile(){
        if(closed||appliedBitrate==speech.profile.bitrate)return
        runCatching{
            val parameters=sender.parameters
            if(parameters.encodings.isNotEmpty()){
                parameters.encodings.forEach{it.maxBitrateBps=speech.profile.bitrate}
                if(sender.setParameters(parameters))appliedBitrate=speech.profile.bitrate
            }
        }
    }
    suspend fun prepareLocalOffer():String=withTimeout(10000){val offer=speechDescription(description(true));set(offer,true);applySpeechProfile();pc.localDescription?.description?:offer.description}
    suspend fun offer():String=withTimeout(35000){prepareLocalOffer();gather()}
    suspend fun answer(sdp:String):String=withTimeout(35000){set(SessionDescription(SessionDescription.Type.OFFER,sdp),false);set(speechDescription(description(false)),true);applySpeechProfile();gather()}
    suspend fun remoteAnswer(sdp:String)=withTimeout(10000){set(SessionDescription(SessionDescription.Type.ANSWER,sdp),false);appliedBitrate=null;applySpeechProfile()}
    private suspend fun connectedThroughRelay():Boolean=kotlinx.coroutines.withTimeoutOrNull(2000){
        suspendCancellableCoroutine<Boolean>{c->
            if(closed){c.resume(false);return@suspendCancellableCoroutine}
            pc.getStats{report->
                val pairId=report.statsMap.values.firstOrNull{it.type=="transport"}?.members?.get("selectedCandidatePairId") as? String
                val pair=pairId?.let{report.statsMap[it]}
                val local=(pair?.members?.get("localCandidateId") as? String)?.let{report.statsMap[it]}
                val remote=(pair?.members?.get("remoteCandidateId") as? String)?.let{report.statsMap[it]}
                val ok=pc.connectionState()==PeerConnection.PeerConnectionState.CONNECTED&&pair?.members?.get("state")=="succeeded"&&local?.members?.get("candidateType")=="relay"&&remote?.members?.get("candidateType")=="relay"
                if(c.isActive)c.resume(ok)
            }
        }
    }?:false
    fun signalingStable()=!closed&&pc.signalingState()==PeerConnection.SignalingState.STABLE
    suspend fun restartOffer():String=withTimeout(16000){
        val previous=pc.localDescription?.description.orEmpty()
        val offer=speechDescription(description(true,true))
        fun ufrag(s:String)=s.lineSequence().firstOrNull{it.startsWith("a=ice-ufrag:")}?.trim()
        check(ufrag(offer.description)!=null&&ufrag(offer.description)!=ufrag(previous)){"ICE restart did not rotate credentials"}
        set(offer,true);gather(true)
    }
    suspend fun restartAnswer(sdp:String):String=withTimeout(16000){
        set(SessionDescription(SessionDescription.Type.OFFER,sdp),false)
        set(speechDescription(description(false)),true);gather(true)
    }
    private suspend fun gather(restart:Boolean=false):String{
        val deadline=android.os.SystemClock.elapsedRealtime()+(if(restart)12000L else 25000L)
        while(!closed&&android.os.SystemClock.elapsedRealtime()<deadline){
            // Non-trickle signaling can proceed as soon as a valid relay candidate exists.
            val current=pc.localDescription?.description.orEmpty()
            val hasRelay=current.lineSequence().any{it.startsWith("a=candidate:")&&it.contains(" typ relay ")}
            if(hasRelay&&(!restart||pc.iceGatheringState()==PeerConnection.IceGatheringState.COMPLETE))break
            if(!restart&&pc.iceGatheringState()==PeerConnection.IceGatheringState.COMPLETE)break
            delay(100)
        }
        if(closed)throw kotlinx.coroutines.CancellationException("Call closed")
        val sdp=pc.localDescription?.description?:error("Call connection unavailable.")
        if(!sdp.lineSequence().any{it.startsWith("a=candidate:")&&it.contains(" typ relay ")})throw SeraRelayUnavailable()
        return sdp
    }
    private data class SpeechStats(val sample:SeraAudioSample?,val link:SeraSpeechLink,val remoteTimestamp:Double?)
    suspend fun audioSample():SeraAudioSample? {
        val stats=kotlinx.coroutines.withTimeoutOrNull(2500){
            suspendCancellableCoroutine<SpeechStats?>{continuation->
                if(closed){continuation.resume(null);return@suspendCancellableCoroutine}
                pc.getStats{report->
                    val values=report.statsMap.values
                    fun isAudio(stat:RTCStats)=stat.members["kind"]=="audio"||stat.members["mediaType"]=="audio"
                    val samples=values.filter{it.type=="inbound-rtp"&&isAudio(it)}.mapNotNull{stat->
                        val received=(stat.members["packetsReceived"] as? Number)?.toLong()
                        val lost=(stat.members["packetsLost"] as? Number)?.toLong()
                        if(received!=null&&lost!=null)SeraAudioSample(received,lost,(stat.members["jitter"] as? Number)?.toDouble())else null
                    }
                    val sample=if(samples.isEmpty())null else SeraAudioSample(samples.sumOf{it.received},samples.sumOf{it.lost},samples.mapNotNull{it.jitterSeconds}.maxOrNull())
                    val remote=values.firstOrNull{it.type=="remote-inbound-rtp"&&isAudio(it)}
                    val selectedId=values.firstOrNull{it.type=="transport"}?.members?.get("selectedCandidatePairId") as? String
                    val pair=selectedId?.let{report.statsMap[it]}
                    val loss=(remote?.members?.get("fractionLost") as? Number)?.toDouble()
                    val rtt=(remote?.members?.get("roundTripTime") as? Number)?.toDouble()?: (pair?.members?.get("currentRoundTripTime") as? Number)?.toDouble()
                    val bandwidth=(pair?.members?.get("availableOutgoingBitrate") as? Number)?.toDouble()
                    if(continuation.isActive)continuation.resume(SpeechStats(sample?.copy(rttSeconds=rtt),SeraSpeechLink(loss,rtt,bandwidth),remote?.timestampUs))
                }
            }
        }?:return null
        if(!closed){
            val fresh=stats.remoteTimestamp!=null&&stats.remoteTimestamp!=lastRemoteReport
            if(fresh)lastRemoteReport=stats.remoteTimestamp
            speech.update(if(fresh)stats.link else stats.link.copy(loss=null))
            applySpeechProfile()
        }
        return stats.sample
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
        if(manageAudioFocus)runCatching{manager.abandonAudioFocusRequest(focusRequest);if(Build.VERSION.SDK_INT>=31)manager.clearCommunicationDevice()else @Suppress("DEPRECATION") run{manager.isSpeakerphoneOn=previousSpeaker};manager.mode=previousMode}
    }
}
