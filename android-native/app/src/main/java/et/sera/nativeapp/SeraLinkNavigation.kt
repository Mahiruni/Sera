package et.sera.nativeapp

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp

internal data class SeraLinkDestination(val kind:String,val value:String)
private val seraLinkId=Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
internal fun seraLinkDestination(raw:String):SeraLinkDestination? {
    val u=runCatching{java.net.URI(raw.trim())}.getOrNull()?:return null
    val scheme=u.scheme?.lowercase()?:return null
    if(scheme !in setOf("https","http")||u.host==null||u.userInfo!=null)return null
    val host=u.host.lowercase()
    if(host!="sera.hisabtech.com")return SeraLinkDestination("external",u.toString())
    if(scheme!="https"||u.port !in listOf(-1,443))return null
    val parts=u.path.orEmpty().split('/').filter{it.isNotEmpty()}
    if(parts.size==2&&seraLinkId.matches(parts[1])&&parts[0] in listOf("members","portfolio","jobs","posts"))
        return SeraLinkDestination(parts[0],parts[1].lowercase())
    if(parts.isEmpty()||(parts.size==1&&parts[0] in listOf("privacy","terms","help","about","contact","guides","education","how-it-works")))
        return SeraLinkDestination("public","https://sera.hisabtech.com/"+parts.joinToString("/"))
    if(parts.size==1&&parts[0] in listOf("feed","jobs","discover","connections","settings","workspace","notifications","saved","profile","messages"))
        return SeraLinkDestination("section",parts[0])
    return SeraLinkDestination("unsupported",u.toString())
}
internal fun openSeraCustomTab(context:Context,url:String){
    runCatching{CustomTabsIntent.Builder().setShowTitle(true).setToolbarColor(android.graphics.Color.rgb(8,126,131)).build().launchUrl(context,Uri.parse(url))}
        .onFailure{android.widget.Toast.makeText(context,"Unable to open this link.",android.widget.Toast.LENGTH_SHORT).show()}
}
@Composable
internal fun SeraLinkNavigation(token:String?,userId:String,onSection:(String)->Unit,content:@Composable ()->Unit){
    val context=LocalContext.current
    val systemHandler=LocalUriHandler.current
    val routes=remember(userId){mutableStateListOf<SeraLinkDestination>()}
    val currentSection by rememberUpdatedState(onSection)
    val currentToken by rememberUpdatedState(token)
    val handler=remember(context,systemHandler,routes){
        object:UriHandler {
            override fun openUri(uri:String){
                if(uri.startsWith("tel:",true)||uri.startsWith("mailto:",true)){systemHandler.openUri(uri);return}
                val route=seraLinkDestination(uri)
                if(route==null){android.widget.Toast.makeText(context,"This link is not supported.",android.widget.Toast.LENGTH_SHORT).show();return}
                if(route.kind=="external"){openSeraCustomTab(context,route.value);return}
                if(route.kind=="section"&&currentToken!=null){
                    routes.clear();currentSection(route.value);return
                }
                if(routes.lastOrNull()!=route)routes.add(route)
            }
        }
    }
    CompositionLocalProvider(LocalUriHandler provides handler){
        content()
        val route=routes.lastOrNull()
        if(route!=null)key(route){
            val close={if(routes.isNotEmpty())routes.removeAt(routes.lastIndex);Unit}
            if(route.kind=="posts"&&!token.isNullOrBlank())SeraNativePostDetail(token,route.value,close)
            else Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)){
                Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.surface){
                    Box(Modifier.fillMaxSize().safeDrawingPadding()){
                        if(route.kind=="public")SeraPublicLinkPage(route.value,close)
                        else if(token.isNullOrBlank()||userId.isBlank())Column(Modifier.padding(24.dp)){
                            TextButton(onClick=close){Text("Back")}
                            Text("Sign in to view this SERA content.")
                        }
                        else when(route.kind){
                            "members","portfolio" -> {
                                var thread by remember{mutableStateOf<String?>(null)}
                                if(thread!=null)SeraNativeInbox(token,userId,Modifier.fillMaxSize(),initialThread=thread,initialPeer=route.value,onThreadExit={thread=null},onInboxExit={thread=null})
                                else SeraTalentPortfolio(token,userId,route.value,Modifier.fillMaxSize(),onMessage={thread=it},onBack=close)
                            }
                            "jobs" -> SeraLinkedJobDetail(token,userId,route.value,close)
                            else -> Column(Modifier.padding(24.dp)){
                                TextButton(onClick=close){Text("Back")}
                                Text("This SERA link is not available in this version.")
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun SeraPublicLinkPage(url:String,onClose:()->Unit){
    val handler=LocalUriHandler.current
    var error by remember(url){mutableStateOf(false)}
    var reload by remember(url){mutableIntStateOf(0)}
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().padding(8.dp)){
            TextButton(onClick=onClose){Text("Back")}
            Text(when{url.endsWith("/privacy")->"Privacy";url.endsWith("/terms")->"Terms";else->"Explore SERA"},style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(12.dp))
        }
        if(error){Text("Unable to load this page.",Modifier.padding(16.dp));TextButton(onClick={error=false;reload++}){Text("Retry")}}
        key(reload){
            AndroidView(modifier=Modifier.weight(1f).fillMaxWidth(),factory={ctx->
                WebView(ctx).apply{
                    settings.javaScriptEnabled=false
                    settings.allowFileAccess=false
                    settings.allowContentAccess=false
                    settings.domStorageEnabled=false
                    settings.mixedContentMode=android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    webViewClient=object:WebViewClient(){
                        override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean{
                            if(request.isForMainFrame){handler.openUri(request.url.toString());return true}
                            return true
                        }
                        override fun onReceivedError(view:WebView,request:WebResourceRequest,e:WebResourceError){if(request.isForMainFrame)error=true}
                    }
                    loadUrl(url)
                }
            },onRelease={it.stopLoading();it.destroy()})
        }
    }
}
