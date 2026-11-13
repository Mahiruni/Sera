package et.sera.nativeapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Sera Signature: the footer's 24px, 1.8 stroke, round-cap optical language.
 * The five dock paths retain their existing Lucide 0.468.0 geometry (ISC).
 * Existing Material action IDs remain stable; only their visual glyph changes.
 * Unknown/specialized vectors, painters and bitmaps retain their original rendering.
 */
private val signaturePaths=mapOf(
    "Home" to listOf("M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8","M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"),
    "PeopleOutline" to listOf("M18 21a8 8 0 0 0-16 0","M15 8a5 5 0 1 1-10 0a5 5 0 1 1 10 0","M22 20c0-3.37-2-6.5-4-8a5 5 0 0 0-.45-8.3"),
    "Explore" to listOf("m16.24 7.76-1.804 5.411a2 2 0 0 1-1.265 1.265L7.76 16.24l1.804-5.411a2 2 0 0 1 1.265-1.265z","M22 12a10 10 0 1 1-20 0a10 10 0 0 1 20 0"),
    "WorkOutline" to listOf("M12 12h.01","M16 6V4a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v2","M22 13a18.15 18.15 0 0 1-20 0","M4 6h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z"),
    "ChatBubbleOutline" to listOf("M20.5 11.5a8.5 8.5 0 0 1-8.5 8.5c-1.5 0-2.9-.4-4.2-1.1L3 21l1.3-5A8.5 8.5 0 1 1 20.5 11.5Z","M8 11.5h8"),
    "Inbox" to listOf("M14 9a2 2 0 0 1-2 2H6l-4 4V4a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2z","M18 9h2a2 2 0 0 1 2 2v11l-4-4h-6a2 2 0 0 1-2-2v-1"),
    "AccountCircle" to listOf("M22 12a10 10 0 1 1-20 0a10 10 0 0 1 20 0","M16 8a4 4 0 1 1-8 0a4 4 0 0 1 8 0","M5 20v-1a7 7 0 0 1 14 0v1"),
    "Search" to listOf("M19 11a8 8 0 1 1-16 0a8 8 0 0 1 16 0","m21 21-4.5-4.5"),
    "ArrowBack" to listOf("M19 12H5","m12 19-7-7 7-7"),
    "ArrowForward" to listOf("M5 12h14","m12 5 7 7-7 7"),
    "Close" to listOf("m18 6-12 12","m6 6 12 12"),
    "MoreVert" to listOf("M12 5h.01","M12 12h.01","M12 19h.01"),
    "MoreHoriz" to listOf("M5 12h.01","M12 12h.01","M19 12h.01"),
    "Call" to listOf("M22 16.92v3a2 2 0 0 1-2.18 2 19.86 19.86 0 0 1-8.66-3.08 19.5 19.5 0 0 1-6-6 19.86 19.86 0 0 1-3.08-8.7A2 2 0 0 1 4.07 2h3a2 2 0 0 1 2 1.72c.13.96.36 1.9.69 2.81a2 2 0 0 1-.45 2.11L8.04 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.91.33 1.85.56 2.81.69A2 2 0 0 1 22 16.92z"),
    "CallEnd" to listOf("M2 15v-2a2 2 0 0 1 1-1.73 18 18 0 0 1 18 0A2 2 0 0 1 22 13v2a1 1 0 0 1-1 1h-4a1 1 0 0 1-1-1v-2a12 12 0 0 0-8 0v2a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1z"),
    "History" to listOf("M3 12a9 9 0 1 0 2.6-6.4L3 8","M3 3v5h5","M12 7v5l3 2"),
    "Schedule" to listOf("M22 12a10 10 0 1 1-20 0a10 10 0 0 1 20 0","M12 6v6l4 2"),
    "Mic" to listOf("M9 4a3 3 0 0 1 6 0v8a3 3 0 0 1-6 0z","M5 10v2a7 7 0 0 0 14 0v-2","M12 19v3","M9 22h6"),
    "MicOff" to listOf("M2 2l20 20","M9 9v3a3 3 0 0 0 5 2.24","M9 5V4a3 3 0 0 1 6 0v6","M5 10v2a7 7 0 0 0 12 4.9","M19 10v2a7 7 0 0 1-.3 2","M12 19v3","M9 22h6"),
    "VolumeUp" to listOf("M11 5 6 9H3v6h3l5 4z","M15.5 8a6 6 0 0 1 0 8","M19 5a10 10 0 0 1 0 14"),
    "VolumeOff" to listOf("M11 5 6 9H3v6h3l5 4z","M16 9l6 6","M22 9l-6 6"),
    "AttachFile" to listOf("M20 11.5 11.4 20a5 5 0 0 1-7.1-7.1L15 2.8a3.5 3.5 0 0 1 5 5L9.5 18.3a2 2 0 0 1-2.8-2.8l9.2-9.2"),
    "Send" to listOf("m22 2-7 20-4-9-9-4z","M22 2 11 13"),
    "SentimentSatisfiedAlt" to listOf("M22 12a10 10 0 1 1-20 0a10 10 0 0 1 20 0","M8 9h.01","M16 9h.01","M8 14a4 4 0 0 0 8 0"),
    "Image" to listOf("M4 3h16a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z","M10 8a2 2 0 1 1-4 0a2 2 0 0 1 4 0","m22 16-5-5-7 7-3-3-5 5"),
    "PhotoLibrary" to listOf("M5 7h14a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2z","M7 3h10","m3 19 5-5 4 4 5-5 4 4","M11 11h.01"),
    "CameraAlt" to listOf("M4 7h3l2-3h6l2 3h3a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2z","M16 13a4 4 0 1 1-8 0a4 4 0 0 1 8 0"),
    "Description" to listOf("M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z","M14 2v6h6","M8 13h8","M8 17h8"),
    "Headphones" to listOf("M3 14v-3a9 9 0 0 1 18 0v3","M3 14a2 2 0 0 1 2-2h2v8H5a2 2 0 0 1-2-2z","M21 14a2 2 0 0 0-2-2h-2v8h2a2 2 0 0 0 2-2z"),
    "Videocam" to listOf("M4 5h11a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z","m17 10 5-3v10l-5-3"),
    "Place" to listOf("M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0z","M15 10a3 3 0 1 1-6 0a3 3 0 0 1 6 0"),
    "Person" to listOf("M16 7a4 4 0 1 1-8 0a4 4 0 0 1 8 0","M4 21v-2a8 8 0 0 1 16 0v2"),
    "Edit" to listOf("M12 20H4v-8L16 3a2.12 2.12 0 0 1 3 3L7 18","m14 5 3 3"),
    "EditNote" to listOf("M11 5H4a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-7","m14 8 5-5 2 2-5 5-3 1z","M6 13h5","M6 17h8"),
    "BookmarkBorder" to listOf("M6 3h12v18l-6-4-6 4z"),
    "StarBorder" to listOf("m12 2 3 6.5 7 1-5 5 1.2 7-6.2-3.4-6.2 3.4L7 14.5l-5-5 7-1z"),
    "FavoriteBorder" to listOf("M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8l1.1 1.1L12 21l7.8-7.5 1.1-1.1a5.5 5.5 0 0 0 0-7.8z"),
    "Check" to listOf("m5 12 4 4L19 6"),
    "DoneAll" to listOf("m2 12 4 4 9-9","m10 16 3 3 9-10"),
    "CheckCircleOutline" to listOf("M22 11a10 10 0 1 1-5.9-8.2","m9 11 3 3L22 4"),
    "Reply" to listOf("m9 10-5 5 5 5","M4 15h10a6 6 0 0 0 6-6V5"),
    "Forward" to listOf("m15 10 5 5-5 5","M20 15H10a6 6 0 0 1-6-6V5"),
    "ContentCopy" to listOf("M8 8h12a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2z","M16 8V4a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h2"),
    "DeleteOutline" to listOf("M3 6h18","M19 6l-1 15H6L5 6","M9 6V3h6v3","M10 10v7","M14 10v7"),
    "Info" to listOf("M22 12a10 10 0 1 1-20 0a10 10 0 0 1 20 0","M12 11v5","M12 7h.01"),
    "PushPin" to listOf("M16 3H8l1 6-4 5v2h14v-2l-4-5z","M12 16v6"),
    "ChevronRight" to listOf("m9 18 6-6-6-6"),
    "ExpandMore" to listOf("m6 9 6 6 6-6"),
    "KeyboardArrowUp" to listOf("m6 15 6-6 6 6"),
    "PlayArrow" to listOf("m7 4 14 8-14 8z"),
    "Pause" to listOf("M9 5H6v14h3z","M18 5h-3v14h3z"),
    "Stop" to listOf("M6 6h12v12H6z"),
    "NotificationsNone" to listOf("M18 8a6 6 0 0 0-12 0c0 7-3 8-3 9h18c0-1-3-2-3-9","M10 21h4"),
    "Settings" to listOf("M12 8a4 4 0 1 1 0 8 4 4 0 0 1 0-8z","M9 3h6l.8 2.5 2.4 1.4 2.6-.5 2 3.5-1.8 2v2.8l1.8 2-2 3.5-2.6-.5-2.4 1.4L15 23H9l-.8-2.9-2.4-1.4-2.6.5-2-3.5 1.8-2v-2.8L1.2 9l2-3.5 2.6.5 2.4-1.4z"),
    "Logout" to listOf("M10 17l5-5-5-5","M15 12H3","M13 3h6a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-6"),
    "Public" to listOf("M22 12a10 10 0 1 1-20 0a10 10 0 0 1 20 0","M2 12h20","M12 2a15 15 0 0 1 0 20 15 15 0 0 1 0-20z"),
    "Lock" to listOf("M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z","M7 11V7a5 5 0 0 1 10 0v4","M12 15v3"),
    "Add" to listOf("M12 5v14","M5 12h14"),
    "AddCircleOutline" to listOf("M22 12a10 10 0 1 1-20 0a10 10 0 0 1 20 0","M12 8v8","M8 12h8"),
    "Refresh" to listOf("M20 7a9 9 0 0 0-15 2","M20 3v5h-5","M4 17a9 9 0 0 0 15-2","M4 21v-5h5"),
    "Link" to listOf("M10 13a4 4 0 0 0 6 0l4-4a4 4 0 0 0-6-6l-2 2","M14 11a4 4 0 0 0-6 0l-4 4a4 4 0 0 0 6 6l2-2"),
    "DateRange" to listOf("M4 5h16a2 2 0 0 1 2 2v13a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z","M7 2v6","M17 2v6","M2 11h20","M8 15h.01","M12 15h.01","M16 15h.01"),
    "Dashboard" to listOf("M3 3h8v10H3z","M13 3h8v6h-8z","M13 11h8v10h-8z","M3 15h8v6H3z"),
    "SystemUpdate" to listOf("M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z","M12 7v9","m8 12 4 4 4-4"),
    "FormatQuote" to listOf("M3 21v-8a7 7 0 0 1 7-7v4a3 3 0 0 0-3 3h3v8z","M14 21v-8a7 7 0 0 1 7-7v4a3 3 0 0 0-3 3h3v8z"),
    "Menu" to listOf("M4 6h16","M4 12h16","M4 18h16"),
    "Share" to listOf("M12 15V3","m8 7 4-4 4 4","M4 12v7a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-7"),
    "Download" to listOf("M12 3v12","m7 10 5 5 5-5","M4 17v4h16v-4"),
    "CallMissed" to listOf("M3 7l9 9 9-9","M3 14V7h7","M14 3l7 7","M21 3l-7 7"),
    "CallReceived" to listOf("M17 7 7 17","M7 7v10h10"),
    "CallMade" to listOf("M7 17 17 7","M7 7h10v10"),
    "ChevronLeft" to listOf("m15 18-6-6 6-6"),
    "OpenInNew" to listOf("M13 3h8v8","m21 3-10 10","M21 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h6"),
    "ArrowUpward" to listOf("M12 19V5","m5 12 7-7 7 7"),
    "ArrowDownward" to listOf("M12 5v14","m5 12 7 7 7-7"),
    "Remove" to listOf("M5 12h14"),
    "RadioButtonChecked" to listOf("M22 12a10 10 0 1 1-20 0a10 10 0 0 1 20 0","M16 12a4 4 0 1 1-8 0a4 4 0 0 1 8 0"),
    "RadioButtonUnchecked" to listOf("M22 12a10 10 0 1 1-20 0a10 10 0 0 1 20 0"),
    "Visibility" to listOf("M2 12s4-8 10-8 10 8 10 8-4 8-10 8-10-8-10-8z","M15 12a3 3 0 1 1-6 0a3 3 0 0 1 6 0"),
    "VisibilityOff" to listOf("M2 2l20 20","M10 4c7-1 12 8 12 8s-1 2-3 4","M6 6c-3 2-4 6-4 6s4 8 10 8c2 0 4-1 5-2"),
    "Shield" to listOf("M12 2l9 4v6c0 5-9 10-9 10S3 17 3 12V6z","m8 12 3 3 5-6")
)
private val signatureAliases=mapOf(
    "People" to "PeopleOutline",
    "Group" to "PeopleOutline",
    "GroupAdd" to "PeopleOutline",
    "Work" to "WorkOutline",
    "BusinessCenter" to "WorkOutline",
    "Chat" to "ChatBubbleOutline",
    "ChatBubble" to "ChatBubbleOutline",
    "Message" to "ChatBubbleOutline",
    "MessageOutlined" to "ChatBubbleOutline",
    "Forum" to "Inbox",
    "Email" to "Inbox",
    "MailOutline" to "Inbox",
    "AccountBox" to "AccountCircle",
    "PersonOutline" to "Person",
    "KeyboardArrowLeft" to "ChevronLeft",
    "ArrowBackIos" to "ChevronLeft",
    "KeyboardArrowRight" to "ChevronRight",
    "ArrowForwardIos" to "ChevronRight",
    "KeyboardArrowDown" to "ExpandMore",
    "ExpandLess" to "KeyboardArrowUp",
    "Phone" to "Call",
    "PhoneEnabled" to "Call",
    "PhoneDisabled" to "CallEnd",
    "AccessTime" to "Schedule",
    "EmojiEmotions" to "SentimentSatisfiedAlt",
    "SentimentSatisfied" to "SentimentSatisfiedAlt",
    "Photo" to "Image",
    "ImageOutlined" to "Image",
    "PhotoCamera" to "CameraAlt",
    "InsertDriveFile" to "Description",
    "Article" to "Description",
    "LocationOn" to "Place",
    "Create" to "Edit",
    "ModeEdit" to "Edit",
    "Bookmark" to "BookmarkBorder",
    "Star" to "StarBorder",
    "Favorite" to "FavoriteBorder",
    "CheckCircle" to "CheckCircleOutline",
    "Delete" to "DeleteOutline",
    "InfoOutline" to "Info",
    "Notifications" to "NotificationsNone",
    "Language" to "Public",
    "Autorenew" to "Refresh",
    "Cached" to "Refresh",
    "CalendarToday" to "DateRange",
    "Event" to "DateRange",
    "SaveAlt" to "Download",
    "FileDownload" to "Download",
    "FileUpload" to "Share",
    "OpenInNew" to "OpenInNew",
    "ArrowUpward" to "ArrowUpward",
    "ArrowDownward" to "ArrowDownward",
    "Remove" to "Remove",
    "VolumeMute" to "VolumeOff",
    "VolumeDown" to "VolumeUp",
    "RadioButtonChecked" to "RadioButtonChecked",
    "RadioButtonUnchecked" to "RadioButtonUnchecked",
    "Visibility" to "Visibility",
    "VisibilityOff" to "VisibilityOff",
    "Shield" to "Shield",
    "Security" to "Shield"
)
internal fun seraSignatureKey(name:String):String? {
    val short=name.substringAfterLast('.')
    val key=signatureAliases[short]?:short
    return key.takeIf{signaturePaths.containsKey(it)}
}

