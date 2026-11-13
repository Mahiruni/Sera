package et.sera.nativeapp

import org.junit.Assert.*
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID

class OptimisticTextQueueTest {
    @Test fun serverEchoReplacesLocalAcknowledgement(){
        val p=NativeQueuedText("client","Hello",null,"2026-11-13T07:00:00Z",TextSendStage.Ready)
        val echo=p.acknowledged("server","user")
        assertEquals(listOf(echo),mergeAcknowledgedText(emptyList(),listOf(echo)))
        val confirmed=echo.copy(readAt="2026-11-13T07:01:00Z")
        assertEquals(listOf(confirmed),mergeAcknowledgedText(listOf(confirmed),listOf(echo)))
        assertEquals("client-user-client",messageTimelineKey(confirmed))
    }
    @Test fun encryptedTextQueueRetainsSeveralMessagesAndIndependentRetries(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val store=NativeChatOutbox(context)
        val user=UUID.randomUUID().toString()
        val thread=UUID.randomUUID().toString()
        val p=NativeQueuedText(UUID.randomUUID().toString(),"First",null,"2026-11-13T07:00:00Z",TextSendStage.Ready)
        val q=p.copy(id=UUID.randomUUID().toString(),body="Second")
        try{
            store.saveText(user,thread,p);store.saveText(user,thread,q);store.saveText(user,thread,p)
            assertEquals(listOf(p,q),store.loadTexts(user,thread))
            store.removeText(user,thread,p.id)
            assertEquals(listOf(q),store.loadTexts(user,thread))
        }finally{store.removeText(user,thread,p.id);store.removeText(user,thread,q.id)}
    }
}
