package et.sera.nativeapp

import android.Manifest
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

/** Run against the actual signed, minified calling APK. No production credentials or traffic. */
class SeraNativeRtcSmokeTest {
    @Test fun nativeEngineCreatesAudioOfferAndCanRestart() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName,Manifest.permission.RECORD_AUDIO)
        val config=JSONObject().put("iceServers",JSONArray().put(JSONObject()
            .put("urls","turn:127.0.0.1:3478?transport=udp")
            .put("username","smoke-test").put("credential","smoke-test")))
        repeat(2) {
            var engine:SeraNativeRtc?=null
            try {
                instrumentation.runOnMainSync { engine=SeraNativeRtc(context,config){} }
                val sdp=runBlocking { checkNotNull(engine).prepareLocalOffer() }
                assertTrue("Audio SDP must be created by native WebRTC",sdp.contains("m=audio "))
            } finally {
                instrumentation.runOnMainSync { engine?.close() }
            }
        }
    }
}
