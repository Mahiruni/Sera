package et.sera.nativeapp
import org.junit.Assert.*
import org.junit.Test
class SeraNetworkIntelligenceTest {
 @Test fun connectivityIsNotConfusedWithBandwidth(){
   assertFalse(seraLinkState(false,false,true,0,0,0).available)
   assertTrue(seraLinkState(true,false,false,10000,10000,2).constrained)
   assertTrue(seraLinkState(true,true,true,10000,10000,2).constrained)
   assertTrue(seraLinkState(true,true,false,300,300,2).constrained)
   assertFalse(seraLinkState(true,true,false,0,0,2).constrained)
   assertFalse(seraLinkState(true,true,false,10000,10000,2).constrained)
 }
 @Test fun secondaryTrafficSlowsButDoesNotChangeVoice(){
   assertEquals(15000L,seraSecondarySyncDelay(SeraLinkState(),false,15000L))
   assertEquals(30000L,seraSecondarySyncDelay(SeraLinkState(constrained=true),false,15000L))
   assertEquals(60000L,seraSecondarySyncDelay(SeraLinkState(constrained=true),true,15000L))
 }
}
