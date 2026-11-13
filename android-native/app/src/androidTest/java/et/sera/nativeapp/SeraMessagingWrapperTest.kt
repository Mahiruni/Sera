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
}
