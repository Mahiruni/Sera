package et.sera.nativeapp
import org.junit.Assert.*
import org.junit.Test
class MessageNotificationPolicyTest{
    @Test fun viewportReadThreshold(){
        assertFalse(isReceiptVisible(90,100,0,100))
        assertTrue(isReceiptVisible(50,100,0,100))
        assertTrue(isReceiptVisible(-100,300,0,100))
        assertFalse(isReceiptVisible(0,0,0,100))
    }
    @Test fun deepLinksCarryAccountAndConversation(){
        val event=SeraMessageBannerEvent("owner","thread","message","message")
        assertEquals("message:thread:owner",event.destination())
        assertEquals("company_message",event.copy(kind="company_message").destination())
        assertFalse(SeraMessageAlerts.validId("../admin"))
    }
}
