package et.sera.nativeapp
import org.junit.Assert.*
import org.junit.Test
class UnreadTimelineTest{
    private fun msg(id:String,sender:String,at:String,read:String="")=NativeMessage(id,sender,"hello",read,at,"",emptyList())
    @Test fun unreadBoundaryIgnoresOwnAndReadMessages(){
        val first=msg("a","me","2026-11-13T07:00:00Z")
        val second=msg("b","peer","2026-11-13T07:01:00Z","2026-11-13T07:02:00Z")
        val third=msg("c","peer","2026-11-13T07:03:00Z")
        val rows=listOf(first,second,third)
        assertEquals("c",rows.first{isUnreadIncoming(it,"me")}.id)
        assertEquals(2,unreadTimelineIndex(rows,emptyList(),"c",100))
        assertEquals(3,unreadTimelineIndex(rows,emptyList(),"c",3))
    }
}
