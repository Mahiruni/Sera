package et.sera.nativeapp

import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class SeraVoiceLocaleTest {
    @Test fun voiceLabelsResolveForAllSupportedLocales(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val labels=listOf("Voice call","Answer","Decline","Mute","End call","Call again","Connected","Recent calls")
        for(language in listOf("en","am","om")){
            val config=Configuration(context.resources.configuration).apply{setLocales(LocaleList(Locale.forLanguageTag(language)))}
            val localized=context.createConfigurationContext(config)
            for(label in labels){
                val result=localized.voiceText(label)
                assertTrue(result.isNotBlank())
                if(language=="en")assertEquals(label,result)else assertNotEquals(label,result)
            }
        }
    }
    @Test fun numbersAndUnknownDiagnosticStringsAreNotAltered(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("01:23",context.voiceText("01:23"))
        assertEquals("diagnostic-unknown",context.voiceText("diagnostic-unknown"))
    }
}
