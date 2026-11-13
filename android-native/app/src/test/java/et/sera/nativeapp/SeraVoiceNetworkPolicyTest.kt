package et.sera.nativeapp

import org.junit.Assert.*
import org.junit.Test

class SeraVoiceNetworkPolicyTest {
    @Test fun adaptsQuicklyDownAndSlowlyUp(){
        val p=SeraSpeechAdaptation()
        assertEquals(SeraSpeechProfile.BALANCED,p.profile)
        assertEquals(SeraSpeechProfile.RESILIENT,p.update(SeraSpeechLink(loss=.15)))
        repeat(2){assertEquals(SeraSpeechProfile.RESILIENT,p.update(SeraSpeechLink(.01,.15,120000.0)))}
        assertEquals(SeraSpeechProfile.BALANCED,p.update(SeraSpeechLink(.01,.15,120000.0)))
        repeat(3){p.update(SeraSpeechLink(.01,.15,120000.0))}
        assertEquals(SeraSpeechProfile.CLEAR,p.profile)
        assertEquals(SeraSpeechProfile.BALANCED,p.update(SeraSpeechLink(loss=.06)))
        assertEquals(SeraSpeechProfile.RESILIENT,p.update(SeraSpeechLink(outgoingBps=35000.0)))
    }
    @Test fun missingOrInvalidMeasurementsDoNotClaimHealthyNetwork(){
        val p=SeraSpeechAdaptation()
        p.update(SeraSpeechLink(loss=.2))
        repeat(8){p.update(SeraSpeechLink(Double.NaN,-1.0,0.0))}
        assertEquals(SeraSpeechProfile.RESILIENT,p.profile)
    }
    @Test fun opusTuningPreservesTransportAndOtherCodecs(){
        val sdp="v=0\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111 0\r\na=rtpmap:111 opus/48000/2\r\na=fmtp:111 minptime=10;useinbandfec=0;stereo=1\r\na=rtpmap:0 PCMU/8000\r\na=ice-ufrag:abc\r\nm=video 9 UDP/TLS/RTP/SAVPF 96\r\na=rtpmap:96 VP8/90000\r\n"
        val result=seraSpeechSdp(sdp)
        assertTrue(result.contains("minptime=10;useinbandfec=1;stereo=0"))
        assertTrue(result.contains("maxaveragebitrate=32000"))
        assertTrue(result.contains("a=rtpmap:0 PCMU/8000\r\na=ice-ufrag:abc"))
        assertTrue(result.contains("m=video 9 UDP/TLS/RTP/SAVPF 96\r\na=rtpmap:96 VP8/90000"))
        assertEquals(result,seraSpeechSdp(result))
    }
    @Test fun absentOpusLeavesSdpUnchanged(){
        val sdp="v=0\r\nm=audio 9 RTP/AVP 0\r\na=rtpmap:0 PCMU/8000\r\n"
        assertEquals(sdp,seraSpeechSdp(sdp))
    }
}
