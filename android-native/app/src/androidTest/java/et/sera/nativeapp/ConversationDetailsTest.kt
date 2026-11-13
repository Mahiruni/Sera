package et.sera.nativeapp

import org.junit.Assert.*
import org.junit.Test

class ConversationDetailsTest {
    private fun message(id:String,body:String,files:List<NativeChatFile> = emptyList(),unsent:Boolean=false)=
        NativeMessage(id,"sender",body,"","2026-11-13T09:00:00Z","",files,unsent=unsent)

    @Test fun sharedContentIncludesBothParticipantsButExcludesUnsentMessages() {
        val photo=NativeChatFile("photo.jpg","image/jpeg","private/photo.jpg",100)
        val pdf=NativeChatFile("document.pdf","application/pdf","private/document.pdf",100)
        val old=message("old","See https://example.com/report.",listOf(photo))
        val newer=message("new","https://example.org",listOf(pdf)).copy(senderId="other")
        val removed=message("removed","https://hidden.example",listOf(photo),true)
        val items=seraConversationSharedItems(listOf(old,newer,removed))
        assertEquals(listOf("new","new","old","old"),items.map{it.messageId})
        assertEquals(listOf(pdf,photo),items.mapNotNull{it.file})
        assertEquals(listOf("https://example.org","https://example.com/report"),items.mapNotNull{it.link})
        assertEquals(setOf("sender","other"),items.map{it.senderId}.toSet())
    }

    @Test fun unsafeAndCredentialBearingLinksAreNotListed() {
        val items=seraConversationSharedItems(listOf(message("m","javascript:alert(1) https://user:pass@example.com https://example.com.")))
        assertEquals(listOf("https://example.com"),items.mapNotNull{it.link})
    }
}
