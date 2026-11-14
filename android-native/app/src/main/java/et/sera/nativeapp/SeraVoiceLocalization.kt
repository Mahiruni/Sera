package et.sera.nativeapp

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

// Translate at the presentation boundary; signaling state, filter keys and tests stay stable.
private fun voiceResource(value:String):Int?=when(value){
    "Voice call"->R.string.sv_voice_call
    "Incoming voice call"->R.string.sv_incoming_call
    "Preparing call…"->R.string.sv_preparing
    "Finding secure connection…"->R.string.sv_finding
    "Trying alternative secure connection…"->R.string.sv_alternative
    "Calling…"->R.string.sv_calling
    "Connecting…"->R.string.sv_connecting
    "Connected"->R.string.sv_connected
    "Reconnecting…"->R.string.sv_reconnecting
    "Cancelling…"->R.string.sv_cancelling
    "Answer"->R.string.sv_answer
    "Decline"->R.string.sv_decline
    "Mute"->R.string.sv_mute
    "Unmute"->R.string.sv_unmute
    "Microphone muted"->R.string.sv_muted
    "Audio"->R.string.sv_audio
    "Speaker"->R.string.sv_speaker
    "Earpiece"->R.string.sv_earpiece
    "Headset"->R.string.sv_headset
    "System default"->R.string.sv_system_default
    "Audio device"->R.string.sv_audio_device
    "End call"->R.string.sv_end
    "Minimize call"->R.string.sv_minimize
    "Return to call"->R.string.sv_return
    "Silence ringtone"->R.string.sv_silence
    "Ringtone silenced"->R.string.sv_silenced
    "Call ended"->R.string.sv_ended
    "Call declined"->R.string.sv_declined
    "Call cancelled"->R.string.sv_cancelled
    "Missed call"->R.string.sv_missed_call
    "No answer"->R.string.sv_no_answer
    "Connection lost"->R.string.sv_lost
    "Unstable connection"->R.string.sv_unstable
    "Call unavailable"->R.string.sv_unavailable
    "Unable to connect"->R.string.sv_unable
    "Call again"->R.string.sv_again
    "Back to messages"->R.string.sv_back_messages
    "Recent calls"->R.string.sv_recent
    "All"->R.string.sv_all
    "Missed"->R.string.sv_missed
    "Incoming"->R.string.sv_incoming
    "Outgoing"->R.string.sv_outgoing
    "No calls in this view"->R.string.sv_empty
    "Secure voice connection"->R.string.sv_secure
    "No audio routes available"->R.string.sv_no_routes
    "Open call"->R.string.sv_open
    "Silence"->R.string.sv_silence_short
    "Open Sera to answer"->R.string.sv_open_answer
    "Ongoing voice call"->R.string.sv_ongoing
    "Tap to return to your call"->R.string.sv_tap_return
    "Enable SERA notifications in Android settings before starting a call."->R.string.sv_notifications
    "Microphone permission is required for voice calling."->R.string.sv_mic_permission
    "Open microphone settings"->R.string.sv_mic_settings
    "Close"->R.string.close
    "No secure call relay found. Switch Wi-Fi or mobile data and try again."->R.string.sv_relay_error
    "Secure calling access unavailable. Try again later."->R.string.sv_access_error
    "Messages"->R.string.messages
    "Profile photo"->R.string.profile_photo
    "Close call history"->R.string.close
    "OK"->android.R.string.ok
    "Sera · Voice call"->R.string.sv_voice_call
    else->null
}

@Composable internal fun voiceText(value:String):String {
    val resource=voiceResource(value)
    return if(resource!=null)stringResource(resource) else value
}
internal fun Context.voiceText(value:String):String=voiceResource(value)?.let{getString(it)}?:value
