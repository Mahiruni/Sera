package et.sera.nativeapp

/** Local diagnostics: elapsed milliseconds and HTTP status only.
 * Never include request URLs, account identifiers, messages, or credentials. */
internal object SeraPerformance {
    fun record(operation:String,started:Long,status:Int=200){
        val ms=(android.os.SystemClock.elapsedRealtime()-started).coerceAtLeast(0L)
        if(operation!="secondary"||ms>=1000L||status !in 200..299)
            android.util.Log.i("SeraPerf","operation=$operation elapsed_ms=$ms status=$status")
    }
}
