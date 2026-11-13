package et.sera.nativeapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.collect

/** OS estimates are scheduling hints, not measured application throughput. */
internal data class SeraLinkState(val available:Boolean=true,val constrained:Boolean=false,val networkId:Long=0L)
internal val LocalSeraLink=staticCompositionLocalOf{SeraLinkState()}
internal fun seraLinkState(internet:Boolean,validated:Boolean,metered:Boolean,downKbps:Int,upKbps:Int,networkId:Long):SeraLinkState =
    SeraLinkState(internet,internet&&(!validated||metered||(downKbps in 1..512)||(upKbps in 1..128)),networkId)
internal fun seraSecondarySyncDelay(link:SeraLinkState,activeCall:Boolean,normal:Long):Long =
    when{activeCall->normal*4;link.constrained->normal*2;else->normal}
private fun snapshot(manager:ConnectivityManager):SeraLinkState {
    val network=manager.activeNetwork?:return SeraLinkState(available=false)
    val caps=manager.getNetworkCapabilities(network)?:return SeraLinkState(available=false)
    return seraLinkState(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
        caps.linkDownstreamBandwidthKbps,caps.linkUpstreamBandwidthKbps,network.networkHandle)
}

/** One app-level default-network observer, active only while the UI is foregrounded. */
@Composable internal fun rememberSeraLink(context:Context):SeraLinkState{
    val manager=remember(context){context.getSystemService(ConnectivityManager::class.java)}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var state by remember(manager){mutableStateOf(runCatching{snapshot(manager)}.getOrDefault(SeraLinkState()))}
    LaunchedEffect(manager,lifecycle){
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){
            callbackFlow {
                fun publish(){trySend(runCatching{snapshot(manager)}.getOrDefault(SeraLinkState()))}
                val callback=object:ConnectivityManager.NetworkCallback(){
                    override fun onAvailable(network:Network){publish()}
                    override fun onLost(network:Network){publish()}
                    override fun onCapabilitiesChanged(network:Network,capabilities:NetworkCapabilities){publish()}
                }
                val registered=runCatching{manager.registerDefaultNetworkCallback(callback)}.isSuccess
                publish()
                awaitClose{if(registered)runCatching{manager.unregisterNetworkCallback(callback)}}
            }.conflate().collect{state=it}
        }
    }
    return state
}
