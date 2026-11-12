package et.sera.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Actual native destinations; no unsupported workspace actions are presented. */
@Composable internal fun SeraNativeWorkspace(modifier:Modifier,onNavigate:(String)->Unit){
    val entries=listOf(Triple("Jobs","Explore opportunities and employer tools",Icons.Outlined.WorkOutline),Triple("Saved","Return to posts you saved",Icons.Outlined.BookmarkBorder),Triple("Inbox","Continue professional conversations",Icons.Outlined.ChatBubbleOutline),Triple("Profile","Manage your professional identity",Icons.Outlined.AccountCircle),Triple("Settings","Account, verification and privacy",Icons.Outlined.Settings))
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("Workspace",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
        Text("Your professional tools",color=MaterialTheme.colorScheme.onSurfaceVariant)
        entries.forEach{(destination,description,icon)->OutlinedCard(onClick={onNavigate(destination)},shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth()){
            Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){Icon(icon,null,tint=MaterialTheme.colorScheme.primary);Column(Modifier.weight(1f)){Text(destination,fontWeight=FontWeight.SemiBold);Text(description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Icon(Icons.Outlined.ChevronRight,null)}
        }}
    }
}
