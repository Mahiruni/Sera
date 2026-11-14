package et.sera.nativeapp
import org.junit.Test
import org.junit.Assert.*
class SeraStartupRecoveryTest {
 @Test fun temporaryErrorsDoNotInvalidateSession(){
  for(code in listOf("","request_timeout","unexpected_failure","over_request_rate_limit","network_error"))assertFalse(code,seraRefreshDefinitivelyExpired(code))
 }
 @Test fun explicitRevokedSessionIsRecognized(){
  for(code in listOf("refresh_token_not_found","refresh_token_already_used","session_not_found","session_expired"))assertTrue(code,seraRefreshDefinitivelyExpired(code))
 }
}
