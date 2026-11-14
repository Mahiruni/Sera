package et.sera.nativeapp
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
/** Account-scoped, bounded previews. Mutations require fresh server access. */
internal class SeraScreenCache(context:Context,private val owner:String){
 private val store=SeraLocalCache.get(context.applicationContext)
 fun generation()=store.generation(owner)
 fun readJobs():List<NativeJob>?=runCatching{
  val raw=store.read(owner,"jobs","open")?:return null
  val rows=JSONArray(raw)
  (0 until rows.length()).map{val r=rows.getJSONObject(it);NativeJob(r.getString("id"),r.optString("title"),r.optString("company"),r.optString("location"),r.optString("workType"),r.optString("description"),r.optString("requirements"),r.optString("createdAt"))}
 }.getOrNull()
 fun writeJobs(jobs:List<NativeJob>,generation:Long){
  val rows=JSONArray()
  jobs.take(40).forEach{j->rows.put(JSONObject().put("id",j.id).put("title",j.title.take(300)).put("company",j.company.take(300)).put("location",j.location.take(200)).put("workType",j.workType.take(100)).put("description",j.description.take(4000)).put("requirements",j.requirements.take(2000)).put("createdAt",j.createdAt))}
  store.write(owner,"jobs","open",rows.toString(),System.currentTimeMillis(),600000L,1,generation)
 }
 fun readProfile():NativeProfileFields?=runCatching{
  val raw=store.read(owner,"profile","self")?:return null
  val r=JSONObject(raw);NativeProfileFields(r.optString("name"),r.optString("headline"),r.optString("location"),r.optString("summary"),r.optString("skills"))
 }.getOrNull()
 fun writeProfile(profile:NativeProfileFields,generation:Long){
  val r=JSONObject().put("name",profile.fullName).put("headline",profile.headline).put("location",profile.location).put("summary",profile.summary).put("skills",profile.skills)
  store.write(owner,"profile","self",r.toString(),System.currentTimeMillis(),900000L,1,generation)
 }
 fun clearJobs()=store.clear(owner,"jobs")
 fun clearProfile()=store.clear(owner,"profile")
}
