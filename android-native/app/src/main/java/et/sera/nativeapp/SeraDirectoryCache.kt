package et.sera.nativeapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal fun seraCacheDenied(error:String)=error.contains("HTTP 401")||error.contains("HTTP 403")||error.contains("Permission denied",true)||error.contains("session expired",true)

internal class SeraDirectoryCache(context:Context,private val owner:String){
 private val store=SeraLocalCache.get(context.applicationContext)
 fun generation()=store.generation(owner)
 private fun read(bucket:String,key:String):JSONObject?=runCatching{store.read(owner,bucket,key)?.let{JSONObject(it)}}.getOrNull()
 private fun write(bucket:String,key:String,value:JSONObject,generation:Long,ttl:Long,max:Int){runCatching{store.write(owner,bucket,key,value.toString(),System.currentTimeMillis(),ttl,max,generation)}}
 fun clear(bucket:String){runCatching{store.clear(owner,bucket)}}
 private fun objects(a:JSONArray?)=(0 until (a?.length()?:0)).mapNotNull{a?.optJSONObject(it)}
 private fun strings(a:JSONArray?)=(0 until (a?.length()?:0)).mapNotNull{a?.optString(it)?.takeIf{v->v.isNotBlank()}}.toSet()
 fun readNetwork(discover:Boolean):Pair<List<SeraProfessional>,SeraNetworkRelations>?{
  val r=read("network",discover.toString())?:return null
  val people=objects(r.optJSONArray("people")).map{SeraProfessional(it.optString("id"),it.optString("name"),it.optString("headline"),it.optString("location"),matchedSkills=it.optString("skills"))}
  val relations=SeraNetworkRelations(strings(r.optJSONArray("follows")),objects(r.optJSONArray("requests")).map{SeraConnectionRequest(it.optString("id"),it.optString("requester"),it.optString("recipient"),it.optString("status"))},strings(r.optJSONArray("followers")))
  return people to relations
 }
 fun writeNetwork(discover:Boolean,people:List<SeraProfessional>,relations:SeraNetworkRelations,generation:Long){
  val p=JSONArray();people.take(500).forEach{p.put(JSONObject().put("id",it.id).put("name",it.name.take(120)).put("headline",it.headline.take(300)).put("location",it.location.take(180)).put("skills",it.matchedSkills.take(300)))}
  val req=JSONArray();relations.requests.take(500).forEach{req.put(JSONObject().put("id",it.id).put("requester",it.requester).put("recipient",it.recipient).put("status",it.status))}
  write("network",discover.toString(),JSONObject().put("people",p).put("follows",JSONArray(relations.follows.toList())).put("followers",JSONArray(relations.followers.toList())).put("requests",req),generation,300000L,2)
 }
 fun readNotifications(tab:String,page:Int)=read("notifications","$tab:$page")
 fun writeNotifications(tab:String,page:Int,rows:List<JSONObject>,names:Map<String,String>,jobs:Map<String,JSONObject>,enabled:Boolean,generation:Long){
  if(page!=1)return
  val safeRows=JSONArray();rows.take(31).forEach{row->
   val safe=JSONObject()
   listOf("id","actor_id","kind","post_id","job_id","created_at","read_at").forEach{k->safe.put(k,row.opt(k)?:JSONObject.NULL)}
   safeRows.put(safe)
  }
  val people=JSONObject();names.forEach{(id,name)->people.put(id,name.take(120))}
  val listings=JSONArray();jobs.values.take(31).forEach{row->listings.put(JSONObject().put("id",row.optString("id")).put("title",row.optString("title").take(300)).put("company",row.optString("company").take(300)))}
  write("notifications","$tab:$page",JSONObject().put("rows",safeRows).put("names",people).put("jobs",listings).put("enabled",enabled),generation,300000L,6)
 }
 fun readExplore(kind:String):List<JSONObject>?=read("explore",kind)?.let{objects(it.optJSONArray("rows"))}
 fun writeExplore(kind:String,rows:List<JSONObject>,generation:Long){
  val fields=when(kind){"events"->listOf("id","title","starts_at","location","attendees","capacity","cancelled");"opportunities"->listOf("id","title","full_name","skills","matched_skills","closed","interest_count");else->return}
  val safe=JSONArray();rows.take(40).forEach{row->val out=JSONObject();fields.forEach{k->row.opt(k)?.let{out.put(k,if(it is String)it.take(500) else it)}};if(out.optString("id").isNotBlank())safe.put(out)}
  write("explore",kind,JSONObject().put("rows",safe),generation,300000L,2)
 }
}
