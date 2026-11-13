package et.sera.nativeapp

import org.json.JSONObject

/** Realtime payloads are only invalidation hints, never trusted message content. */
internal sealed interface SeraMessageSyncHint {
    data object Full:SeraMessageSyncHint
    data class Rows(val ids:Set<String>):SeraMessageSyncHint
}
internal fun seraMessageHint(payload:JSONObject):SeraMessageSyncHint{
    val row=payload.optJSONObject("data")?:return SeraMessageSyncHint.Full
    val type=row.optString("type").uppercase()
    if(type !in setOf("INSERT","UPDATE","DELETE"))return SeraMessageSyncHint.Full
    val table=row.optString("table")
    val record=if(type=="DELETE")row.optJSONObject("old_record") else row.optJSONObject("record")
    val id=when(table){
        "professional_messages"->if(type=="DELETE")null else record?.optString("id")
        "professional_message_reactions"->record?.optString("message_id")
        else->null
    }
    return if(id!=null&&SeraMessageAlerts.validId(id))SeraMessageSyncHint.Rows(setOf(id)) else SeraMessageSyncHint.Full
}

/** Conflates bursty socket events without losing IDs; overflow requires full reconciliation. */
internal class SeraMessageHints {
    private var full=false
    private val ids=linkedSetOf<String>()
    @Synchronized fun add(hint:SeraMessageSyncHint){
        when(hint){
            SeraMessageSyncHint.Full->{full=true;ids.clear()}
            is SeraMessageSyncHint.Rows->if(!full){ids.addAll(hint.ids);if(ids.size>64){full=true;ids.clear()}}
        }
    }
    @Synchronized fun drain():SeraMessageSyncHint {
        val result=if(full||ids.isEmpty())SeraMessageSyncHint.Full else SeraMessageSyncHint.Rows(ids.toSet())
        full=false;ids.clear();return result
    }
}

/** Authoritative rows replace matching IDs; preserve other loaded rows, ordering and bounded window. */
internal fun seraMergeMessageDelta(existing:List<NativeMessage>,changed:List<NativeMessage>,limit:Int):List<NativeMessage>{
    val rows=existing.associateBy{it.id}.toMutableMap()
    changed.forEach{rows[it.id]=it}
    return rows.values.sortedWith(compareBy<NativeMessage>{runCatching{java.time.Instant.parse(it.createdAt)}.getOrDefault(java.time.Instant.EPOCH)}.thenBy{it.id}).takeLast(limit.coerceAtLeast(1))
}
