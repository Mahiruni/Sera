package et.sera.nativeapp
import org.junit.Assert.*
import org.junit.Test
class MessageNotificationPolicyTest{
    @Test fun avatarFallbackIsSmallAndUsable(){val image=SeraNotificationAvatars.initials("Sera Member");assertEquals(128,image.width);assertEquals(128,image.height)}
    @Test fun communicationPushRoutesAreAccountBound(){val owner="11111111-1111-4111-8111-111111111111";val target="22222222-2222-4222-8222-222222222222";assertEquals("space_message:$target:$owner",SeraActivityPush.destination("space_message",owner,target));assertEquals("message:$target:$owner",SeraActivityPush.destination("missed_call",owner,target));assertNull(SeraActivityPush.destination("space_message",owner,"../admin"));assertNull(SeraActivityPush.destination("other",owner,target))}
    @Test fun activityOpenCarriesExactEventAndAccount(){
        val owner="11111111-1111-4111-8111-111111111111";val target="22222222-2222-4222-8222-222222222222";val event="33333333-3333-4333-8333-333333333333"
        val raw=SeraActivityPush.openDestination("missed_call",owner,target,event)!!
        assertEquals(SeraActivityPush.OpenEvent("missed_call",owner,target,event),SeraActivityPush.parseOpen(raw))
        assertNull(SeraActivityPush.parseOpen(raw+":extra"));assertNull(SeraActivityPush.openDestination("space_message",owner,target,"invalid"))
        val intent=android.content.Intent().setData(android.net.Uri.parse("sera://activity/$owner/missed_call/$target/$event")).putExtra("sera_kind",raw)
        assertEquals(raw,SeraActivityPush.launchDestination(intent))
        intent.setData(android.net.Uri.parse("sera://activity/$owner/missed_call/$target/invalid"))
        assertNull(SeraActivityPush.launchDestination(intent))
    }
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
