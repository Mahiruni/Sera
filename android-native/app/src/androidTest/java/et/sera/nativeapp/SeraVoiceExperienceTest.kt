package et.sera.nativeapp

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SeraVoiceExperienceTest {
    @get:Rule val compose=createComposeRule()
    @Test fun packetLossWarningRequiresMeasuredAudio(){
        assertEquals(null,seraAudioQuality(null,SeraAudioSample(100,3)))
        assertEquals(null,seraAudioQuality(SeraAudioSample(100,3),SeraAudioSample(101,3)))
        assertEquals(null,seraAudioQuality(SeraAudioSample(100,3),SeraAudioSample(99,3)))
        assertEquals(null,seraAudioQuality(SeraAudioSample(100,3),SeraAudioSample(200,4)))
        assertEquals("Unstable connection",seraAudioQuality(SeraAudioSample(100,3),SeraAudioSample(190,13)))
    }
    @Test fun historyDistinguishesMissedAndUnansweredCalls(){
        val missed=SeraCallEvent("1","peer","missed","2026-11-13T20:00:00Z")
        val outgoing=SeraCallEvent("2","me","missed","2026-11-13T20:01:00Z")
        assertEquals("Missed call",missed.outcome("me"))
        assertEquals("No answer",outgoing.outcome("me"))
        compose.setContent{MaterialTheme{SeraVoiceHistoryContent("Test member",null,listOf(missed,outgoing),"me",false,{}, {})}}
        compose.onNodeWithText("Missed call").assertExists()
        compose.onNodeWithText("No answer").assertExists()
        compose.onNodeWithText("Missed",substring=false).performClick()
        compose.onNodeWithText("Missed call").assertExists()
        compose.onNodeWithText("No answer").assertDoesNotExist()
    }
    @Test fun durationDoesNotCountNegativeTimeAndSupportsHours(){
        assertEquals("00:00",seraCallDuration(-1))
        assertEquals("00:59",seraCallDuration(59))
        assertEquals("01:00",seraCallDuration(60))
        assertEquals("59:59",seraCallDuration(3599))
        assertEquals("1:00:00",seraCallDuration(3600))
        assertEquals("2:03:04",seraCallDuration(7384))
    }
    @Test fun completedSummaryShowsRealDurationAndActions(){
        var called=false
        var closed=false
        compose.setContent{MaterialTheme{SeraVoiceSummaryContent("Test member",null,SeraVoiceSummary("Call ended",502,false),{called=true},{closed=true})}}
        compose.onNodeWithTag("sera_voice_summary_duration").assertTextEquals("08:22")
        compose.onNodeWithText("Call again").performClick()
        compose.onNodeWithText("Back to messages").performClick()
        compose.runOnIdle{assertEquals(true,called);assertEquals(true,closed)}
    }
    @Test fun unansweredCallNeverDisplaysConversationDuration(){
        compose.setContent{MaterialTheme{SeraVoiceSummaryContent("Test member",null,SeraVoiceSummary("No answer",null,false),{}, {})}}
        compose.onNodeWithTag("sera_voice_summary_duration").assertDoesNotExist()
        compose.onNodeWithText("No answer").assertExists()
    }
    @Test fun muteControlTogglesAndShowsState(){
        compose.setContent{MaterialTheme{
            var muted by remember{mutableStateOf(false)}
            Column{SeraVoiceControl(if(muted)Icons.Outlined.MicOff else Icons.Outlined.Mic,if(muted)"Unmute" else "Mute",active=muted){muted=!muted}}
        }}
        compose.onNodeWithTag("sera_voice_Mute").performClick()
        compose.onNodeWithTag("sera_voice_Unmute").assertExists().performClick()
        compose.onNodeWithTag("sera_voice_Mute").assertExists()
    }
}
