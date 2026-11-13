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
    private var socket: WebSocket? = null
    private val reference = java.util.concurrent.atomic.AtomicInteger(0)
    private val topic = "realtime:sera-messages-" + conversationId
    private fun frame(topic: String, event: String, payload: JSONObject): String =
        JSONObject().put("topic",topic).put("event",event).put("payload",payload).put("ref", reference.incrementAndGet().toString()).toString()
    suspend fun run(onSync: suspend () -> Unit) = coroutineScope {
        val endpoint = BuildConfig.SUPABASE_URL.trimEnd('/').replaceFirst("https://","wss://")
        require(endpoint.startsWith("wss://"))
        try {
            while (isActive) {
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
                            "postgres_changes", "phx_reply" -> signals.trySend(Unit)
                            "phx_error", "phx_close" -> { disconnected.set(true); webSocket.cancel(); signals.trySend(Unit) }
                        }
                    }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { disconnected.set(true); signals.trySend(Unit) }
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { disconnected.set(true); signals.trySend(Unit) }
                })
                val heartbeat = launch { while (isActive) { delay(25000); socket?.send(frame("phoenix","heartbeat",JSONObject())) } }
                try {
                    onSync()
                    while (isActive && !disconnected.get()) {
                        withTimeoutOrNull(15000) { signals.receive() }
                        onSync()
                    }
                } finally { heartbeat.cancel(); socket?.cancel() }
                delay(2000)
            }
        } finally { socket?.cancel(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }
}
