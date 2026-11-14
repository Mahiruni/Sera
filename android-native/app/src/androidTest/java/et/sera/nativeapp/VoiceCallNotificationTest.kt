package et.sera.nativeapp

import android.app.Notification
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class VoiceCallNotificationTest {
    private val id="f0caa111-0000-4000-8000-000000000005"
    private val recipient="f0caa111-0000-4000-8000-000000000002"
    @Test fun ringWindowNeverExceedsServerDeadlineOr45Seconds(){
        val now=Instant.parse("2026-11-13T00:00:00Z").toEpochMilli()
        assertEquals(20000L,callRingWindowMillis("2026-11-13T00:00:20Z",now))
        assertEquals(45000L,callRingWindowMillis("2026-11-13T00:10:00Z",now))
        assertEquals(0L,callRingWindowMillis("2026-11-12T23:59:59Z",now))
        assertEquals(0L,callRingWindowMillis("not a date",now))
        assertEquals(0L,callRingWindowMillis(null,now))
    }
    @Test fun notificationAnswerGrantIsOneUseAndAccountBound(){
        SeraNotificationAnswer.issue(id,recipient)
        assertFalse(SeraNotificationAnswer.take(id,"00000000-0000-4000-8000-000000000001"))
        assertFalse(SeraNotificationAnswer.take(id,recipient))
        SeraNotificationAnswer.issue(id,recipient)
        assertTrue(SeraNotificationAnswer.take(id,recipient))
        assertFalse(SeraNotificationAnswer.take(id,recipient))
    }
    @Test fun namedCallKeepsPrivatePublicVersion(){val context=InstrumentationRegistry.getInstrumentation().targetContext;val preview=SeraNotificationPreview("Test caller","",recipient,mode="sender");val notification=SeraPush.buildCallNotification(context,id,recipient,21000L,preview);assertEquals("Test caller",notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString());assertNotNull(notification.publicVersion);assertEquals(Notification.VISIBILITY_PRIVATE,notification.visibility)}
    @Test fun incomingCallNotificationHasPrivateBoundedRingAndSilence(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val notification=SeraPush.buildCallNotification(context,id,recipient,21000L)
        assertEquals(Notification.CATEGORY_CALL,notification.category)
        assertEquals(Notification.VISIBILITY_PRIVATE,notification.visibility)
        assertEquals(21000L,notification.timeoutAfter)
        assertTrue(notification.flags and Notification.FLAG_INSISTENT != 0)
        assertNotNull(notification.publicVersion)
        assertNotNull(notification.contentIntent)
        assertNotNull(notification.deleteIntent)
        assertTrue(notification.actions.size>=2)
        assertTrue(notification.actions.any{it.title.toString().contains("Answer",ignoreCase=true)})
        assertTrue(notification.actions.any{it.title.toString().contains("Decline",ignoreCase=true)})
    }
}
