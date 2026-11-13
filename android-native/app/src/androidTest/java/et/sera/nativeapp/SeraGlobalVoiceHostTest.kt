package et.sera.nativeapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SeraGlobalVoiceHostTest {
    @get:Rule val compose=createComposeRule()
    @Test fun activeCallSurvivesConversationExitAndSwitch(){
        val state=SeraGlobalCallState()
        val first=SeraCallTarget("first","First member",null)
        val second=SeraCallTarget("second","Second member",null)
        state.select(first,{}, {})
        state.report("first",false,true)
        state.start("first")
        state.report("first",false,true)
        assertTrue(state.pinned)
        state.consumeStart()
        state.report("first",true,true)
        state.unselect("first")
        assertEquals(first,state.target)
        state.select(second,{}, {})
        assertEquals(first,state.target)
        val before=state.callRequest
        state.start("second")
        assertEquals(before,state.callRequest)
        state.restore()
        assertEquals(1,state.returnRequest)
        state.report("first",false,true)
        assertEquals(second,state.target)
        assertFalse(state.pinned)
    }
    @Test fun globalIncomingNeverReplacesAnActiveCallAndExpiresCleanly(){
        val state=SeraGlobalCallState()
        val first=SeraCallTarget("first","First member",null)
        val incoming=SeraCallTarget("incoming","Incoming member",null)
        val later=SeraCallTarget("later","Later member",null)
        state.select(first,{}, {})
        state.report("first",false,true)
        state.discover(incoming,"invite",System.currentTimeMillis()+45000)
        assertEquals(incoming,state.target)
        assertEquals("invite",state.pendingIncomingId)
        state.report("incoming",false,true)
        assertTrue(state.pinned)
        state.select(later,{}, {})
        state.discover(later,"other",System.currentTimeMillis()+45000)
        assertEquals(incoming,state.target)
        state.completeDiscovery("other")
        assertEquals("invite",state.pendingIncomingId)
        state.completeDiscovery("invite")
        state.report("incoming",true,true)
        assertTrue(state.pinned)
        state.report("incoming",false,true)
        assertEquals(later,state.target)
        assertFalse(state.pinned)
        state.discover(first,"expired",System.currentTimeMillis()-1)
        assertEquals(later,state.target)
    }
    @Test fun ongoingStripProvidesReturnMuteAndEnd(){
        var restore=0
        var mute=0
        var end=0
        compose.setContent{MaterialTheme{
            SeraOngoingCallStrip("Test member",null,"02:38",false,{restore++},{mute++},{end++},true)
        }}
        compose.onNodeWithText("02:38").assertExists()
        compose.onNodeWithText("Test member").performClick()
        compose.onNodeWithContentDescription("Mute").performClick()
        compose.onNodeWithContentDescription("End call").performClick()
        compose.runOnIdle{assertEquals(1,restore);assertEquals(1,mute);assertEquals(1,end)}
    }
}
