package et.sera.nativeapp

internal data class SeraCallEvent(val id:String,val caller:String,val state:String,val createdAt:String,val connectedSeconds:Long?=null){
    fun incoming(userId:String)=caller!=userId
    fun outcome(userId:String)=when(state){
        "missed"->if(incoming(userId))"Missed call" else "No answer"
        "declined"->"Call declined"
        "cancelled"->"Call cancelled"
        "failed"->"Connection lost"
        "ended"->"Call ended"
        else->"Voice call"
    }
    fun dateTime()=runCatching{java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,java.text.DateFormat.SHORT).format(java.util.Date(java.time.Instant.parse(createdAt).toEpochMilli()))}.getOrDefault("")
}
