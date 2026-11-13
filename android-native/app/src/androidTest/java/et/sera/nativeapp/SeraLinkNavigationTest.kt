package et.sera.nativeapp
import org.junit.Assert.*
import org.junit.Test

class SeraLinkNavigationTest {
    private val id="12345678-1234-1234-1234-123456789abc"
    @Test fun internalContentRoutesToNativeDestinations(){
        for(kind in listOf("members","portfolio","jobs","posts")){
            assertEquals(SeraLinkDestination(kind,id),seraLinkDestination("https://sera.hisabtech.com/$kind/$id"))
        }
    }
    @Test fun legalAndSectionsStayInsideApp(){
        assertEquals("public",seraLinkDestination("https://sera.hisabtech.com/privacy")?.kind)
        assertEquals("public",seraLinkDestination("https://sera.hisabtech.com/terms")?.kind)
        assertEquals(SeraLinkDestination("section","messages"),seraLinkDestination("https://sera.hisabtech.com/network/messages"))
    }
    @Test fun malformedAndSpoofedLinksNeverRouteAsNative(){
        assertNull(seraLinkDestination("javascript:alert(1)"))
        assertNull(seraLinkDestination("https://sera.hisabtech.com@evil.example/posts/$id"))
        assertNull(seraLinkDestination("http://sera.hisabtech.com/posts/$id"))
        assertNull(seraLinkDestination("https://sera.hisabtech.com:444/posts/$id"))
        assertEquals("external",seraLinkDestination("https://sera.hisabtech.com.evil.example/posts/$id")?.kind)
        assertEquals("unsupported",seraLinkDestination("https://sera.hisabtech.com/posts/not-an-id")?.kind)
    }
}
