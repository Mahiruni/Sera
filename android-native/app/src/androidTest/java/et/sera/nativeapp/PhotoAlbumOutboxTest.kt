package et.sera.nativeapp

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class PhotoAlbumOutboxTest {
    @Test fun albumFilesAndOrderSurviveEncryptedOutboxRoundTrip(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val user=UUID.randomUUID().toString();val thread=UUID.randomUUID().toString();val id=UUID.randomUUID().toString()
        val box=NativeChatOutbox(context)
        val first=NativeChatSelection("first.jpg","image/jpeg",byteArrayOf(-1,-40,-1,1))
        val second=NativeChatSelection("second.png","image/png",byteArrayOf(-119,80,78,71,13,10,26,10))
        try{
            box.save(user,thread,NativePending(id,"Photo caption",null,null,listOf(first,second)))
            val loaded=NativeChatOutbox(context).load(user,thread)!!
            assertEquals(id,loaded.clientId)
            assertEquals("Photo caption",loaded.body)
            assertNull(loaded.file)
            assertEquals(listOf("first.jpg","second.png"),loaded.album.map{it.name})
            assertArrayEquals(first.bytes,loaded.album[0].bytes)
            assertArrayEquals(second.bytes,loaded.album[1].bytes)
            assertNotEquals(albumItemId(id,0),albumItemId(id,1))
            assertEquals(albumItemId(id,0),albumItemId(id,0))
            assertFalse(java.io.File(context.filesDir,"sera_chat_pending/$user-${albumItemId(id,0)}.bin").readBytes().contentEquals(first.bytes))
        }finally{box.remove(user,thread,id)}
        assertNull(box.load(user,thread))
        assertFalse(java.io.File(context.filesDir,"sera_chat_pending/$user-${albumItemId(id,0)}.bin").exists())
    }
}
