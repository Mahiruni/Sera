package et.sera.nativeapp
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class SeraDirectoryCacheTest{
 private val owner="00000000-0000-4000-8000-000000000101"
 private val other="00000000-0000-4000-8000-000000000102"
 @Test fun scopeAndSensitiveFields(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val a=SeraDirectoryCache(context,owner);val b=SeraDirectoryCache(context,other)
  try{
   a.writeNetwork(true,listOf(SeraProfessional("member","Name","Engineer","",avatarPath="private/avatar",identityVerified=true)),SeraNetworkRelations(emptySet(),emptyList()),a.generation())
   val member=a.readNetwork(true)?.first?.single()
   assertEquals("Name",member?.name);assertNull(member?.avatarPath);assertFalse(member?.identityVerified?:true)
   assertNull(b.readNetwork(true))
   a.writeExplore("events",listOf(JSONObject().put("id","event").put("title","Event").put("owner_id","secret").put("going",true)),a.generation())
   val event=a.readExplore("events")!!.single();assertEquals("Event",event.optString("title"));assertFalse(event.has("owner_id"));assertFalse(event.has("going"))
   val stale=a.generation();a.clear("explore");a.writeExplore("events",listOf(event),stale);assertNull(a.readExplore("events"))
   a.writeNotifications("All",1,listOf(JSONObject().put("id","n").put("kind","like").put("body","private")),mapOf("member" to "Name"),emptyMap(),false,a.generation())
   assertFalse(a.readNotifications("All",1)!!.getJSONArray("rows").getJSONObject(0).has("body"))
   assertNull(b.readNotifications("All",1))
  }finally{a.clear("network");a.clear("explore");a.clear("notifications");b.clear("network")}
 }
}
