package et.sera.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Test

class SeraVideoPolicyTest {
    @Test fun freeCeilingAndPremiumRecovery(){
        val p=SeraVideoAdaptation()
        val good=SeraSpeechLink(.01,.1,5_000_000.0)
        repeat(15){p.update(SeraVideoLevel.STANDARD,good)}
        assertEquals(SeraVideoLevel.STANDARD,p.level)
        repeat(15){p.update(SeraVideoLevel.SUPER_HD,good)}
        assertEquals(SeraVideoLevel.SUPER_HD,p.level)
        assertEquals(SeraVideoLevel.STANDARD,p.update(SeraVideoLevel.STANDARD,good))
        assertEquals(SeraVideoLevel.DATA_SAVER,p.update(SeraVideoLevel.SUPER_HD,SeraSpeechLink(.09,.7,250_000.0)))
        assertEquals(SeraVideoLevel.PAUSED,p.update(SeraVideoLevel.SUPER_HD,SeraSpeechLink(.2,1.1,90_000.0)))
        repeat(2){assertEquals(SeraVideoLevel.PAUSED,p.update(SeraVideoLevel.SUPER_HD,good))}
        assertEquals(SeraVideoLevel.DATA_SAVER,p.update(SeraVideoLevel.SUPER_HD,good))
        repeat(15){p.update(SeraVideoLevel.SUPER_HD,good,true)}
        assertEquals(SeraVideoLevel.DATA_SAVER,p.level)
    }
    @Test fun unknownMetricsNeverUnlockHd(){
        val p=SeraVideoAdaptation()
        repeat(20){p.update(SeraVideoLevel.SUPER_HD,SeraSpeechLink(Double.NaN,-1.0,Double.NaN))}
        assertEquals(SeraVideoLevel.DATA_SAVER,p.level)
        assertEquals(SeraVideoLevel.STANDARD,seraVideoCap("unexpected"))
        assertEquals(0,SeraVideoLevel.PAUSED.bitrate)
    }
}
