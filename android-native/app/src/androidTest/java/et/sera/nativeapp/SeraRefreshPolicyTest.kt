package et.sera.nativeapp
import org.junit.Assert.assertEquals
import org.junit.Test
class SeraRefreshPolicyTest {
 @Test fun reconnectBackoffIsBounded(){
  assertEquals(2000L,seraReconnectDelay(0))
  assertEquals(8000L,seraReconnectDelay(2))
  assertEquals(61000L,seraReconnectDelay(99,2000))
 }
 @Test fun healthyRealtimeAndVoiceUseConservativeIntervals(){
  assertEquals(30000L,seraMessageFallbackInterval(true))
  assertEquals(15000L,seraMessageFallbackInterval(false))
  assertEquals(120000L,seraFeedProbeInterval(true))
 }
}
