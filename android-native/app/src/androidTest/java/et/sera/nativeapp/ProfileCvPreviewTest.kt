package et.sera.nativeapp

import android.graphics.pdf.PdfDocument
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream

class ProfileCvPreviewTest {
    @get:Rule val compose=createComposeRule()
    private fun samplePdf():ByteArray {
        val document=PdfDocument()
        try {
            for(i in 1..2){
                val page=document.startPage(PdfDocument.PageInfo.Builder(300,400,i).create())
                page.canvas.drawText("Professional CV - page "+i,20f,40f,android.graphics.Paint().apply{textSize=14f})
                document.finishPage(page)
            }
            return ByteArrayOutputStream().use{out->document.writeTo(out);out.toByteArray()}
        }finally{document.close()}
    }
    @Test fun savedPdfCanBeReviewedBeforeSending(){
        val cv=NativeChatSelection("SERA-CV.pdf","application/pdf",samplePdf())
        var dismissed=false
        compose.setContent{MaterialTheme{SeraPdfPreview(cv,onDismiss={dismissed=true})}}
        compose.waitUntil(15000){compose.onAllNodesWithContentDescription("PDF page 1").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("1 / 2").assertExists()
        compose.onNodeWithText("Previous").assertIsNotEnabled()
        compose.onNodeWithText("Next").performClick()
        compose.waitUntil(15000){compose.onAllNodesWithContentDescription("PDF page 2").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("2 / 2").assertExists()
        compose.onNodeWithText("Next").assertIsNotEnabled()
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle{assertTrue(dismissed)}
    }
}
