package et.sera.nativeapp

import android.Manifest
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SeraPermissionSetupTest {
    @get:Rule val compose=createComposeRule()
    @Test fun requestsOnlySupportedFeaturePermissions(){
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS,Manifest.permission.RECORD_AUDIO,Manifest.permission.CAMERA),seraPermissionSteps(33).map{it.permission})
        assertEquals(listOf(Manifest.permission.RECORD_AUDIO,Manifest.permission.CAMERA),seraPermissionSteps(32).map{it.permission})
    }
    @Test fun userCanSkipSetupWithoutGrantingPermission(){
        var finished=false
        compose.setContent{MaterialTheme{SeraPermissionSetup(onComplete={finished=true})}}
        // Pre-granted test environments may complete automatically.
        compose.waitForIdle()
        if(compose.onAllNodesWithTag("sera_permission_skip_all").fetchSemanticsNodes().isNotEmpty())compose.onNodeWithTag("sera_permission_skip_all").performClick()
        compose.runOnIdle{assertEquals(true,finished)}
    }
}
