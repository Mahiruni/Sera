package et.sera.nativeapp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class SeraLocalCacheTest {
 private val a="00000000-0000-4000-8000-000000000081"
 private val b="00000000-0000-4000-8000-000000000082"
 @Test fun accountScopeExpiryAndClear(){
  val store=SeraLocalCache.get(InstrumentationRegistry.getInstrumentation().targetContext)
  val now=System.currentTimeMillis()
  try{
   assertTrue(store.write(a,"test","one","private value",now,10000,2))
   assertEquals("private value",store.read(a,"test","one",now))
   assertNull(store.read(b,"test","one",now))
   assertTrue(store.bytes(a)>0)
   assertNull(store.read(a,"test","one",now+10001))
   store.write(a,"test","one","new",now,10000,2)
   store.clear(a,"test")
   assertNull(store.read(a,"test","one",now))
  }finally{store.clear(a);store.clear(b)}
 }
 @Test fun bucketLimitEvictsOlderEntries(){
  val store=SeraLocalCache.get(InstrumentationRegistry.getInstrumentation().targetContext)
  val now=System.currentTimeMillis()
  try{
   for(i in 1..3)assertTrue(store.write(a,"test",i.toString(),"value",now+i,60000,2))
   assertNull(store.read(a,"test","1",now+5))
   assertEquals("value",store.read(a,"test","3",now+5))
  }finally{store.clear(a)}
 }
}
