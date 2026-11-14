package et.sera.nativeapp

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class SeraMemberDetailTest {
    private val id="11111111-1111-1111-1111-111111111111"
    @Test fun memberWithoutPostsLoadsFromMemberDirectory(){
        val result=loadSeraMemberDetail("test",id){rpc,body->
            assertEquals("member_cards",rpc)
            assertEquals(id,body.getJSONArray("ids").getString(0))
            JSONArray().put(JSONObject().put("id",id).put("full_name","Network member")).toString() to ""
        }
        assertEquals("Network member",result.member?.title)
        assertEquals("",result.error)
    }
    @Test fun missingAndTemporaryFailureAreDistinct(){
        val missing=loadSeraMemberDetail("test",id){_,_->"[]" to ""}
        val failed=loadSeraMemberDetail("test",id){_,_->null to "Connection unavailable."}
        assertNull(missing.member);assertEquals("",missing.error)
        assertNull(failed.member);assertTrue(failed.error.isNotBlank())
    }
    @Test fun neverDisplayAnotherMemberFromUnexpectedResponse(){
        val result=loadSeraMemberDetail("test",id){_,_->"""[{"id":"22222222-2222-2222-2222-222222222222","full_name":"Other"}]""" to ""}
        assertNull(result.member)
    }
}
