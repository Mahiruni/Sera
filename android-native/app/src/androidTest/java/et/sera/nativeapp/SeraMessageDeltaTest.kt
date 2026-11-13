package et.sera.nativeapp

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SeraMessageDeltaTest {
 private val id="00000000-0000-4000-8000-000000000001"
 private fun message(id:String,body:String,time:String="2026-11-14T00:00:00Z")=NativeMessage(id,"sender",body,"",time,"",emptyList())
 @Test fun hintsOnlyTrustValidMessageIdentifiers(){
   val event=JSONObject().put("data",JSONObject().put("table","professional_messages").put("type","UPDATE").put("record",JSONObject().put("id",id)))
   assertEquals(SeraMessageSyncHint.Rows(setOf(id)),seraMessageHint(event))
   event.getJSONObject("data").put("type","DELETE")
   assertEquals(SeraMessageSyncHint.Full,seraMessageHint(event))
   assertEquals(SeraMessageSyncHint.Full,seraMessageHint(JSONObject()))
 }
 @Test fun coalescingRetainsIdsAndFullDominates(){
   val hints=SeraMessageHints()
   hints.add(SeraMessageSyncHint.Rows(setOf("a")));hints.add(SeraMessageSyncHint.Rows(setOf("b","a")))
   assertEquals(SeraMessageSyncHint.Rows(setOf("a","b")),hints.drain())
   hints.add(SeraMessageSyncHint.Full);hints.add(SeraMessageSyncHint.Rows(setOf("a")))
   assertEquals(SeraMessageSyncHint.Full,hints.drain())
   hints.add(SeraMessageSyncHint.Rows((1..65).map{it.toString()}.toSet()))
   assertEquals(SeraMessageSyncHint.Full,hints.drain())
 }
 @Test fun authoritativeDeltaPreservesHistoryAndReplacesEdits(){
   val old=message("a","old")
   val second=message("b","keep","2026-11-14T00:01:00Z")
   val third=message("c","new","2026-11-14T00:02:00Z")
   assertEquals(listOf(old.copy(body="edited",unsent=true),second,third),seraMergeMessageDelta(listOf(old,second),listOf(third,old.copy(body="edited",unsent=true)),3))
   assertEquals(listOf(second,third),seraMergeMessageDelta(listOf(old,second),listOf(third),2))
 }
}
