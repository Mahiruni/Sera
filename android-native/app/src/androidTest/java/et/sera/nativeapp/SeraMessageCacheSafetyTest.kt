package et.sera.nativeapp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
class SeraMessageCacheSafetyTest {
 private val owner="00000000-0000-4000-8000-000000000031"
 private val thread="00000000-0000-4000-8000-000000000032"
 @Test fun clearedCacheRejectsLateNetworkWrite(){
  val cache=SeraMessageHistoryCache(InstrumentationRegistry.getInstrumentation().targetContext)
  try{
   val generation=cache.generation(owner)
   cache.clear(owner)
   cache.write(owner,thread,JSONArray().put(JSONObject().put("body","late")).toString(),generation)
   assertNull(cache.read(owner,thread))
  }finally{cache.clear(owner)}
 }
 @Test fun unsentContentIsRemovedFromCache(){
  val cache=SeraMessageHistoryCache(InstrumentationRegistry.getInstrumentation().targetContext)
  try{
   cache.write(owner,thread,JSONArray().put(JSONObject().put("body","removed").put("unsent_at","2026-11-14").put("professional_message_attachments",JSONArray().put("private"))).toString())
   val row=JSONArray(cache.read(owner,thread)).getJSONObject(0)
   assertEquals("Message unsent",row.getString("body"));assertFalse(row.has("professional_message_attachments"))
  }finally{cache.clear(owner)}
 }
}
