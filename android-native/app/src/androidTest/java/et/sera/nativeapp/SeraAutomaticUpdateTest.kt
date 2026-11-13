package et.sera.nativeapp

import org.junit.Assert.*
import org.junit.Test

class SeraAutomaticUpdateTest {
    @Test fun opensOnlyForVerifiedForegroundUpdate() {
        assertTrue(shouldAutomaticallyOfferSeraInstall(true,false,true,false))
        assertFalse(shouldAutomaticallyOfferSeraInstall(false,false,true,false))
        assertFalse(shouldAutomaticallyOfferSeraInstall(true,true,true,false))
        assertFalse(shouldAutomaticallyOfferSeraInstall(true,false,false,false))
    }
    @Test fun cancelledOrAlreadyOfferedUpdateIsNotAutomaticallyPromptedAgain() {
        assertFalse(shouldAutomaticallyOfferSeraInstall(true,false,true,true))
        assertFalse(shouldAutomaticallyOfferSeraInstall(false,false,true,true))
    }
}
