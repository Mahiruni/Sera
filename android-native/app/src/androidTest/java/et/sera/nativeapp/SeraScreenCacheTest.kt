package et.sera.nativeapp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
class SeraScreenCacheTest {
 private val owner="00000000-0000-4000-8000-000000000091"
 private val other="00000000-0000-4000-8000-000000000092"
 @Test fun jobsAndSelfProfileAreScopedAndCleared(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val store=SeraLocalCache.get(context)
  val a=SeraScreenCache(context,owner);val b=SeraScreenCache(context,other)
  try{
   a.writeJobs(listOf(NativeJob("job","Engineer","Sera","Addis Ababa","Full time","Details","Skills","2026-11-14")),a.generation())
   a.writeProfile(NativeProfileFields("Member","Engineer","Addis Ababa","Summary","Skills"),a.generation())
   assertEquals("Engineer",a.readJobs()?.single()?.title)
   assertEquals("Member",a.readProfile()?.fullName)
   assertNull(b.readJobs());assertNull(b.readProfile())
   val stale=a.generation()
   store.clear(owner)
   a.writeProfile(NativeProfileFields("Late"),stale)
   assertNull(a.readProfile());assertNull(a.readJobs())
  }finally{store.clear(owner);store.clear(other)}
 }
}
