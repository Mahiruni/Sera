package et.sera.nativeapp

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.isActive
import java.util.concurrent.TimeUnit

/** Authenticated Postgres change subscription; REST remains the source of truth. */
internal class SeraRealtime(private val token: String, private val conversationId: String) {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).pingInterval(20, TimeUnit.SECONDS).build()
    private val signals = Channel<Unit>(Channel.CONFLATED)
    private val hints=SeraMessageHints()
    private var socket: WebSocket? = null
    private val reference = java.util.concurrent.atomic.AtomicInteger(0)
    private val topic = "realtime:sera-messages-" + conversationId
    private fun frame(topic: String, event: String, payload: JSONObject): String =
        JSONObject().put("topic",topic).put("event",event).put("payload",payload).put("ref", reference.incrementAndGet().toString()).toString()
    suspend fun run(onSync: suspend (SeraMessageSyncHint) -> Unit) = coroutineScope {
        val endpoint = BuildConfig.SUPABASE_URL.trimEnd('/').replaceFirst("https://","wss://")
        require(endpoint.startsWith("wss://"))
        try {
            var attempt=0
            while (isActive) {
                val joined=java.util.concurrent.atomic.AtomicBoolean(false)
                hints.add(SeraMessageSyncHint.Full)
                val disconnected = java.util.concurrent.atomic.AtomicBoolean(false)
                val request = Request.Builder().url(endpoint + "/realtime/v1/websocket?apikey=" + java.net.URLEncoder.encode(BuildConfig.SUPABASE_ANON_KEY,"UTF-8") + "&vsn=1.0.0").build()
                socket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        val change = JSONObject().put("event","*").put("schema","public").put("table","professional_messages").put("filter","conversation_id=eq." + conversationId)
                        val config = JSONObject().put("broadcast",JSONObject().put("self",false)).put("presence",JSONObject().put("key","")).put("postgres_changes",JSONArray().put(change).put(JSONObject().put("event","*").put("schema","public").put("table","professional_message_reactions").put("filter","conversation_id=eq."+conversationId)))
                        webSocket.send(frame(topic,"phx_join",JSONObject().put("config",config).put("access_token",token)))
                    }
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val data = runCatching { JSONObject(text) }.getOrNull() ?: return
                        when(data.optString("event")) {
                            "postgres_changes" -> if(data.optString("topic")==topic){hints.add(seraMessageHint(data.optJSONObject("payload")?:JSONObject()));signals.trySend(Unit)}
                            "phx_reply" -> if(data.optString("topic")==topic){if(data.optJSONObject("payload")?.optString("status")=="ok"){joined.set(true);hints.add(SeraMessageSyncHint.Full);signals.trySend(Unit)}else{disconnected.set(true);webSocket.cancel();signals.trySend(Unit)}}
                            "phx_error", "phx_close" -> { disconnected.set(true); webSocket.cancel(); signals.trySend(Unit) }
                        }
                    }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { disconnected.set(true); signals.trySend(Unit) }
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { disconnected.set(true); signals.trySend(Unit) }
                })
                val heartbeat = launch { while (isActive) { delay(25000); socket?.send(frame("phoenix","heartbeat",JSONObject())) } }
                try {
                    onSync(SeraMessageSyncHint.Full)
                    var lastFull=android.os.SystemClock.elapsedRealtime()
                    while (isActive && !disconnected.get()) {
                        val fallback=seraMessageFallbackInterval(joined.get())
                        val remaining=(fallback-(android.os.SystemClock.elapsedRealtime()-lastFull)).coerceAtLeast(1L)
                        val signal=withTimeoutOrNull(remaining) { signals.receive() }
                        if(!disconnected.get()){
                            if(signal!=null)delay(180)
                            while(signals.tryReceive().isSuccess){}
                            val pending=hints.drain()
                            val hint=if(signal==null||android.os.SystemClock.elapsedRealtime()-lastFull>=fallback)SeraMessageSyncHint.Full else pending
                            onSync(hint)
                            if(hint==SeraMessageSyncHint.Full)lastFull=android.os.SystemClock.elapsedRealtime()
                            if(signal==null&&!joined.get()){disconnected.set(true);socket?.cancel()}
                        }
                    }
                } finally { heartbeat.cancel(); socket?.cancel() }
                if(joined.get())attempt=0
                delay(seraReconnectDelay(attempt,kotlin.random.Random.nextLong(0,1001)))
                attempt=(attempt+1).coerceAtMost(5)
            }
        } finally { socket?.cancel(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }
}
