package et.sera.nativeapp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private const val SERA_SHARE_ORIGIN="https://sera.hisabtech.com"
private val professionalPattern=Regex("^https://sera\\.hisabtech\\.com/(members|portfolio|jobs|posts)/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})/?$")
private val locationPattern=Regex("^https://www\\.google\\.com/maps/search/\\?api=1&query=([+-]?\\d+(?:\\.\\d+)?),([+-]?\\d+(?:\\.\\d+)?)$")
internal data class SeraShareCard(val url:String,val label:String,val location:Boolean=false)
internal fun professionalShare(raw:String):SeraShareCard?{
    val match=professionalPattern.matchEntire(raw.trim())?:return null
    val kind=match.groupValues[1];val id=match.groupValues[2].lowercase(Locale.ROOT)
    return SeraShareCard("$SERA_SHARE_ORIGIN/$kind/$id",when(kind){"members"->"Professional profile";"portfolio"->"Portfolio";"jobs"->"Job opportunity";else->"SERA post"})
}
internal fun locationShare(lat:String,lng:String):SeraShareCard?{
    val decimal=Regex("^[+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)$")
    if(!decimal.matches(lat.trim())||!decimal.matches(lng.trim()))return null
    val a=lat.trim().toDoubleOrNull()?:return null;val b=lng.trim().toDoubleOrNull()?:return null
    if(!a.isFinite()||!b.isFinite()||a !in -90.0..90.0||b !in -180.0..180.0)return null
    return SeraShareCard("https://www.google.com/maps/search/?api=1&query="+String.format(Locale.US,"%.6f,%.6f",a,b),"Shared location",true)
}
internal fun messageShareCards(body:String):List<SeraShareCard> = body.lineSequence().mapNotNull{line->professionalShare(line)?:locationPattern.matchEntire(line.trim())?.let{locationShare(it.groupValues[1],it.groupValues[2])}}.distinctBy{it.url}.take(4).toList()
@Composable internal fun SeraShareDialog(kind:SeraAttachmentKind,userId:String,onDismiss:()->Unit,onAdd:(String)->Unit){
    var value by remember{mutableStateOf("")};var lng by remember{mutableStateOf("")}
    val location=kind==SeraAttachmentKind.LOCATION
    val card=if(location)locationShare(value,lng)else professionalShare(value)
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(location)"Share a location"else "Share SERA content")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(if(location)"Enter coordinates to share a map pin. Your location is not collected automatically."else "Paste a SERA profile, portfolio, job or post link. The recipient must have access.",fontSize=13.sp)
        OutlinedTextField(value,{value=it.take(300)},singleLine=true,label={Text(if(location)"Latitude"else "SERA link")})
        if(location)OutlinedTextField(lng,{lng=it.take(30)},singleLine=true,label={Text("Longitude")})
        else{Row{TextButton(onClick={value="$SERA_SHARE_ORIGIN/members/$userId"}){Text("My profile")};TextButton(onClick={value="$SERA_SHARE_ORIGIN/portfolio/$userId"}){Text("My portfolio")}}}
        card?.let{Text(it.label,fontWeight=FontWeight.SemiBold)}
    }},confirmButton={TextButton(enabled=card!=null,onClick={card?.let{onAdd(it.url)}}){Text("Add to message")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}
@Composable internal fun SeraSharedLinkCards(body:String,ink:Color,surface:Color){
    val handler=LocalUriHandler.current
    val cards=remember(body){messageShareCards(body)}
    cards.forEach{card->Surface(color=surface,shape=RoundedCornerShape(12.dp),modifier=Modifier.padding(vertical=4.dp).widthIn(max=220.dp)){
        Column(Modifier.padding(10.dp)){
            Text(card.label,color=ink,fontWeight=FontWeight.SemiBold,fontSize=13.sp)
            Text(if(card.location)"Google Maps"else "SERA.et · Current content",color=ink.copy(alpha=.8f),fontSize=11.sp)
            TextButton(onClick={runCatching{handler.openUri(card.url)}},contentPadding=PaddingValues(0.dp)){Text(if(card.location)"Open map"else "View on SERA",color=ink,fontSize=12.sp)}
        }
    }}
}
