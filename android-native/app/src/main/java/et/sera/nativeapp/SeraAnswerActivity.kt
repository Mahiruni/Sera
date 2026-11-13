package et.sera.nativeapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock

/** Only our non-exported notification activity can issue a one-use answer grant. */
internal object SeraNotificationAnswer {
    private data class Grant(val id:String,val recipient:String,val deadline:Long)
    private var grant:Grant?=null
    fun issue(id:String,recipient:String){grant=Grant(id,recipient,SystemClock.elapsedRealtime()+45000L)}
    fun take(id:String,recipient:String):Boolean{
        val value=grant?:return false
        grant=null
        return value.id==id&&value.recipient==recipient&&SystemClock.elapsedRealtime()<value.deadline
    }
}
class SeraAnswerActivity:Activity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        val id=intent.getStringExtra("call_id")
        val recipient=intent.getStringExtra("recipient_id")
        if(BuildConfig.LIVE_CALLING_INCLUDED&&id!=null&&recipient!=null&&SeraMessageAlerts.validId(id)&&SeraMessageAlerts.validId(recipient)){
            SeraNotificationAnswer.issue(id,recipient)
            startActivity(Intent(this,MainActivity::class.java).setAction("et.sera.nativeapp.INCOMING_CALL").putExtra("sera_kind","voice_call:"+id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        finish()
    }
}