@Composable internal fun SeraSignatureGlyph(name:String,tint:Color,modifier:Modifier=Modifier,description:String?=null,size:Dp=24.dp,filled:Boolean=false){
    val key=seraSignatureKey(name)?:return
    val paths=remember(key){signaturePaths.getValue(key).map{PathParser().parsePathString(it).toPath()}}
    val semantics=if(description==null)Modifier else Modifier.semantics{contentDescription=description;role=Role.Image}
    Canvas(modifier.then(semantics).size(size)){
        val factor=this.size.minDimension/24f
        val left=(this.size.width-24f*factor)/2f
        val top=(this.size.height-24f*factor)/2f
        withTransform({translate(left,top);scale(factor,factor,pivot=Offset.Zero)}){
            val stroke=Stroke(width=1.8f,cap=StrokeCap.Round,join=StrokeJoin.Round)
            paths.forEachIndexed{index,path->
                if(filled&&index==0)drawPath(path,tint)
                drawPath(path,tint,style=stroke)
            }
        }
    }
}

/** Package-local icon adapter; applies to native screens using Material3 wildcard imports. */
@Composable internal fun Icon(imageVector:ImageVector,contentDescription:String?,modifier:Modifier=Modifier,tint:Color=LocalContentColor.current){
    val short=imageVector.name.substringAfterLast('.')
    if(seraSignatureKey(imageVector.name)==null||tint==Color.Unspecified){
        MaterialIcon(imageVector,contentDescription,modifier,tint);return
    }
    SeraSignatureGlyph(imageVector.name,tint,modifier,contentDescription,filled=short in setOf("Favorite","Bookmark","Star"))
}
@Composable internal fun Icon(painter:Painter,contentDescription:String?,modifier:Modifier=Modifier,tint:Color=LocalContentColor.current){
    MaterialIcon(painter,contentDescription,modifier,tint)
}
@Composable internal fun Icon(bitmap:ImageBitmap,contentDescription:String?,modifier:Modifier=Modifier,tint:Color=LocalContentColor.current){
    MaterialIcon(bitmap,contentDescription,modifier,tint)
}
