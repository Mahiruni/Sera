package et.sera.nativeapp

import java.net.HttpURLConnection
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Cancellation can win only before the message commit starts. */
internal class NativeChatTransfer {
    private val state=AtomicInteger(0)
    private val connection=AtomicReference<HttpURLConnection?>(null)
    val cancelled:Boolean get()=state.get()==1
    fun requestCancel():Boolean=state.compareAndSet(0,1)
    fun disconnect(){connection.get()?.let{runCatching{it.disconnect()}}}
    fun bind(c:HttpURLConnection){connection.set(c);checkActive()}
    fun release(c:HttpURLConnection){connection.compareAndSet(c,null)}
    fun checkActive(){check(!cancelled){"Upload cancelled."}}
    fun beginCommit(){check(state.compareAndSet(0,2)){"Upload cancelled."}}
}
