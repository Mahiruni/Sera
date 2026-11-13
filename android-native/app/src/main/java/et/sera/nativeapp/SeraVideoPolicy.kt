package et.sera.nativeapp

internal enum class SeraVideoLevel(val width:Int,val height:Int,val fps:Int,val bitrate:Int,val label:String){
    PAUSED(0,0,0,0,"Video paused · audio continues"),
    DATA_SAVER(640,360,15,220000,"Data Saver · 360p"),
    STANDARD(854,480,24,500000,"Standard · 480p"),
    HD(1280,720,24,1200000,"HD · 720p"),
    SUPER_HD(1920,1080,30,2500000,"Super HD · 1080p")
}
internal fun seraVideoCap(value:String)=when(value){"hd"->SeraVideoLevel.HD;"super_hd"->SeraVideoLevel.SUPER_HD;else->SeraVideoLevel.STANDARD}
internal class SeraVideoAdaptation{
    var level=SeraVideoLevel.DATA_SAVER
        private set
    private var good=0
    fun update(cap:SeraVideoLevel,link:SeraSpeechLink,saver:Boolean=false):SeraVideoLevel{
        val max=if(saver)SeraVideoLevel.DATA_SAVER.ordinal else cap.ordinal
        val b=link.outgoingBps?.takeIf{it.isFinite()&&it>=0}
        val l=link.loss?.takeIf{it.isFinite()&&it in 0.0..1.0}
        val r=link.rttSeconds?.takeIf{it.isFinite()&&it>=0}
        val severe=(b!=null&&b<140000)||(l!=null&&l>=.15)||(r!=null&&r>=1)
        val poor=(b!=null&&b<350000)||(l!=null&&l>=.07)||(r!=null&&r>=.6)
        var n=minOf(level.ordinal,max)
        if(severe){n=0;good=0}
        else if(poor){n=minOf(n,1);good=0}
        else if(b!=null&&l!=null&&r!=null&&l<.035&&r<.35){
            val next=minOf(n+1,max)
            if(b>SeraVideoLevel.entries[next].bitrate*1.35+80000){if(++good>=3){n=next;good=0}}else good=0
        }else good=0
        level=SeraVideoLevel.entries[n];return level
    }
}
