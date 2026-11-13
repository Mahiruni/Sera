package et.sera.nativeapp
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class SeraAttachmentKind { PHOTO,CAMERA,DOCUMENT,AUDIO,VIDEO,CV }
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun SeraAttachmentStudio(onDismiss:()->Unit,onChoose:(SeraAttachmentKind)->Unit){
    val dark=androidx.compose.foundation.isSystemInDarkTheme()
    val surface=if(dark)Color(0xFF19292E)else Color(0xFFFFFFFF)
    val tile=if(dark)Color(0xFF253D42)else Color(0xFFE8F5F3)
    val ink=if(dark)Color(0xFFF0F7F6)else Color(0xFF183B40)
    val muted=if(dark)Color(0xFFA8C3C5)else Color(0xFF617B7C)
    val accent=if(dark)Color(0xFF6DE0D8)else Color(0xFF087E83)
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=surface,contentColor=ink,shape=RoundedCornerShape(topStart=28.dp,topEnd=28.dp)){
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start=20.dp,end=20.dp,bottom=20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){Text("Share something",fontSize=23.sp,fontWeight=FontWeight.Bold,color=ink);Text("Choose an attachment",fontSize=13.sp,color=muted)}
                IconButton(onClick=onDismiss){Icon(Icons.Outlined.Close,"Close attachment studio",tint=muted)}
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                AttachmentTile("Photos", "Up to 4 images",Icons.Outlined.PhotoLibrary,tile,ink,muted,accent,Modifier.weight(1f)){onChoose(SeraAttachmentKind.PHOTO)}
                AttachmentTile("Camera","Take a photo",Icons.Outlined.CameraAlt,tile,ink,muted,accent,Modifier.weight(1f)){onChoose(SeraAttachmentKind.CAMERA)}
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                AttachmentTile("Document","PDF files",Icons.Outlined.Description,tile,ink,muted,accent,Modifier.weight(1f)){onChoose(SeraAttachmentKind.DOCUMENT)}
                AttachmentTile("Audio","Choose audio",Icons.Outlined.Headphones,tile,ink,muted,accent,Modifier.weight(1f)){onChoose(SeraAttachmentKind.AUDIO)}
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                AttachmentTile("Video","MP4 · up to 5 MB",Icons.Outlined.Videocam,tile,ink,muted,accent,Modifier.weight(1f)){onChoose(SeraAttachmentKind.VIDEO)}
                AttachmentTile("Profile CV","Choose saved CV",Icons.Outlined.Description,tile,ink,muted,accent,Modifier.weight(1f)){onChoose(SeraAttachmentKind.CV)}
            }
            Text("Private conversation · Up to 5 MB per file",fontSize=12.sp,color=muted)
        }
    }
}
@Composable private fun AttachmentTile(title:String,subtitle:String,icon:androidx.compose.ui.graphics.vector.ImageVector,tile:Color,ink:Color,muted:Color,accent:Color,modifier:Modifier,onClick:()->Unit){
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(tile).clickable(onClick=onClick).padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
        Icon(icon,null,tint=accent,modifier=Modifier.size(25.dp))
        Text(title,color=ink,fontWeight=FontWeight.SemiBold,fontSize=14.sp)
        Text(subtitle,color=muted,fontSize=11.sp)
    }
}
@Composable internal fun SeraAttachmentThumbnail(file:NativeChatSelection,accent:Color){
    var bitmap by remember(file){mutableStateOf<android.graphics.Bitmap?>(null)}
    LaunchedEffect(file){if(file.mime.startsWith("image/"))bitmap=withContext(Dispatchers.Default){runCatching{val options=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true};android.graphics.BitmapFactory.decodeByteArray(file.bytes,0,file.bytes.size,options);val sample=maxOf(1,maxOf(options.outWidth,options.outHeight)/160);android.graphics.BitmapFactory.decodeByteArray(file.bytes,0,file.bytes.size,android.graphics.BitmapFactory.Options().apply{inSampleSize=sample})}.getOrNull()}}
    val image=bitmap
    if(image!=null)Image(image.asImageBitmap(),"Selected photo",Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),contentScale=ContentScale.Crop)
    else Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha=.12f)),contentAlignment=Alignment.Center){Icon(Icons.Outlined.Description,null,tint=accent)}
}
