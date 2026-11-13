package et.sera.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Test

class SeraMessagingWrapperTest {
    @Test fun messagingOccupiesNinetyPercentWithKeyboardClosed() {
        assertEquals(.90f,seraMessagingHeightFraction(false),.001f)
    }
    @Test fun messagingUsesEntireUsableViewportWithKeyboardOpen() {
        assertEquals(1f,seraMessagingHeightFraction(true),.001f)
    }
    @Test fun dragMovesUpAndDownWithinBounds() {
        assertEquals(.8f,seraMessagingDragFraction(.9f,100f,1000f),.001f)
        assertEquals(1f,seraMessagingDragFraction(.9f,-500f,1000f),.001f)
        assertEquals(.6f,seraMessagingDragFraction(.9f,500f,1000f),.001f)
    }
    @Test fun releaseSnapsToNearestOrFlingDirection() {
        assertEquals(.6f,seraMessagingSnapFraction(.67f,0f,420f),.001f)
        assertEquals(.9f,seraMessagingSnapFraction(.83f,0f,420f),.001f)
        assertEquals(1f,seraMessagingSnapFraction(.92f,-600f,420f),.001f)
        assertEquals(.6f,seraMessagingSnapFraction(.83f,600f,420f),.001f)
    }
    @Test fun keyboardPreservesRestingPosition() {
        assertEquals(1f,seraMessagingHeightFraction(true,.6f),.001f)
        assertEquals(.6f,seraMessagingHeightFraction(false,.6f),.001f)
    }
}
