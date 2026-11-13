package et.sera.nativeapp

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SeraRelayRecoveryTest {
    private fun config(vararg urls:String)=JSONObject().put("enabled",true).put("iceServers",JSONArray(urls.map{JSONObject().put("urls",it).put("username","test-user").put("credential","test-secret")}))
    @Test fun fallbackPrefersTls443AndRetainsSameCredentials(){
        val udp="turn:turn.cloudflare.com:3478?transport=udp"
        val tls="turns:turn.cloudflare.com:5349?transport=tcp"
        val tls443="turns:turn.cloudflare.com:443?transport=tcp"
        val attempts=seraRelayAttempts(config(udp,tls,tls443))
        assertEquals(2,attempts.size)
        assertEquals(3,attempts.first().config.getJSONArray("iceServers").length())
        val fallback=attempts.last().config.getJSONArray("iceServers")
        assertEquals(2,fallback.length())
        assertEquals(tls443,fallback.getJSONObject(0).getString("urls"))
        assertEquals("test-user",fallback.getJSONObject(0).getString("username"))
        assertEquals("test-secret",fallback.getJSONObject(0).getString("credential"))
        assertEquals(32000L,attempts.sumOf{it.budgetMs})
    }
    @Test fun neverUsesStunOrInventsCredentials(){
        assertTrue(seraRelayAttempts(config("stun:stun.cloudflare.com:3478")).isEmpty())
        assertTrue(seraRelayAttempts(JSONObject()).isEmpty())
        val udp="turn:turn.cloudflare.com:3478?transport=udp"
        val attempts=seraRelayAttempts(config(udp))
        assertEquals(2,attempts.size)
        assertEquals(udp,attempts.last().config.getJSONArray("iceServers").getJSONObject(0).getString("urls"))
    }
}
