package et.sera.nativeapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private data class ExploreTile(val key:String,val label:Int,val glyph:String)
private val exploreTiles=listOf(
    ExploreTile("hiring",R.string.explore_hiring,"WorkOutline"),
    ExploreTile("opportunities",R.string.explore_opportunities,"StarBorder"),
    ExploreTile("events",R.string.explore_events,"DateRange"),
    ExploreTile("spaces",R.string.explore_spaces,"Inbox"),
    ExploreTile("talent",R.string.explore_talent,"AccountCircle"),
    ExploreTile("communities",R.string.explore_communities,"PeopleOutline")
)

@Composable internal fun SeraExplore(token:String,userId:String,modifier:Modifier=Modifier){
    var destination by rememberSaveable(userId){mutableStateOf<String?>(null)}
    val back={destination=null}
    BackHandler(destination!=null){back()}
    when(destination){
        "hiring"->{SeraEmployerDashboard(token,userId,modifier,onBack=back);return}
        "opportunities"->{SeraOpportunities(token,userId,modifier,onBack=back);return}
        "events"->{SeraEvents(token,userId,modifier,onBack=back);return}
        "spaces"->{SeraCollaboration(token,userId,modifier,onBack=back);return}
        "talent"->{SeraTalent(token,userId,modifier,onBack=back);return}
        "communities"->{SeraCommunities(token,modifier,onBack=back);return}
    }
    SeraExploreGrid(modifier,onOpen={destination=it})
}

@Composable internal fun SeraExploreGrid(modifier:Modifier=Modifier,onOpen:(String)->Unit){
    val colors=MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        Text(stringResource(R.string.explore_title),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
        Text(stringResource(R.string.explore_subtitle),style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
        exploreTiles.chunked(3).forEach{tiles->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                tiles.forEach{tile->
                    OutlinedCard(onClick={onOpen(tile.key)},modifier=Modifier.weight(1f).aspectRatio(1f).testTag("sera_explore_"+tile.key),shape=RoundedCornerShape(18.dp),colors=CardDefaults.outlinedCardColors(containerColor=colors.surface)){
                        Column(Modifier.fillMaxSize().padding(6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                            SeraSignatureGlyph(tile.glyph,colors.primary,size=25.dp)
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(tile.label),style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.SemiBold,textAlign=TextAlign.Center,maxLines=3)
                        }
                    }
                }
            }
        }
    }
}
