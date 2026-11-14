package et.sera.nativeapp
/** Short-lived signed URL reuse. Never persist a bearer URL to the new cache. */
internal object SeraAvatarMemory {
    private data class Entry(val url:String,val at:Long)
    private val entries=object:LinkedHashMap<String,Entry>(32,.75f,true){}
    @Synchronized fun get(token:String,path:String):String? {
        val k=token+"|"+path
        return entries[k]?.takeIf{android.os.SystemClock.elapsedRealtime()-it.at in 0L..60000L}?.url.also{if(it==null)entries.remove(k)}
    }
    @Synchronized fun put(token:String,path:String,url:String){
        entries[token+"|"+path]=Entry(url,android.os.SystemClock.elapsedRealtime())
        while(entries.size>100)entries.remove(entries.keys.first())
    }
    @Synchronized fun clear(){entries.clear()}
}
