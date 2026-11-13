package et.sera.nativeapp
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

// Presentation only: never rewrite stored message content or infer a mention's identity.
private val richTokens=Regex("""https?://[^\s<>]+|\*\*[^*\n]+\*\*|(?<![\p{L}\p{N}_.])@[\p{L}\p{N}_]{1,40}""",RegexOption.IGNORE_CASE)
internal fun seraRichMessage(body:String,linkColor:Color,interactive:Boolean=true):AnnotatedString=buildAnnotatedString{
 var cursor=0
 for(m in richTokens.findAll(body)){
  append(body.substring(cursor,m.range.first))
  val value=m.value
  when{
   value.startsWith("https://",true)||value.startsWith("http://",true)->{
    val url=value.trimEnd('.',',','!','?',')',']','}',':',';')
    val valid=runCatching{val uri=java.net.URI(url);uri.host!=null&&uri.userInfo==null&&uri.scheme.lowercase() in setOf("http","https")}.getOrDefault(false)
    val style=SpanStyle(color=linkColor,textDecoration=TextDecoration.Underline)
    if(valid&&interactive)withLink(LinkAnnotation.Url(url,TextLinkStyles(style=style))){append(url)}
    else if(valid)withStyle(style){append(url)} else append(url)
    append(value.substring(url.length))
   }
   value.startsWith("**")->withStyle(SpanStyle(fontWeight=FontWeight.Bold)){append(value.substring(2,value.length-2))}
   else->withStyle(SpanStyle(color=linkColor,fontWeight=FontWeight.Medium)){append(value)}
  }
  cursor=m.range.last+1
 }
 append(body.substring(cursor))
}
@Composable internal fun SeraRichMessageText(body:String,color:Color,linkColor:Color,interactive:Boolean=true){
 val styled=remember(body,linkColor,interactive){seraRichMessage(body,linkColor,interactive)}
 Text(styled,color=color,style=seraChatBody(MaterialTheme.typography.bodyLarge))
}
