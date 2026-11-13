package et.sera.nativeapp
import org.junit.Assert.*
import org.junit.Test
class NativeChatTransferTest {
    @Test fun cancellationWinsBeforeCommit(){
        val transfer=NativeChatTransfer()
        assertTrue(transfer.requestCancel());assertTrue(transfer.cancelled)
        assertFalse(transfer.requestCancel())
        assertTrue(runCatching{transfer.beginCommit()}.isFailure)
    }
    @Test fun committingCannotBeCancelled(){
        val transfer=NativeChatTransfer()
        transfer.beginCommit();assertFalse(transfer.requestCancel());assertFalse(transfer.cancelled)
    }
}
