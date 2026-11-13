package et.sera.nativeapp

/** Encoder payload limits, not total network data (TURN/UDP/TLS overhead is additional). */
internal enum class SeraSpeechProfile(val bitrate:Int){ CLEAR(32000), BALANCED(20000), RESILIENT(12000) }
internal data class SeraSpeechLink(val loss:Double?=null,val rttSeconds:Double?=null,val outgoingBps:Double?=null)
internal class SeraSpeechAdaptation {
    var profile=SeraSpeechProfile.BALANCED
        private set
    private var good=0
    fun update(link:SeraSpeechLink):SeraSpeechProfile {
        val loss=link.loss?.takeIf{it.isFinite()&&it in 0.0..1.0}
        val rtt=link.rttSeconds?.takeIf{it.isFinite()&&it>=0}
        val bandwidth=link.outgoingBps?.takeIf{it.isFinite()&&it>0}
        val severe=(loss!=null&&loss>=.12)||(rtt!=null&&rtt>=.8)||(bandwidth!=null&&bandwidth<40000)
        val stressed=(loss!=null&&loss>=.05)||(rtt!=null&&rtt>=.4)||(bandwidth!=null&&bandwidth<75000)
        val healthy=loss!=null&&loss<.025&&rtt!=null&&rtt<.3&&(bandwidth==null||bandwidth>=90000)
        when {
            severe->{profile=SeraSpeechProfile.RESILIENT;good=0}
            stressed->{if(profile==SeraSpeechProfile.CLEAR)profile=SeraSpeechProfile.BALANCED;good=0}
            healthy->{good++;if(good>=3){profile=when(profile){SeraSpeechProfile.RESILIENT->SeraSpeechProfile.BALANCED;SeraSpeechProfile.BALANCED->SeraSpeechProfile.CLEAR;else->profile};good=0}}
            else->good=0
        }
        return profile
    }
}

/** Preserve every other SDP attribute and payload. Opus RTP clock/channel mapping stays unchanged. */
internal fun seraSpeechSdp(sdp:String):String {
    val lines=sdp.split("\n").toMutableList()
    val opus=Regex("^a=rtpmap:(\\d+) opus/48000/2\\r?$",RegexOption.IGNORE_CASE)
    val settings=linkedMapOf("stereo" to "0","sprop-stereo" to "0","useinbandfec" to "1","maxaveragebitrate" to "32000")
    var i=0
    while(i<lines.size){
        if(!lines[i].startsWith("m=audio ")){i++;continue}
        var end=i+1
        while(end<lines.size&&!lines[end].startsWith("m="))end++
        val ids=(i+1 until end).mapNotNull{opus.matchEntire(lines[it])?.groupValues?.get(1)}
        for(id in ids){
            val prefix="a=fmtp:$id "
            val index=(i+1 until end).firstOrNull{lines[it].startsWith(prefix)}
            val params=linkedMapOf<String,String>()
            if(index!=null)lines[index].removePrefix(prefix).trimEnd('\r').split(';').forEach{val parts=it.trim().split('=',limit=2);if(parts.size==2)params[parts[0]]=parts[1]}
            params.putAll(settings)
            val cr=if(lines[i].endsWith("\r"))"\r" else ""
            val line=prefix+params.entries.joinToString(";"){it.key+"="+it.value}+cr
            if(index!=null)lines[index]=line else {lines.add(end-(if(end==lines.size&&lines.last().isEmpty())1 else 0),line);end++}
        }
        i=end
    }
    return lines.joinToString("\n")
}
