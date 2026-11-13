package et.sera.nativeapp

import org.junit.Assert.*
import org.junit.Test

class MessagingInteractionTest {
    @Test fun swipeIsDirectionalAndBounded(){
        assertEquals(0f,seraReplyDrag(0f,40f,true,48f),.01f)
        assertEquals(-48f,seraReplyDrag(0f,-48f,true,48f),.01f)
        assertEquals(0f,seraReplyDrag(0f,-40f,false,48f),.01f)
        assertEquals(67.2f,seraReplyDrag(0f,1000f,false,48f),.01f)
    }
    @Test fun microphoneLevelIsBounded(){
        assertEquals(0f,seraVoiceLevel(-1),.01f)
        assertEquals(1f,seraVoiceLevel(32767),.01f)
        assertEquals(1f,seraVoiceLevel(100000),.01f)
        assertTrue(seraVoiceLevel(1000)<seraVoiceLevel(10000))
    }
}
