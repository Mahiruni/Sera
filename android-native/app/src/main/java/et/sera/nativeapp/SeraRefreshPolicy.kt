package et.sera.nativeapp

/** Conservative foreground synchronization budgets; audio uses its separate engine. */
internal fun seraReconnectDelay(attempt:Int,jitterMs:Long=0):Long =
    (2000L * (1L shl attempt.coerceIn(0,5))).coerceAtMost(60000L) + jitterMs.coerceIn(0L,1000L)
internal fun seraMessageFallbackInterval(joined:Boolean):Long = if(joined) 30000L else 15000L
internal fun seraFeedProbeInterval(activeCall:Boolean):Long = if(activeCall) 120000L else 60000L

/** Small initial history page keeps opening chat independent of older history. */
internal const val SERA_INITIAL_MESSAGE_WINDOW=40
